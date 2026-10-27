package org.gemini.ui.forge.manager

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.resource.ExternalResourceLoader
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.SkillMarkdownParser
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.service.ai.GeminiToolConfig
import org.gemini.ui.forge.service.ai.UniversalAiToolRegistry
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * UI Studio 技能与智能体能力调度管理器（标准 AI 智能体 Markdown 规范）
 *
 * 【核心架构原则】：
 * 1. 技能配置文件与人设指令 100% 外部 Markdown 文件化（YAML Frontmatter + Markdown Instructions），代码中 0 技能数据硬编码；
 * 2. 复用通用 ExternalResourceLoader 基础加载器，外部优先 -> 内置出厂预设兜底，Cache Miss 时自动释放落盘到磁盘；
 * 3. 动态扫描支持：自动感知 ~/.geminiuiforge/skills/ 目录下新增的 *.md 技能并动态挂载；
 * 4. 严格遵循完全按需懒加载原则（On-Demand Loading），使用时异步加载并进入内存缓存，启动期零 I/O；
 * 5. 独立于 PromptManager，专职于技能结构反序列化与运行时工具调度。
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
     * 按需获取已注册的全部技能列表（标准 Markdown 优先 + 出厂自愈释放 + 外部扫描）
     * 【自愈铁律】：若外部磁盘不存在实体文件，自动触发强制释放落盘建立外部目录
     */
    suspend fun getRegisteredSkills(bypassCache: Boolean = false): List<UiStudioSkill> {
        val hasExternalFile = storage.exists("$SKILLS_DIR/$SPEC_TO_VIEW_SKILL_ID.md")
        if (!bypassCache && cachedSkills != null && hasExternalFile) {
            return cachedSkills!!
        }

        // 清理旧版历史残留的 spec_to_view.json（如有）
        try {
            if (storage.exists("$SKILLS_DIR/$SPEC_TO_VIEW_SKILL_ID.json")) {
                storage.deleteFile("$SKILLS_DIR/$SPEC_TO_VIEW_SKILL_ID.json")
                AppLogger.i(TAG, "已安全清理旧版非标 JSON 技能文件: $SPEC_TO_VIEW_SKILL_ID.json")
            }
        } catch (_: Exception) {}

        val loadedMap = LinkedHashMap<String, UiStudioSkill>()

        // 1. 加载核心 Spec-to-View Markdown 技能（若外部磁盘不存在，通过 loadText 自动触发强制释放落盘）
        try {
            val mdContent = resourceLoader.loadText(SKILLS_DIR, "$SPEC_TO_VIEW_SKILL_ID.md", bypassCache = !hasExternalFile)
            val skill = SkillMarkdownParser.parse(mdContent, defaultId = SPEC_TO_VIEW_SKILL_ID)
            loadedMap[skill.id] = skill
        } catch (e: Exception) {
            AppLogger.e(TAG, "加载技能 [$SPEC_TO_VIEW_SKILL_ID.md] 失败", e)
        }

        // 2. 扫描外部 skills/ 目录下的所有 *.md 与 *.json 自定义技能
        try {
            val files = storage.listFiles(SKILLS_DIR)
            for (fileName in files) {
                if (fileName.equals("$SPEC_TO_VIEW_SKILL_ID.md", ignoreCase = true)) continue
                if (fileName.equals("disabled_skills.json", ignoreCase = true) || fileName.startsWith(".")) continue
                val relativePath = "$SKILLS_DIR/$fileName"
                try {
                    val content = storage.readFromFile(relativePath)
                    if (!content.isNullOrBlank()) {
                        val skill = if (fileName.endsWith(".md", ignoreCase = true)) {
                            val defaultId = fileName.substringBeforeLast(".")
                            SkillMarkdownParser.parse(content, defaultId = defaultId)
                        } else if (fileName.endsWith(".json", ignoreCase = true)) {
                            json.decodeFromString<UiStudioSkill>(content)
                        } else null

                        if (skill != null && !loadedMap.containsKey(skill.id)) {
                            loadedMap[skill.id] = skill
                            AppLogger.i(TAG, "✨ 成功动态加载外部技能: ${skill.name} (${skill.id}) [来自 $fileName]")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "解析外部技能 [$fileName] 失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "扫描外部技能目录失败", e)
        }

        val allList = loadedMap.values.toList()
        cachedSkills = allList
        return allList
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
     * 读取用户被禁用的技能 ID 集合
     */
    suspend fun getDisabledSkillIds(): Set<String> {
        val content = storage.readFromFile("$SKILLS_DIR/disabled_skills.json") ?: return emptySet()
        return runCatching {
            json.decodeFromString<Set<String>>(content)
        }.getOrDefault(emptySet())
    }

    /**
     * 校验技能当前是否处于启用状态
     */
    suspend fun isSkillEnabled(skillId: String): Boolean {
        val disabledIds = getDisabledSkillIds()
        if (skillId in disabledIds) return false
        val allSkills = getRegisteredSkills()
        val target = allSkills.firstOrNull { it.id == skillId } ?: return false
        return target.isSkillEnabled
    }

    /**
     * 获取所有当前已启用的技能清单（已排除被禁用的技能，红线隔离）
     */
    suspend fun getEnabledSkills(): List<UiStudioSkill> {
        val disabledIds = getDisabledSkillIds()
        return getRegisteredSkills().filter { it.isSkillEnabled && it.id !in disabledIds }
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
            val matchesKeywords = skill.effectiveTriggers.any { lowerPrompt.contains(it.lowercase()) }
            if (hasDocMention || hasAllAssetsMention || matchesKeywords) {
                active.add(skill)
            }
        }

        return active
    }

    /**
     * 构建 Gemini 官方标准的 ToolConfig 工具声明
     *
     * 【自主大脑精炼工具集架构】：
     * 绝不平铺下发 43+ 个全量 MCP 工具（彻底切断 Slots 模板底层运维等噪音工具对大模型的干扰）；
     * 仅下发：专属界面排版装配工具 (apply_view_layout) + 工作区核心工具 + 4 个探索发现元工具 (search_tools, list_tools, search_skills, read_skill_spec)。
     * 大模型如同人类大脑，遇到未掌握的细节通过元工具自主搜索与索取。
     */
    fun buildToolConfigs(skills: List<UiStudioSkill> = emptyList(), enableGoogleSearch: Boolean = true): List<GeminiToolConfig> {
        val extraExplicitToolNames = skills.flatMap { it.toolNames }.toSet()
        val coreConfigs = UniversalAiToolRegistry.buildStudioCoreGeminiTools(
            enableGoogleSearch = enableGoogleSearch,
            extraExplicitToolNames = extraExplicitToolNames
        )
        val declarations = mutableListOf<JsonObject>()

        // 注入 apply_view_layout 专有视图排版工具
        declarations.add(buildApplyViewLayoutDeclaration())

        // 注入精炼核心工作区工具与 4 个探索元工具
        coreConfigs.forEach { config ->
            config.functionDeclarations?.let { declarations.addAll(it) }
        }

        return listOf(
            GeminiToolConfig(
                functionDeclarations = declarations,
                googleSearch = if (enableGoogleSearch) buildJsonObject {} else null
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
