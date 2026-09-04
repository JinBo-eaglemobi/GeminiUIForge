package org.gemini.ui.forge.manager

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 提示词模板元数据
 *
 * @param id 提示词唯一标识符（对应文件名）
 * @param displayNameZh 中文业务显示名称
 * @param descZh 业务用途描述
 */
data class PromptMeta(
    val id: String,
    val displayNameZh: String,
    val descZh: String
)

/**
 * AI 提示词管理器
 *
 * 核心架构原则：
 * 1. 外部本地存储 (~/.geminiuiforge/prompts/$name.txt) 具有最高优先级；
 * 2. 只有在外部缓存不存在时，才读取 Jar 包内置资源作为出厂兜底；
 * 3. 用户在设置界面修改后，一律写入外部本地存储；
 * 4. 用户点击"恢复出厂预设"时，删除外部缓存，回滚至内置资源。
 */
class PromptManager(private val storage: LocalFileStorage) {
    private val TAG = "PromptManager"
    private val PROMPTS_DIR = "prompts"

    /** 全案注册的 10 大核心提示词模板清单 */
    val promptMetas = listOf(
        PromptMeta("analyze_template", "整页 UI 识别与结构化分析", "用于 Gemini 视觉大模型首次分析参考图并自动推断页面所有 UI 模块结构与坐标"),
        PromptMeta("refine_template", "局部区域重构与结构微调", "用于对页面局部区域进行二次重塑与结构化调整"),
        PromptMeta("ai_optimize_prompt", "AI 提示词一键精炼优化", "用于将简短描述扩充为包含材质、光影、细节的高质量生图提示词"),
        PromptMeta("optimize_instruction_zh", "中文生图提示词优化指令", "针对中文生图意图的系统优化约束指令"),
        PromptMeta("optimize_instruction_en", "英文生图提示词优化指令", "针对英文生图意图的高清材质与渲染风格系统约束指令"),
        PromptMeta("refine_instruction_update", "模块局部修改默认指令", "在局部重塑时，针对已有模块进行修改的默认指令模板"),
        PromptMeta("refine_instruction_new", "模块新增生成默认指令", "在局部重塑时，针对新选区生成新模块的默认指令模板"),
        PromptMeta("cloud_bg_removal", "云端大模型透明抠图去背", "通过云端多模态大模型精准剔除背景、保留主体元素的提示词"),
        PromptMeta("image_gen_transparent", "透明背景生图引导指令", "指导视觉模型直接生成纯白/纯黑/可抠图背景的引导提示词"),
        PromptMeta("gemini_image_gen", "Gemini 图像生成组装模板", "调用 Gemini 视觉模型生成单体 UI 图像时的顶层提示词组装结构")
    )

    /**
     * 文本换行符归一化与两端空白修剪。
     * 用于消除 Windows 换行符（\r\n）与 Unix 换行符（\n）以及末尾空行导致的虚假差异。
     */
    fun normalizeText(text: String): String {
        return text.replace("\r\n", "\n").trim()
    }

    /**
     * 获取提示词内容（外部存储绝对优先）。
     * 逻辑：优先从外部本地存储读取；若不存在，则从内置 Resource 中读取。
     * 若两者均不存在或内容为空白，直接抛出 IllegalStateException，绝不做任何保底或兜底！
     *
     * @param functionName 提示词标识符
     * @return 最终生效的有效提示词文本内容
     * @throws IllegalStateException 当提示词文件缺失或内容为空白时直接抛出异常
     */
    @OptIn(InternalResourceApi::class)
    suspend fun getPrompt(functionName: String): String {
        val fileName = "$functionName.txt"
        val relativePath = "$PROMPTS_DIR/$fileName"

        // 1. 优先尝试从外部本地存储目录读取（用户修改过的配置即时生效）
        try {
            if (storage.exists(relativePath)) {
                val cachedContent = storage.readFromFile(relativePath)
                if (!cachedContent.isNullOrBlank()) {
                    return cachedContent
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read cached prompt: $fileName", e)
        }

        // 2. 外部缓存不存在时，从内部 Resource 读取出厂预设
        val defaultContent = getDefaultResourcePrompt(functionName)
        if (defaultContent.isBlank()) {
            throw IllegalStateException("提示词模板 [$functionName] 在外部缓存与内部资源中均未找到或内容为空白！")
        }
        return defaultContent
    }

    /**
     * 获取应用内部打包的原始出厂预设提示词（不读外部缓存）。
     * 若资源不存在或内容为空，直接抛出 IllegalStateException。
     */
    @OptIn(InternalResourceApi::class)
    suspend fun getDefaultResourcePrompt(functionName: String): String {
        val resourcePath = "prompts/$functionName.txt"
        return try {
            val content = readResourceBytes(resourcePath).decodeToString()
            if (content.isBlank()) {
                throw IllegalStateException("内置提示词资源 [$resourcePath] 内容为空！")
            }
            content
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read default prompt from resource: $resourcePath", e)
            throw IllegalStateException("内置提示词资源 [$resourcePath] 读取失败: ${e.message}", e)
        }
    }

    /**
     * 将用户修改后的提示词保存到外部本地存储中（一律保存在外部）。
     */
    suspend fun savePrompt(functionName: String, content: String): Boolean {
        val fileName = "$functionName.txt"
        val relativePath = "$PROMPTS_DIR/$fileName"
        return try {
            storage.saveToFile(relativePath, content)
            AppLogger.i(TAG, "Successfully saved custom prompt to external storage: $fileName")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save custom prompt to external storage: $fileName", e)
            false
        }
    }

    /**
     * 清除外部本地缓存的提示词，回滚至内部默认资源。
     */
    suspend fun resetPrompt(functionName: String): Boolean {
        val fileName = "$functionName.txt"
        val relativePath = "$PROMPTS_DIR/$fileName"
        return storage.deleteFile(relativePath)
    }

    /**
     * 判断当前提示词在外部本地磁盘上是否存在物理文件。
     */
    suspend fun hasPhysicalExternalFile(functionName: String): Boolean {
        val fileName = "$functionName.txt"
        val relativePath = "$PROMPTS_DIR/$fileName"
        return storage.exists(relativePath)
    }

    /**
     * 判断当前提示词是否被用户实质定制过。
     *
     * 精准判定准则：
     * 1. 外部本地存储存在该文件；
     * 2. 外部文件内容经换行符归一化（\r\n -> \n）与空白修剪后，与出厂内置默认内容存在实质差异。
     */
    suspend fun isCustomized(functionName: String): Boolean {
        val fileName = "$functionName.txt"
        val relativePath = "$PROMPTS_DIR/$fileName"
        if (!storage.exists(relativePath)) return false
        val cachedContent = storage.readFromFile(relativePath) ?: return false
        val defaultContent = getDefaultResourcePrompt(functionName)
        return normalizeText(cachedContent) != normalizeText(defaultContent)
    }
}
