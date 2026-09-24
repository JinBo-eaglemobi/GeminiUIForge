package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.looseJson

/**
 * 更新图元属性与元数据工具
 */
class UpdateBlockTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "update_block"

    override val description: String =
        "更新指定模板中某个图元的属性配置。支持修改坐标与尺寸(bounds)、中文/英文生图提示词(userPromptZh/userPromptEn)、图元名称(name)以及组件类型(type)。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "模板工程名称")
            })
            put("blockId", buildJsonObject {
                put("type", "string")
                put("description", "目标图元的唯一标识 ID")
            })
            put("name", buildJsonObject {
                put("type", "string")
                put("description", "可选：图元语义化显示名称")
            })
            put("type", buildJsonObject {
                put("type", "string")
                put("description", "可选：图元组件类型（如 BUTTON, IMAGE, TEXT, CONTAINER, REEL 等）")
            })
            put("isPureContainer", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否为纯容器/占位层（true 表示仅用于排版，不参与任何 AI 图片资源生成）")
            })
            put("userPromptZh", buildJsonObject {
                put("type", "string")
                put("description", "可选：中文 AI 生图提示词")
            })
            put("userPromptEn", buildJsonObject {
                put("type", "string")
                put("description", "可选：英文 AI 生图提示词")
            })
            put("bounds", buildJsonObject {
                put("type", "object")
                put("description", "可选：局部相对坐标与尺寸对象 { left, top, right, bottom }")
                put("properties", buildJsonObject {
                    put("left", buildJsonObject { put("type", "number") })
                    put("top", buildJsonObject { put("type", "number") })
                    put("right", buildJsonObject { put("type", "number") })
                    put("bottom", buildJsonObject { put("type", "number") })
                })
            })
            put("cropRect", buildJsonObject {
                put("type", "object")
                put("description", "可选：参考图局部裁切区域坐标 { left, top, right, bottom }")
                put("properties", buildJsonObject {
                    put("left", buildJsonObject { put("type", "number") })
                    put("top", buildJsonObject { put("type", "number") })
                    put("right", buildJsonObject { put("type", "number") })
                    put("bottom", buildJsonObject { put("type", "number") })
                })
            })
            put("scaleConfig", buildJsonObject {
                put("type", "object")
                put("description", "可选：图片不透明主体缩放与发光对齐配置 { scaleX, scaleY, offsetX, offsetY, enabled, lockAspectRatio }")
                put("properties", buildJsonObject {
                    put("scaleX", buildJsonObject { put("type", "number") })
                    put("scaleY", buildJsonObject { put("type", "number") })
                    put("offsetX", buildJsonObject { put("type", "number") })
                    put("offsetY", buildJsonObject { put("type", "number") })
                    put("enabled", buildJsonObject { put("type", "boolean") })
                    put("lockAspectRatio", buildJsonObject { put("type", "boolean") })
                })
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("blockId")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")
        val blockId = arguments["blockId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'blockId' 不能为空")

        onProgress?.invoke(0.2f, "正在读取工程结构...")
        val match = repository.findTemplatePair(projectName)
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        var foundBlock: UIBlock? = null

        fun updateRecursively(blocks: List<UIBlock>): List<UIBlock> {
            return blocks.map { b ->
                if (b.id == blockId) {
                    var updated = b

                    arguments["userPromptZh"]?.jsonPrimitive?.contentOrNull?.let { zh ->
                        updated = updated.copy(userPromptZh = zh)
                    }

                    arguments["userPromptEn"]?.jsonPrimitive?.contentOrNull?.let { en ->
                        updated = updated.copy(userPromptEn = en)
                    }

                    arguments["isPureContainer"]?.jsonPrimitive?.booleanOrNull?.let { pure ->
                        updated = updated.copy(isPureContainer = pure)
                    }

                    arguments["type"]?.jsonPrimitive?.contentOrNull?.let { typeStr ->
                        try {
                            val newType = UIBlockType.valueOf(typeStr.uppercase())
                            updated = updated.copy(type = newType)
                        } catch (_: Throwable) {}
                    }

                    arguments["bounds"]?.let { it as? JsonObject }?.let { bObj ->
                        val left = bObj["left"]?.jsonPrimitive?.floatOrNull ?: updated.bounds.left
                        val top = bObj["top"]?.jsonPrimitive?.floatOrNull ?: updated.bounds.top
                        val right = bObj["right"]?.jsonPrimitive?.floatOrNull ?: updated.bounds.right
                        val bottom = bObj["bottom"]?.jsonPrimitive?.floatOrNull ?: updated.bounds.bottom
                        updated = updated.copy(bounds = SerialRect(left, top, right, bottom))
                    }

                    arguments["cropRect"]?.let { it as? JsonObject }?.let { cObj ->
                        val left = cObj["left"]?.jsonPrimitive?.floatOrNull ?: updated.cropRect?.left ?: updated.bounds.left
                        val top = cObj["top"]?.jsonPrimitive?.floatOrNull ?: updated.cropRect?.top ?: updated.bounds.top
                        val right = cObj["right"]?.jsonPrimitive?.floatOrNull ?: updated.cropRect?.right ?: updated.bounds.right
                        val bottom = cObj["bottom"]?.jsonPrimitive?.floatOrNull ?: updated.cropRect?.bottom ?: updated.bounds.bottom
                        updated = updated.copy(cropRect = SerialRect(left, top, right, bottom))
                    }

                    arguments["scaleConfig"]?.let { it as? JsonObject }?.let { scObj ->
                        val currentSc = updated.scaleConfig
                        val scaleX = scObj["scaleX"]?.jsonPrimitive?.floatOrNull ?: currentSc.scaleX
                        val scaleY = scObj["scaleY"]?.jsonPrimitive?.floatOrNull ?: currentSc.scaleY
                        val offsetX = scObj["offsetX"]?.jsonPrimitive?.floatOrNull ?: currentSc.offsetX
                        val offsetY = scObj["offsetY"]?.jsonPrimitive?.floatOrNull ?: currentSc.offsetY
                        val enabled = scObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: currentSc.enabled
                        val lockAspectRatio = scObj["lockAspectRatio"]?.jsonPrimitive?.booleanOrNull ?: currentSc.lockAspectRatio
                        updated = updated.copy(
                            scaleConfig = currentSc.copy(
                                scaleX = scaleX,
                                scaleY = scaleY,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                enabled = enabled,
                                lockAspectRatio = lockAspectRatio
                            )
                        )
                    }

                    foundBlock = updated
                    updated
                } else {
                    b.copy(children = updateRecursively(b.children))
                }
            }
        }

        onProgress?.invoke(0.5f, "正在应用属性补丁...")
        val updatedPages = state.pages.map { page ->
            page.copy(blocks = updateRecursively(page.blocks))
        }

        if (foundBlock == null) {
            return McpToolResult.error("在模板 '$projectName' 中未找到 ID 为 '$blockId' 的图元")
        }

        onProgress?.invoke(0.8f, "正在保存模板...")
        val updatedProject = state.copy(pages = updatedPages)
        repository.saveTemplate(projectName, updatedProject)

        // 纯本地离屏渲染更新最新图元标注图并落盘缓存
        try {
            org.gemini.ui.forge.service.TemplateOverlayRenderer.renderAndSaveLatest(projectName, updatedProject)
        } catch (e: Exception) {
            AppLogger.w("UpdateBlockTool", "更新标注图异常", e)
        }

        // ★ 同步通知当前活跃工作区刷新内存状态与画布渲染
        try {
            val activeVm = McpUiActionPipeline.getActiveViewModel()
            if (activeVm != null) {
                activeVm.reload(updatedProject)
            }
        } catch (e: Throwable) {
            AppLogger.w("UpdateBlockTool", "通知活跃工作区刷新失败", e)
        }

        onProgress?.invoke(1.0f, "图元属性已更新")

        val resultJson = buildJsonObject {
            put("success", true)
            put("updatedBlock", looseJson.encodeToJsonElement(foundBlock))
        }.toString()

        return McpToolResult.text(resultJson)
    }
}
