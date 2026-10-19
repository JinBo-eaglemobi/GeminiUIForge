package org.gemini.ui.forge.manager

import kotlinx.serialization.json.*
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.service.ai.GeminiToolConfig

/**
 * UI Studio 技能与智能体能力调度管理器。
 */
object UiStudioSkillManager {

    /** 核心旗舰技能：根据 UX 规范与素材清单全自动装配界面 */
    val SPEC_TO_VIEW_SKILL = UiStudioSkill(
        id = "spec_to_view",
        name = "Spec-to-View 界面生成",
        description = "根据传入的 UX 排版规范、文档或用户自然语言描述，结合素材库切片，自动构建新页面并直接在 UI Studio 画布中排版呈现。",
        triggerKeywords = listOf("创建界面", "生成界面", "排版", "自动排版", "生成视图", "布局", "spec", "规范", "组装", "老虎机", "slot"),
        systemInstructionAddition = """
你是一位顶级游戏 UI/UX 架构与排版工程大师。
当用户希望根据规范文档或素材库创建/生成界面时，请充分理解设计结构与审美规范（遵循 8pt 空间网格、嵌套圆角、纯容器与局部相对坐标系铁律）：
1. 局部相对坐标系：每个子图元的 bounds.left 和 bounds.top 必须且恒等于相对于其直接父容器左上角的局部坐标；
2. 全屏背景底座：背景图元 (BACKGROUND) 的 bounds 与 cropRect 必须铺满全屏 [0, 0, width, height]；
3. 纯容器层 (CONTAINER / isPureContainer = true)：无独立材质的组合结构仅作为相对定位排版容器；
4. 切片精准映射：从素材库清单中选取最匹配的切片名称填入 assetFileName。

请通过调用本地方法 `apply_view_layout` 直接将生成的排版树应用到当前客户端工程中。
""".trimIndent()
    )

    /** 注册的所有技能列表 */
    val registeredSkills = listOf(SPEC_TO_VIEW_SKILL)

    /**
     * 智能判定当前用户会话应激活的技能列表。
     */
    fun resolveActiveSkills(
        prompt: String,
        attachedMentions: List<MentionItem>,
        mode: SkillActivationMode = SkillActivationMode.AUTO
    ): List<UiStudioSkill> {
        if (mode == SkillActivationMode.DISABLED) return emptyList()
        if (mode == SkillActivationMode.ALWAYS_ON) return registeredSkills

        // AUTO 模式：智能意图探测
        val active = mutableListOf<UiStudioSkill>()
        val lowerPrompt = prompt.lowercase()

        // 判定 1：如果有拖入/引用参考文档 (DOC) 或引用了全部素材清单 (ALL_ASSETS)
        val hasDocMention = attachedMentions.any { it.category == MentionCategory.DOC }
        val hasAllAssetsMention = attachedMentions.any { it.id == "all_assets_manifest" }

        // 判定 2：关键词意图命中
        val matchesKeywords = SPEC_TO_VIEW_SKILL.triggerKeywords.any { lowerPrompt.contains(it) }

        if (hasDocMention || hasAllAssetsMention || matchesKeywords) {
            active.add(SPEC_TO_VIEW_SKILL)
        }

        return active
    }

    /**
     * 构建 Gemini 官方标准的 ToolConfig 工具声明
     */
    fun buildToolConfigs(skills: List<UiStudioSkill>): List<GeminiToolConfig> {
        if (skills.isEmpty()) return emptyList()

        val declarations = mutableListOf<JsonObject>()
        if (skills.any { it.id == SPEC_TO_VIEW_SKILL.id }) {
            declarations.add(buildApplyViewLayoutDeclaration())
        }

        if (declarations.isEmpty()) return emptyList()

        return listOf(
            GeminiToolConfig(
                functionDeclarations = declarations
            )
        )
    }

    /**
     * 构建 apply_view_layout 函数声明的官方标准 JSON Schema
     */
    private fun buildApplyViewLayoutDeclaration(): JsonObject {
        return buildJsonObject {
            put("name", "apply_view_layout")
            put("description", "在 UI Studio 中创建或更新一个视图界面，并将解析后的图元树（UIBlock Tree）自动装配到该页面中，直接在当前工作区画布呈现。")
            put("parameters", buildJsonObject {
                put("type", "OBJECT")
                put("properties", buildJsonObject {
                    put("viewName", buildJsonObject {
                        put("type", "STRING")
                        put("description", "创建或更新的视图界面英文/数字唯一名称，如 'FootballSlotMain' 或 'MainPage'")
                    })
                    put("width", buildJsonObject {
                        put("type", "INTEGER")
                        put("description", "视图页面分辨率宽度，默认 1280 或 1920")
                    })
                    put("height", buildJsonObject {
                        put("type", "INTEGER")
                        put("description", "视图页面分辨率高度，默认 720 或 1080")
                    })
                    put("description", buildJsonObject {
                        put("type", "STRING")
                        put("description", "简短阐述当前界面的设计布局构思与核心模块划分")
                    })
                    put("blocks", buildJsonObject {
                        put("type", "ARRAY")
                        put("description", "页面的顶层图元列表。遵循局部相对坐标系，纯容器可递归包含 children。")
                        put("items", buildJsonObject {
                            put("type", "OBJECT")
                            put("properties", buildJsonObject {
                                put("id", buildJsonObject { put("type", "STRING"); put("description", "图元唯一ID") })
                                put("type", buildJsonObject { put("type", "STRING"); put("description", "模块类型: BACKGROUND, BUTTON, CONTAINER, TEXT, IMAGE, SLOTS_WINDOW 等") })
                                put("x", buildJsonObject { put("type", "NUMBER"); put("description", "相对直接父容器左上角的局部 X 坐标") })
                                put("y", buildJsonObject { put("type", "NUMBER"); put("description", "相对直接父容器左上角的局部 Y 坐标") })
                                put("width", buildJsonObject { put("type", "NUMBER"); put("description", "物理宽度") })
                                put("height", buildJsonObject { put("type", "NUMBER"); put("description", "物理高度") })
                                put("isPureContainer", buildJsonObject { put("type", "BOOLEAN"); put("description", "是否为纯布局排版占位容器（不生成独立图片）") })
                                put("assetFileName", buildJsonObject { put("type", "STRING"); put("description", "绑定的素材库切片文件名，如 'Back_Button.png'") })
                                put("children", buildJsonObject {
                                    put("type", "ARRAY")
                                    put("description", "嵌套子图元列表")
                                    put("items", buildJsonObject { put("type", "OBJECT") })
                                })
                            })
                            put("required", buildJsonArray {
                                add("id")
                                add("type")
                                add("x")
                                add("y")
                                add("width")
                                add("height")
                            })
                        })
                    })
                })
                put("required", buildJsonArray {
                    add("viewName")
                    add("width")
                    add("height")
                    add("blocks")
                })
            })
        }
    }
}
