package org.gemini.ui.forge.manager

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.resource.ExternalResourceLoader
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.service.ai.GeminiToolConfig
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * UI Studio 技能与智能体能力调度管理器。
 *
 * 【核心架构原则】：
 * 1. 技能配置文件与人设指令 100% 外部文件化（位于 skills 目录下的 json 文件），代码中 0 技能数据硬编码；
 * 2. 复用通用 ExternalResourceLoader 基础加载器，外部优先 -> 内置出厂预设兜底；
 * 3. 严格遵循完全按需懒加载原则（On-Demand Loading），使用时异步加载并进入内存缓存，启动期零 I/O；
 * 4. 独立于 PromptManager，专职于技能结构反序列化与运行时工具调度。
 */
object UiStudioSkillManager {
    private const val TAG = "UiStudioSkillManager"
    private const val SKILLS_DIR = "skills"

    /** 核心技能 ID 标识常量 */
    const val SPEC_TO_VIEW_SKILL_ID = "spec_to_view"

    private val storage = LocalFileStorage()
    val resourceLoader = ExternalResourceLoader(storage)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 内存技能缓存 */
    private var cachedSkills: List<UiStudioSkill>? = null

    /**
     * 按需获取已注册的全部技能列表（外部本地优先 -> 内置出厂资源 -> 内存缓存）
     */
    suspend fun getRegisteredSkills(): List<UiStudioSkill> {
        cachedSkills?.let { return it }
        val loaded = mutableListOf<UiStudioSkill>()

        // 按需加载核心 Spec-to-View 技能
        try {
            val jsonContent = resourceLoader.loadText(SKILLS_DIR, "$SPEC_TO_VIEW_SKILL_ID.json")
            val skill = json.decodeFromString<UiStudioSkill>(jsonContent)
            loaded.add(skill)
        } catch (e: Exception) {
            AppLogger.e(TAG, "加载技能 [$SPEC_TO_VIEW_SKILL_ID.json] 失败", e)
        }

        cachedSkills = loaded
        return loaded
    }

    /**
     * 同步获取当前内存已缓存的技能列表（若未加载则返回空列表）
     */
    fun getCachedSkills(): List<UiStudioSkill> {
        return cachedSkills ?: emptyList()
    }

    /**
     * 清除技能缓存（在外部技能文件变动时调用）
     */
    fun invalidateCache() {
        cachedSkills = null
        resourceLoader.clearCache()
    }

    /**
     * 智能判定当前用户会话应激活的技能列表（挂起/异步按需版本）
     */
    suspend fun resolveActiveSkills(
        prompt: String,
        attachedMentions: List<MentionItem>,
        mode: SkillActivationMode = SkillActivationMode.AUTO,
        enabledSkillIds: Set<String>? = null
    ): List<UiStudioSkill> {
        val allSkills = getRegisteredSkills()
        val filtered = if (enabledSkillIds != null) allSkills.filter { it.id in enabledSkillIds } else allSkills
        return resolveFromSkills(filtered, prompt, attachedMentions, mode)
    }

    /**
     * 同步纯内存判定激活的技能列表（供 UI 状态高频绑定使用，复用已缓存技能）
     */
    fun resolveActiveSkillsSync(
        prompt: String,
        attachedMentions: List<MentionItem>,
        mode: SkillActivationMode = SkillActivationMode.AUTO,
        enabledSkillIds: Set<String>? = null
    ): List<UiStudioSkill> {
        val allSkills = getCachedSkills()
        val filtered = if (enabledSkillIds != null) allSkills.filter { it.id in enabledSkillIds } else allSkills
        return resolveFromSkills(filtered, prompt, attachedMentions, mode)
    }

    private fun resolveFromSkills(
        skills: List<UiStudioSkill>,
        prompt: String,
        attachedMentions: List<MentionItem>,
        mode: SkillActivationMode
    ): List<UiStudioSkill> {
        if (mode == SkillActivationMode.DISABLED) return emptyList()
        if (mode == SkillActivationMode.ALWAYS_ON) return skills

        // AUTO 模式：智能意图探测
        val active = mutableListOf<UiStudioSkill>()
        val lowerPrompt = prompt.lowercase()

        val hasDocMention = attachedMentions.any { it.category == MentionCategory.DOC }
        val hasAllAssetsMention = attachedMentions.any { it.id == "all_assets_manifest" }

        for (skill in skills) {
            val matchesKeywords = skill.triggerKeywords.any { lowerPrompt.contains(it.lowercase()) }
            if (hasDocMention || hasAllAssetsMention || matchesKeywords) {
                active.add(skill)
            }
        }

        return active
    }

    /**
     * 构建 Gemini 官方标准的 ToolConfig 工具声明
     */
    fun buildToolConfigs(skills: List<UiStudioSkill>): List<GeminiToolConfig> {
        if (skills.isEmpty()) return emptyList()

        val declarations = mutableListOf<JsonObject>()
        if (skills.any { it.id == SPEC_TO_VIEW_SKILL_ID }) {
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
                                put("slotRole", buildJsonObject { put("type", "STRING"); put("description", "插槽语义角色(用于全自动装配): BACKGROUND, SPIN_BUTTON, REEL_SYMBOL, BUTTON, HUD_FRAME, TITLE_LOGO, BET_CONTROL") })
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
