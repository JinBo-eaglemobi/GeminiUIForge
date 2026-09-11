package org.gemini.ui.forge.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 视觉工作室模型体系分组定义
 */
enum class ModelStudioGroup(
    val title: String,
    val icon: ImageVector,
    val brandColor: Color
) {
    /** 🍌 专属视觉生图系列（专为游戏美术、按钮、图标与原图切片生成优化） */
    IMAGE_SPECIALIZED(
        title = "Nano Banana 专属视觉生图系列",
        icon = Icons.Default.AutoAwesome,
        brandColor = Color(0xFFFF9800)
    ),

    /** ✨ 官方全能多模态系列（具备深度多轮视觉理解与混合思维链推理） */
    MULTIMODAL_REASONING(
        title = "Gemini 官方全能多模态系列",
        icon = Icons.Default.Psychology,
        brandColor = Color(0xFF2979FF)
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
 * 2. 运行时直接从 [GeminiModel.entries] 全量动态探测；即使未来重新生成枚举或增加全新模型，
 *    系统也能自动感知并按特征智能归组、自动推导标签，新模型秒级生效！
 */
object StudioModelResolver {

    /**
     * 动态从 [GeminiModel.entries] 解析出所有适合视觉工作室的模型，并附带自适应元数据。
     * 自动过滤纯语音 (TTS)、向量 (Embedding) 等无关模型。
     */
    fun resolveStudioModels(): List<ModelStudioMeta> {
        return GeminiModel.entries
            .filter { model ->
                // 必须支持内容生成
                val supportsGen = model.supportedMethods.contains("generateContent", ignoreCase = true)
                // 排除语音、纯向量及本地微模型
                val isNoise = model.modelName.contains("tts", ignoreCase = true) ||
                        model.modelName.contains("embedding", ignoreCase = true) ||
                        model.modelName.contains("gemma", ignoreCase = true) ||
                        model.modelName.contains("aqa", ignoreCase = true)
                supportsGen && !isNoise && isStudioCandidate(model)
            }
            .map { model ->
                buildModelMeta(model)
            }
            .sortedWith(
                compareBy<ModelStudioMeta> { it.group.ordinal }
                    .thenByDescending { getModelPriority(it.model) }
            )
    }

    /**
     * 判定是否属于视觉工作室候选模型
     */
    private fun isStudioCandidate(model: GeminiModel): Boolean {
        val name = model.modelName.lowercase()
        return name.contains("image") ||
                model.displayName.contains("Banana", ignoreCase = true) ||
                name.contains("3.7") ||
                name.contains("2.5-pro") ||
                name.contains("2.5-flash") ||
                name.contains("flash-latest")
    }

    /**
     * 模型优先级权重计算（用于排序：最新/旗舰排在最前）
     */
    private fun getModelPriority(model: GeminiModel): Int {
        val name = model.modelName.lowercase()
        return when {
            name.contains("3.1-flash-image") -> 100
            name.contains("3-pro-image") -> 95
            name.contains("3.7-flash") -> 90
            name.contains("2.5-pro") -> 85
            name.contains("2.5-flash-image") -> 80
            name.contains("3.1-flash-lite-image") -> 75
            name.contains("2.5-flash") -> 70
            name.contains("latest") -> 65
            else -> 50
        }
    }

    /**
     * 动态组装单个模型的元数据（全部字段源自服务器返回数据，按名称规则自适应推导）
     */
    private fun buildModelMeta(model: GeminiModel): ModelStudioMeta {
        // 1. 判定分组体系
        val isImageSpecialized = model.modelName.contains("image", ignoreCase = true) ||
                model.displayName.contains("Banana", ignoreCase = true)
        val group = if (isImageSpecialized) ModelStudioGroup.IMAGE_SPECIALIZED else ModelStudioGroup.MULTIMODAL_REASONING

        // 2. 按名称规则推导标签徽标（不依赖人工维护表）
        val n = model.modelName.lowercase()
        val badge = when {
            n.contains("pro") -> "旗舰"
            n.contains("lite") -> "轻量"
            n.contains("latest") || n.contains("preview") -> "最新"
            else -> "通用"
        }

        // 3. 特性说明直接采用服务器返回的官方描述
        val summary = model.description.ifBlank { "${model.modelName} 官方多模态模型" }

        return ModelStudioMeta(
            model = model,
            group = group,
            badge = badge,
            summaryZh = summary
        )
    }
}
