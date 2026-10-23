package org.gemini.ui.forge.manager

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gemini.ui.forge.manager.resource.ExternalResourceLoader
import org.gemini.ui.forge.model.prompt.PromptKey
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 提示词模板元数据（供设置界面与 MCP 列举说明展示使用）
 *
 * @param id 提示词唯一标识符（对应文件名）
 * @param displayNameZh 中文业务显示名称
 * @param descZh 业务用途描述
 * @param isMarkdown 是否为 Markdown 格式扩展名 (.md)
 */
data class PromptMeta(
    val id: String,
    val displayNameZh: String,
    val descZh: String,
    val isMarkdown: Boolean = false
) {
    val fileName: String
        get() {
            val key = PromptKey.fromId(id)
            return key?.fileName ?: if (isMarkdown) "$id.md" else "$id.txt"
        }
}

/**
 * AI 提示词管理器
 *
 * 核心架构原则：
 * 1. 复用统一的公用基础加载器 ExternalResourceLoader，外部本地存储 (~/.geminiuiforge/prompts/$name) 绝对优先；
 * 2. 外部文件不存在时，按需回退读取内置资源作为出厂预设；
 * 3. 彻底按需懒加载 (On-Demand Loading)，启动期零全量扫描与预载，使用时自动进入内存轻量缓存；
 * 4. 强类型枚举 PromptKey 优先，杜绝魔法字符串传参。
 */
class PromptManager(
    private val storage: LocalFileStorage,
    val resourceLoader: ExternalResourceLoader = ExternalResourceLoader(storage)
) {
    private val TAG = "PromptManager"
    private val PROMPTS_DIR = "prompts"

    /** 内存图元默认提示词解析缓存 */
    private var blockTypePromptsCache: Map<String, String>? = null

    /** 全案注册的核心提示词与规范模板清单（用于设置界面与系统自检展示） */
    val promptMetas = listOf(
        PromptMeta("analyze_template", "整页 UI 识别与结构化分析", "用于 Gemini 视觉大模型首次分析参考图并自动推断页面所有 UI 模块结构与坐标"),
        PromptMeta("refine_template", "局部区域重构与结构微调", "用于对页面局部区域进行二次重塑与结构化调整"),
        PromptMeta("IMAGE_TO_UI_SPEC", "UI 模块规范与组件字典 (公用方案)", "定义图元层级、转轴容器与符号集解耦标准（全图识别与局部重构自动附带）", isMarkdown = true),
        PromptMeta("ai_optimize_prompt", "AI 提示词一键精炼优化", "用于将简短描述扩充为包含材质、光影、细节的高质量生图提示词"),
        PromptMeta("optimize_instruction_zh", "中文生图提示词优化指令", "针对中文生图意图的系统优化约束指令"),
        PromptMeta("optimize_instruction_en", "英文生图提示词优化指令", "针对英文生图意图的高清材质与渲染风格系统约束指令"),
        PromptMeta("refine_instruction_update", "模块局部修改默认指令", "在局部重塑时，针对已有模块进行修改的默认指令模板"),
        PromptMeta("refine_instruction_new", "模块新增生成默认指令", "在局部重塑时，针对新选区生成新模块的默认指令模板"),
        PromptMeta("chat_refine_instruction", "AI 改图指令组装模板", "用于在会话中根据用户微调指令扩充和组装图元生图提示词"),
        PromptMeta("gemini_image_gen", "Gemini 图像生成组装模板", "调用 Gemini 视觉模型生成单体 UI 图像时的顶层提示词组装结构"),
        PromptMeta("detect_asset_sheet_coords", "图集 SpriteSheet AI 视觉坐标检测", "用于调用 Gemini 视觉模型深度识别素材图集中的全部独立组件并提取精准外切坐标与类型"),
        PromptMeta("normalize_asset_sheet", "图集标注清洗系统提示词", "将非标图集数据清洗转化为标准 JSON 格式的系统人设"),
        PromptMeta("normalize_asset_sheet_user", "图集标注清洗用户前缀模板", "提交给图集清洗引擎的用户提示词前缀"),
        PromptMeta("block_type_prompts", "图元组件默认生图提示词字典", "定义各类游戏 UI 组件的标准英文出厂提示词")
    )

    private fun resolveFileName(functionName: String): String {
        val key = PromptKey.fromId(functionName)
        if (key != null) return key.fileName
        return promptMetas.find { it.id == functionName }?.fileName ?: "$functionName.txt"
    }

    /**
     * 文本换行符归一化与两端空白修剪。
     */
    fun normalizeText(text: String): String {
        return text.replace("\r\n", "\n").trim()
    }

    // ==========================================
    // 强类型枚举 API（优先通道）
    // ==========================================

    /**
     * 按需获取指定枚举键的提示词内容（外部本地优先 -> 内置资源兜底 -> 内存缓存）
     */
    suspend fun getPrompt(key: PromptKey): String {
        return resourceLoader.loadText(PROMPTS_DIR, key.fileName)
    }

    /**
     * 按需获取内置打包出厂预设提示词内容
     */
    suspend fun getDefaultResourcePrompt(key: PromptKey): String {
        return resourceLoader.loadDefaultResourceText(PROMPTS_DIR, key.fileName)
    }

    /**
     * 保存用户定制的提示词到外部本地文件并即时更新缓存
     */
    suspend fun savePrompt(key: PromptKey, content: String): Boolean {
        if (key == PromptKey.BLOCK_TYPE_PROMPTS) {
            blockTypePromptsCache = null
        }
        return resourceLoader.saveExternalText(PROMPTS_DIR, key.fileName, content)
    }

    /**
     * 重置提示词，删除外部定制文件并回滚出厂
     */
    suspend fun resetPrompt(key: PromptKey): Boolean {
        if (key == PromptKey.BLOCK_TYPE_PROMPTS) {
            blockTypePromptsCache = null
        }
        return resourceLoader.deleteExternalText(PROMPTS_DIR, key.fileName)
    }

    /**
     * 判断指定提示词是否存在外部本地文件
     */
    suspend fun hasPhysicalExternalFile(key: PromptKey): Boolean {
        return resourceLoader.hasExternalFile(PROMPTS_DIR, key.fileName)
    }

    /**
     * 判断提示词是否被用户实质定制过
     */
    suspend fun isCustomized(key: PromptKey): Boolean {
        if (!hasPhysicalExternalFile(key)) return false
        val cachedContent = resourceLoader.loadText(PROMPTS_DIR, key.fileName, bypassCache = true)
        val defaultContent = getDefaultResourcePrompt(key)
        return normalizeText(cachedContent) != normalizeText(defaultContent)
    }

    // ==========================================
    // 字符串 API（兼容通道，供设置界面与 MCP 调用）
    // ==========================================

    suspend fun getPrompt(functionName: String): String {
        val key = PromptKey.fromId(functionName)
        if (key != null) return getPrompt(key)
        val fileName = resolveFileName(functionName)
        return resourceLoader.loadText(PROMPTS_DIR, fileName)
    }

    suspend fun getDefaultResourcePrompt(functionName: String): String {
        val key = PromptKey.fromId(functionName)
        if (key != null) return getDefaultResourcePrompt(key)
        val fileName = resolveFileName(functionName)
        return resourceLoader.loadDefaultResourceText(PROMPTS_DIR, fileName)
    }

    suspend fun savePrompt(functionName: String, content: String): Boolean {
        val key = PromptKey.fromId(functionName)
        if (key != null) return savePrompt(key, content)
        val fileName = resolveFileName(functionName)
        return resourceLoader.saveExternalText(PROMPTS_DIR, fileName, content)
    }

    suspend fun resetPrompt(functionName: String): Boolean {
        val key = PromptKey.fromId(functionName)
        if (key != null) return resetPrompt(key)
        val fileName = resolveFileName(functionName)
        return resourceLoader.deleteExternalText(PROMPTS_DIR, fileName)
    }

    suspend fun hasPhysicalExternalFile(functionName: String): Boolean {
        val key = PromptKey.fromId(functionName)
        if (key != null) return hasPhysicalExternalFile(key)
        val fileName = resolveFileName(functionName)
        return resourceLoader.hasExternalFile(PROMPTS_DIR, fileName)
    }

    suspend fun isCustomized(functionName: String): Boolean {
        val key = PromptKey.fromId(functionName)
        if (key != null) return isCustomized(key)
        if (!hasPhysicalExternalFile(functionName)) return false
        val fileName = resolveFileName(functionName)
        val cachedContent = resourceLoader.loadText(PROMPTS_DIR, fileName, bypassCache = true)
        val defaultContent = getDefaultResourcePrompt(functionName)
        return normalizeText(cachedContent) != normalizeText(defaultContent)
    }

    // ==========================================
    // 业务特化方法（按需加载与解耦）
    // ==========================================

    /**
     * 按需获取 IMAGE_TO_UI_SPEC 规范内容（外部缓存优先）
     */
    suspend fun getImageToUiSpec(): String {
        return getPrompt(PromptKey.IMAGE_TO_UI_SPEC)
    }

    /**
     * 启动时自检并同步核心规范与 Agent 人设（仅在外部不存在时自愈释放出厂备份）
     */
    suspend fun ensurePromptsSyncedToCache() {
        ensureSpecSyncedToCache()
        val agentPromptRel = "$PROMPTS_DIR/${PromptKey.UI_STUDIO_AGENT_PROMPT.fileName}"
        try {
            if (!storage.exists(agentPromptRel)) {
                val content = getDefaultResourcePrompt(PromptKey.UI_STUDIO_AGENT_PROMPT)
                storage.saveToFile(agentPromptRel, content)
                AppLogger.i(TAG, "🌱 成功将出厂 Agent 人设释放至外部缓存: $agentPromptRel")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "启动同步 Agent 人设失败: $agentPromptRel", e)
        }
    }

    /**
     * 启动时同步 IMAGE_TO_UI_SPEC（仅在外部不存在时同步一次）
     */
    suspend fun ensureSpecSyncedToCache() {
        val relPath = "$PROMPTS_DIR/${PromptKey.IMAGE_TO_UI_SPEC.fileName}"
        try {
            if (!storage.exists(relPath)) {
                val content = getDefaultResourcePrompt(PromptKey.IMAGE_TO_UI_SPEC)
                storage.saveToFile(relPath, content)
                AppLogger.i(TAG, "已在应用启动时将 IMAGE_TO_UI_SPEC 规范自动同步到本地缓存: $relPath")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动同步 IMAGE_TO_UI_SPEC 规范失败", e)
        }
    }

    /**
     * 按需获取特定图元类型的默认英文生图提示词
     */
    suspend fun getBlockTypePrompt(type: UIBlockType): String {
        val cache = blockTypePromptsCache ?: run {
            try {
                val jsonText = getPrompt(PromptKey.BLOCK_TYPE_PROMPTS)
                val json = Json.parseToJsonElement(jsonText).jsonObject
                val map = json.mapValues { it.value.jsonPrimitive.content }
                blockTypePromptsCache = map
                map
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析 block_type_prompts.json 失败", e)
                emptyMap()
            }
        }
        return cache[type.name] ?: ""
    }

    companion object {
        private const val PROMPTS_DIR = "prompts"
        private val AGENT_PROMPT_FILE = PromptKey.UI_STUDIO_AGENT_PROMPT.fileName // "ui_studio_agent_prompt.md"

        /**
         * 读取 UI Studio 项目专属 Agent 的全局系统人设提示词（外部缓存优先 -> 内置出厂种子）。
         * 提示词完全外部化存储于 prompts/ui_studio_agent_prompt.md 中，严禁在代码中硬编码长字符串。
         */
        suspend fun getUiStudioAgentPrompt(storage: LocalFileStorage = LocalFileStorage()): String {
            return try {
                val loader = ExternalResourceLoader(storage)
                loader.loadText(PROMPTS_DIR, AGENT_PROMPT_FILE)
            } catch (e: Exception) {
                AppLogger.e("PromptManager", "读取 $AGENT_PROMPT_FILE 失败", e)
                ""
            }
        }

        /**
         * 保存用户定制的 UI Studio 智能设计助理 Agent 系统提示词到全局外部缓存 (~/.geminiuiforge/prompts/ui_studio_agent_prompt.md)
         */
        suspend fun saveUiStudioAgentPrompt(newPrompt: String, storage: LocalFileStorage = LocalFileStorage()): Boolean {
            return try {
                val loader = ExternalResourceLoader(storage)
                loader.saveExternalText(PROMPTS_DIR, AGENT_PROMPT_FILE, newPrompt)
            } catch (e: Exception) {
                AppLogger.e("PromptManager", "保存 $AGENT_PROMPT_FILE 失败", e)
                false
            }
        }

        /**
         * 重置 UI Studio 智能设计助理 Agent 系统提示词为出厂预设
         */
        suspend fun resetUiStudioAgentPrompt(storage: LocalFileStorage = LocalFileStorage()): Boolean {
            return try {
                val loader = ExternalResourceLoader(storage)
                loader.deleteExternalText(PROMPTS_DIR, AGENT_PROMPT_FILE)
            } catch (e: Exception) {
                AppLogger.e("PromptManager", "重置 $AGENT_PROMPT_FILE 失败", e)
                false
            }
        }
    }
}
