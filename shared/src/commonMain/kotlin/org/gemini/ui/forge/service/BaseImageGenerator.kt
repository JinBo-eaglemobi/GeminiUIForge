package org.gemini.ui.forge.service

import org.gemini.ui.forge.utils.AppLogger
import kotlin.math.abs

/**
 * 图片生成器的基础参数与公共逻辑
 */
abstract class BaseImageGenerator {
    
    /**
     * 图片生成的各种配置参数
     */
    data class GenParams(
        /** 组件/功能块类型名称 */
        val blockType: String,
        /** 用户输入的原始提示词 */
        val userPrompt: String,
        /** 本次请求生成的图片张数 */
        val count: Int,
        /** API Key (如果需要覆盖全局配置) */
        val apiKey: String,
        /** 目标图片宽度 (可选) */
        val targetWidth: Float? = null,
        /** 目标图片高度 (可选) */
        val targetHeight: Float? = null,
        /** 是否生成透明背景 PNG */
        val isPng: Boolean = false,
        /** 图片尺寸规格 (如 1k, 2k 等) */
        val imageSize: String = "1k",
        /** 艺术风格名称 */
        val style: String = "",
        /** 参考图的 URI 路径 */
        val referenceImageUri: String? = null,
        /** 是否强制走 Vertex AI 路由 */
        val isVertexAI: Boolean = false
    )

    /** 计算最接近的标准比例 */
    protected fun calculateAspectRatio(width: Float?, height: Float?): String {
        if (width != null && height != null && height > 0) {
            val ratio = width / height
            val options = mapOf(
                "1:1" to 1.0f,
                "4:3" to 1.333f,
                "3:4" to 0.75f,
                "16:9" to 1.777f,
                "9:16" to 0.562f,
                "3:2" to 1.5f,
                "2:3" to 0.666f
            )
            return options.minByOrNull { abs(it.value - ratio) }?.key ?: "1:1"
        }
        return "1:1"
    }

    /** 根据 imageSize 动态定义分辨率基准 (1k=1024, 2k=2048) */
    protected fun getResolutionBase(imageSize: String): Float =
        if (imageSize.lowercase().contains("2k")) 2048f else 1024f

    /** 计算形状类型 */
    protected fun calculateShapeType(width: Float?, height: Float?): String {
        val ratio = if (width != null && height != null && height > 0) width / height else 1.0f
        return when {
            ratio > 2.5f -> "ultra-wide horizontal"
            ratio > 1.2f -> "rectangular"
            ratio < 0.4f -> "ultra-tall vertical"
            ratio < 0.8f -> "tall"
            else -> "square-shaped and blocky"
        }
    }

    protected fun syncLog(tag: String, message: String, onLog: (String) -> Unit) {
        onLog(message)
        AppLogger.i(tag, message)
    }
}
