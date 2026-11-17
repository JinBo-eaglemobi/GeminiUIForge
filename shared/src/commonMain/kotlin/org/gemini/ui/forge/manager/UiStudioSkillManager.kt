package org.gemini.ui.forge.manager

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.resource.ExternalResourceLoader
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.chat.MentionPayload
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.SkillMarkdownParser
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.service.ai.GeminiToolConfig
import org.gemini.ui.forge.service.tool.ai.AiToolRegistry
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * UI Studio 技能与智能体能力调度管理器（标准 AI 智能体 Markdown 规范）
 *
 * 【核心架构原则】：
 * 1. 技能配置文件与人设指令 100% 外部 Markdown 文件化（YAML Frontmatter + Markdown Instructions），代码中 0 技能数据硬编码；
 * 2. 复用通用 ExternalResourceLoader 基础加载器，外部优先 -> 内置出厂预设兜底，Cache Miss 时自动释放落盘到磁盘；
 * 3. 动态扫描支持：自动感知 ~/.geminiuiforge/skills/<skill_id>/SKILL.md 子目录下新增的标准技能并动态挂载；
 * 4. 严格遵循完全按需懒加载原则（On-Demand Loading），使用时异步加载并进入内存缓存，启动期零 I/O；
 * 5. 独立于 PromptManager，专职于技能结构反序列化与运行时工具调度。
 */
object UiStudioSkillManager {
    private const val TAG = "UiStudioSkillManager"
    private const val SKILLS_DIR = "skills"
    private const val SKILL_ENTRY_FILE = "SKILL.md"
    private const val BUILTIN_SKILLS_MANIFEST = "builtin_skills.json"

    private val storage = LocalFileStorage()
    val resourceLoader = ExternalResourceLoader(storage)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 内存技能缓存 */
    private var cachedSkills: List<UiStudioSkill>? = null

    /**
     * 按需获取已注册的全部技能列表（纯动态外部目录扫描 + 数据驱动出厂自愈）
     * 【架构铁律】：代码中 0 技能名称硬编码，技能清单与元数据 100% 由外部 Markdown 自描述驱动
     */
    suspend fun getRegisteredSkills(bypassCache: Boolean = false): List<UiStudioSkill> {
        if (!bypassCache && cachedSkills != null) {
            return cachedSkills!!
        }

        val loadedMap = LinkedHashMap<String, UiStudioSkill>()

        // 1. 出厂内置技能自愈释放（纯数据驱动：读取 skills/builtin_skills.json 配置清单）
        try {
            val manifestContent = resourceLoader.loadDefaultResourceText(SKILLS_DIR, BUILTIN_SKILLS_MANIFEST)
            val builtinSkillDirs = json.decodeFromString<List<String>>(manifestContent)
            for (dirName in builtinSkillDirs) {
                try {
                    val relativePath = "$SKILLS_DIR/$dirName/$SKILL_ENTRY_FILE"
                    if (!storage.exists(relativePath)) {
                        // 外部缺失时，通过 resourceLoader 自动从内置出厂资源释放到本地磁盘对应子目录
                        resourceLoader.loadText(SKILLS_DIR, "$dirName/$SKILL_ENTRY_FILE", bypassCache = true)
                        AppLogger.i(TAG, "🌱 成功自愈释放出厂技能至本地: $relativePath")
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "自愈释放出厂技能 [$dirName/$SKILL_ENTRY_FILE] 失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "未配置或读取出厂技能清单失败: ${e.message}")
        }

        // 2. 纯动态扫描外部 skills/ 目录下的所有独立技能子目录（Folder-per-Skill 业界标准架构）
        try {
            val skillDirs = storage.listDirectories(SKILLS_DIR)
            for (dirName in skillDirs) {
                if (dirName.startsWith(".")) continue

                val entryRelativePath = "$SKILLS_DIR/$dirName/$SKILL_ENTRY_FILE"
                try {
                    val content = storage.readFromFile(entryRelativePath)
                    if (!content.isNullOrBlank()) {
                        val skill = SkillMarkdownParser.parse(content, defaultId = dirName)
                        if (!loadedMap.containsKey(skill.id)) {
                            loadedMap[skill.id] = skill
                            AppLogger.i(TAG, "✨ 成功动态加载技能: ${skill.name} (${skill.id}) [来自 $entryRelativePath]")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "解析外部技能目录 [$dirName] 失败: ${e.message}")
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
        val hasFolderMention = attachedMentions.any { it.payload is MentionPayload.AssetFolderPayload }

        for (skill in skills) {
            val matchesKeywords = skill.effectiveTriggers.any { lowerPrompt.contains(it.lowercase()) }
            if (hasDocMention || hasAllAssetsMention || hasFolderMention || matchesKeywords) {
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
        val coreConfigs = AiToolRegistry.buildStudioCoreGeminiTools(
            enableGoogleSearch = enableGoogleSearch,
            extraExplicitToolNames = extraExplicitToolNames
        )
        val declarations = mutableListOf<JsonObject>()

        // 注入精炼核心工作区工具与探索元工具（单一事实源统一由 AiToolRegistry 驱动，严禁手动重复追加）
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
}
