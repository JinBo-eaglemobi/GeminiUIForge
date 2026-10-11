package org.gemini.ui.forge.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 视觉工作室模型体系分组定义
 * 
 * 用户明确要求规范：官方全能多模态系列排在最前，专属视觉生图系列排在后面。
 */
enum class ModelStudioGroup(
    val title: String,
    val icon: ImageVector,
    val brandColor: Color
) {
    /** ✨ 官方全能多模态系列（具备深度多轮视觉理解与混合思维链推理，排在第一位） */
    MULTIMODAL_REASONING(
        title = "Gemini 官方全能多模态系列",
        icon = Icons.Default.Psychology,
        brandColor = Color(0xFF2979FF)
    ),

    /** 🍌 专属视觉生图系列（专为游戏美术、按钮、图标与原图切片生成优化，排在第二位） */
    IMAGE_SPECIALIZED(
        title = "Nano Banana 专属视觉生图系列",
        icon = Icons.Default.AutoAwesome,
        brandColor = Color(0xFFFF9800)
    )
}

/**
 * 模型在视觉工作室中的特性元数据
 */
data class ModelStudioMeta(
    val model: GeminiModel,
    val group: ModelStudioGroup,
    val badge: String,
    val summaryZh: String
)

/**
 * 视觉工作室模型动态能力探测与自适应解析中心。
 *
 * 核心架构原则：
 * 1. 100% 独立于单元测试自动生成的 GeminiModel.kt 文件，绝不污染原始枚举；
 * 2. 运行时直接从 [GeminiModel.entries] 全量动态探测；
 * 3. 采用鲁棒的正则表达式匹配，彻底杜绝死板字符串写死导致的漏检或版本错乱！
 */
object StudioModelResolver {

    // 过滤噪声模型的正则（语音合成、向量嵌入、Gemma 微模型、问答模型）
    private val NOISE_MODEL_REGEX = Regex("""(tts|embedding|gemma|aqa)""", RegexOption.IGNORE_CASE)

    // 生图专属特征探测正则
    private val IMAGE_SPECIALIZED_REGEX = Regex("""(image|imagen|banana)""", RegexOption.IGNORE_CASE)

    // 视觉工作室核心可用模型特征正则（包含版本号模式 \d+(\.\d+)?，以及 flash/pro 核心主干）
    private val CANDIDATE_FEATURE_REGEX = Regex("""(image|banana|flash|pro|\bv?\d+(\.\d+)?)""", RegexOption.IGNORE_CASE)

    // 版本号抽取正则（如 3.8, 3.1, 2.5）
    private val VERSION_EXTRACT_REGEX = Regex("""(?:gemini[-_]?)?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    /**
     * 动态从 [GeminiModel.entries] 解析出所有适合视觉工作室的模型，并附带自适应元数据。
     * 自动过滤纯语音 (TTS)、向量 (Embedding) 等无关模型。
     */
    fun resolveStudioModels(): List<ModelStudioMeta> {
        return GeminiModel.entries
            .filter { model ->
                // 1. 必须支持内容生成 (generateContent)
                val supportsGen = model.supportedMethods.contains("generateContent", ignoreCase = true)
                // 2. 正则过滤无关噪声
                val isNoise = NOISE_MODEL_REGEX.containsMatchIn(model.modelName)
                // 3. 必须符合工作室候选特征
                supportsGen && !isNoise && isStudioCandidate(model)
            }
            .map { model ->
                buildModelMeta(model)
            }
            .sortedWith(
                // 先按分组排序（MULTIMODAL_REASONING 在前，IMAGE_SPECIALIZED 在后）
                compareBy<ModelStudioMeta> { it.group.ordinal }
                    // 再按版本与旗舰优先级动态降序排序
                    .thenByDescending { getModelPriority(it.model) }
            )
    }

    /**
     * 判定是否属于视觉工作室候选模型（基于正则动态语义匹配，零硬编码字符串）
     */
    private fun isStudioCandidate(model: GeminiModel): Boolean {
        val name = model.modelName
        val display = model.displayName
        return CANDIDATE_FEATURE_REGEX.containsMatchIn(name) || CANDIDATE_FEATURE_REGEX.containsMatchIn(display)
    }

    /**
     * 模型优先级权重计算（用于排序：基于正则提取的主版本号与特性权重综合打分）
     */
    private fun getModelPriority(model: GeminiModel): Int {
        val name = model.modelName.lowercase()
        val display = model.displayName.lowercase()

        // 1. 提取版本号基础分（例如 3.8 -> 380, 3.1 -> 310, 2.5 -> 250）
        val versionMatch = VERSION_EXTRACT_REGEX.find(name)
        val versionScore = versionMatch?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 100).toInt() } ?: 200

        // 2. 特性加权（最新/预览/旗舰增加权重分）
        var bonus = 0
        if (name.contains("latest") || display.contains("latest")) bonus += 30
        if (name.contains("pro") || display.contains("pro")) bonus += 25
        if (name.contains("flash") || display.contains("flash")) bonus += 15
        if (name.contains("preview") || display.contains("preview")) bonus += 10
        if (name.contains("image") || display.contains("banana")) bonus += 5

        return versionScore + bonus
    }

    /**
     * 动态组装单个模型的元数据（全部字段源自服务器返回数据，按名称规则自适应推导）
     */
    private fun buildModelMeta(model: GeminiModel): ModelStudioMeta {
        // 1. 判定分组体系（生图专属 vs 多模态推理）
        val isImageSpecialized = IMAGE_SPECIALIZED_REGEX.containsMatchIn(model.modelName) ||
                IMAGE_SPECIALIZED_REGEX.containsMatchIn(model.displayName)
        val group = if (isImageSpecialized) ModelStudioGroup.IMAGE_SPECIALIZED else ModelStudioGroup.MULTIMODAL_REASONING

        // 2. 按特征推导标签徽标
        val n = model.modelName.lowercase()
        val badge = when {
            n.contains("pro") -> "旗舰"
            n.contains("lite") -> "轻量"
            n.contains("latest") || n.contains("preview") -> "最新"
            else -> "通用"
        }

        // 3. 特性说明直接采用官方描述
        val summary = model.description.ifBlank { "${model.modelName} 官方多模态模型" }

        return ModelStudioMeta(
            model = model,
            group = group,
            badge = badge,
            summaryZh = summary
        )
    }
}
