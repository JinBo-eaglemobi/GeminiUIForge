package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 万能视图排版与局部微调工具 (edit_view_layout)
 *
 * 整合整页替换重构 (REPLACE_PAGE)、局部容器替换 (REPLACE_CONTAINER) 与节点增删改补丁 (PATCH)，
 * 统一以 operations 操作单元集合为核心入参，支持单次调用并发下发多项操作。
 */
class EditViewLayoutTool : ToolDefinition {

    override val name: String = "edit_view_layout"

    override val description: String =
        "在 UI Studio 中对视图界面执行排版编辑与装配。支持三大核心模式：1. REPLACE_PAGE (全量重构整页或新建视图页面)；2. REPLACE_CONTAINER (局部重构指定父容器内部图元，保持容器外壳与同级组件不变)；3. PATCH (对指定图元进行局部精准属性微调或增删)。支持单次调用传递多个 operations 数组流水线批量执行。【严格红线约束】：若涉及整页全量重构或新建页面，必须先通过 ask_user_question 征得用户明确授权；排版完成后必须立即调用 save_project 落盘。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        idempotentHint = false
    )

    override val timeoutMs: Long = 60_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("operations", buildJsonObject {
                put("type", "array")
                put("description", "排版操作单元列表（按数组顺序流水线执行）。")
                put("items", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("mode", buildJsonObject {
                            put("type", "string")
                            put("description", "排版操作模式: 'REPLACE_PAGE' (整页全量替换/新建), 'REPLACE_CONTAINER' (容器内部局部替换), 'PATCH' (节点精确增删改微调)")
                        })
                        put("viewName", buildJsonObject {
                            put("type", "string")
                            put("description", "目标视图页面名称（可选，缺省时默认为当前画布正在编辑的活动视图；纯英文、数字与下划线）")
                        })
                        put("targetId", buildJsonObject {
                            put("type", "string")
                            put("description", "目标容器图元唯一 ID (当 mode 为 'REPLACE_CONTAINER' 时必填，指示将新图元注入替换哪个容器内部)")
                        })
                        put("canvasWidth", buildJsonObject {
                            put("type", "number")
                            put("description", "视图分辨率宽度 (REPLACE_PAGE 模式专用，如 1280 或 1920)")
                        })
                        put("canvasHeight", buildJsonObject {
                            put("type", "number")
                            put("description", "视图分辨率高度 (REPLACE_PAGE 模式专用，如 720 或 1080)")
                        })
                        put("description", buildJsonObject {
                            put("type", "string")
                            put("description", "简短阐述该排版操作的设计构思与核心模块划分")
                        })
                        put("elements", buildJsonObject {
                            put("type", "array")
                            put("description", "图元数据列表（统一数据模型）。REPLACE_PAGE 时为页面顶层图元；REPLACE_CONTAINER 时为容器内部子图元；PATCH 时为待微调或增删的图元项。")
                            put("items", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("id", buildJsonObject { put("type", "string"); put("description", "图元唯一ID (PATCH 模式下为目标图元ID)") })
                                    put("type", buildJsonObject { put("type", "string"); put("description", "基础组件类型枚举: BUTTON, VIEW, TEXT, IMAGE, LIST, SPINE, COMBO_BOX, PROGRESS_BAR, POPUP_MENU, LOADER, SCROLL_BAR, SLIDER, INPUT") })
                                    put("x", buildJsonObject { put("type", "number"); put("description", "相对直接父容器左上角的局部 X 坐标") })
                                    put("y", buildJsonObject { put("type", "number"); put("description", "相对直接父容器左上角的局部 Y 坐标") })
                                    put("width", buildJsonObject { put("type", "number"); put("description", "物理宽度 (必须 > 0)") })
                                    put("height", buildJsonObject { put("type", "number"); put("description", "物理高度 (必须 > 0)") })
                                    put("name", buildJsonObject { put("type", "string"); put("description", "展示名称") })
                                    put("assetHash", buildJsonObject { put("type", "string"); put("description", "绑定的素材库切片唯一Hash (最高优先级精确装配主键)") })
                                    put("assetFileName", buildJsonObject { put("type", "string"); put("description", "绑定的素材库切片文件名，如 'Back_Button.png'") })
                                    put("targetPageId", buildJsonObject { put("type", "string"); put("description", "引用的平级子页面 ID (若该图元为组件实例，指向对应独立子页面。红线契约：设置 targetPageId 时绝对严禁传入 children，组件子图元必须在对应独立页面中维护)") })
                                    put("slotRole", buildJsonObject { put("type", "string"); put("description", "插槽语义角色(用于全自动装配): BACKGROUND, SPIN_BUTTON, REEL_SYMBOL, BUTTON, HUD_FRAME, TITLE_LOGO, BET_CONTROL") })
                                    put("op", buildJsonObject { put("type", "string"); put("description", "PATCH 模式专用动词: UPDATE (默认, 局部修改指定字段), ADD (追加新图元), REMOVE (删除图元)") })
                                    put("parentBlockId", buildJsonObject { put("type", "string"); put("description", "PATCH ADD 追加图元时的目标父容器 ID (缺省时追加到页面顶层)") })
                                    put("properties", buildJsonObject {
                                        put("type", "object")
                                        put("description", "组件专属高级配置属性 (BlockProperties)，覆盖全量 13 种基础类型。例如：LIST 的 listLayout, lineGap, columnGap, autoResizeItem, lineCount, columnCount, defaultItem, overflow, scrollType; BUTTON 的 text, downEffect, downEffectValue, mode; COMPONENT 的 clipOverflow, layoutType, gap; PROGRESS_BAR 的 max, value, titleType; TEXT 的 fontSize, color, align, ubb 等")
                                    })
                                    put("children", buildJsonObject {
                                        put("type", "array")
                                        put("description", "嵌套子图元列表 (红线契约：当设置了 targetPageId 时，绝对严禁包含任何 children！否则将被引擎直接打回拒绝)")
                                        put("items", buildJsonObject { put("type", "object") })
                                    })
                                })
                                put("required", buildJsonArray {
                                    add("id")
                                })
                            })
                        })
                    })
                    put("required", buildJsonArray {
                        add("mode")
                        add("elements")
                    })
                })
            })
        })
        put("required", buildJsonArray {
            add("operations")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val studioVm = org.gemini.ui.forge.service.mcp.McpUiActionPipeline.getActiveUiStudioViewModel()
            ?: return ToolResult.error("当前未打开 UI Studio 工作台")
        val editResult = studioVm.aiLayout.executeEditViewLayout(arguments)
        return if (editResult.isSuccess) {
            ToolResult.success(editResult.summary)
        } else {
            ToolResult.error(editResult.errorMessage ?: "排版执行失败")
        }
    }
}
