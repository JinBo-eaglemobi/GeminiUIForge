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
     * 已知核心主力模型的精修元数据配置表
     */
    private val curatedMetaMap: Map<GeminiModel, Pair<String, String>> = mapOf(
        GeminiModel.GEMINI_3_1_FLASH_IMAGE to Pair(
            "最新一代",
            "最新一代智能生图大模型，质感细腻、光影自然、响应极速"
        ),
        GeminiModel.GEMINI_3_PRO_IMAGE to Pair(
            "旗舰渲染",
            "专为商业级高精度设计渲染优化，材质逼真、外发光层次丰富"
        ),
        GeminiModel.GEMINI_2_5_FLASH_IMAGE to Pair(
            "快速生成",
            "经典快速视觉图像生成，适合低成本创意草稿探索"
        ),
        GeminiModel.GEMINI_3_1_FLASH_LITE_IMAGE to Pair(
            "超轻量",
            "超低延迟轻量图像生成，秒级验证构图与色彩方向"
        ),
        GeminiModel.GEMINI_3_7_FLASH to Pair(
            "混合推理",
            "具备复杂视觉空间理解与混合思维链的最新多模态旗舰"
        ),
        GeminiModel.GEMINI_2_5_PRO to Pair(
            "高精度构图",
            "超强复杂语义解析与高难度多轮构图推理"
        ),
        GeminiModel.GEMINI_2_5_FLASH to Pair(
            "极速多模态",
            "极速响应与低延迟视觉多模态交互"
        )
    )

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
     * 动态组装单个模型的元数据（已知模型使用精修配置，未知新模型自动语义推导）
     */
    private fun buildModelMeta(model: GeminiModel): ModelStudioMeta {
        val curated = curatedMetaMap[model]

        // 1. 判定分组体系
        val isImageSpecialized = model.modelName.contains("image", ignoreCase = true) ||
                model.displayName.contains("Banana", ignoreCase = true)
        val group = if (isImageSpecialized) ModelStudioGroup.IMAGE_SPECIALIZED else ModelStudioGroup.MULTIMODAL_REASONING

        // 2. 推导标签徽标
        val badge = if (curated != null) {
            curated.first
        } else {
            val n = model.modelName.lowercase()
            when {
                n.contains("pro") -> "旗舰"
                n.contains("lite") -> "轻量"
                n.contains("latest") || n.contains("preview") -> "最新"
                else -> "通用"
            }
        }

        // 3. 推导特性说明
        val summary = if (curated != null) {
            curated.second
        } else {
            // 未知新模型：自适应提取官方描述或名称
            model.description.ifBlank { "${model.displayName} 官方多模态模型" }.take(60)
        }

        return ModelStudioMeta(
            model = model,
            group = group,
            badge = badge,
            summaryZh = summary
        )
    }
}
