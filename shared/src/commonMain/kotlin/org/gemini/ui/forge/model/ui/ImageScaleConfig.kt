package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Stable

/**
 * 图片主体自适应缩放与偏置对齐配置 (Image Scale & Alignment Config)
 *
 * 用于使带透明通道/外发光特效的生成图片，其不透明核心主体能够与模块 bounds (及设计参考图) 1:1 精准重合。
 *
 * @property scaleX 水平缩放因数 (1.0 = 原始尺寸)
 * @property scaleY 垂直缩放因数 (1.0 = 原始尺寸)
 * @property offsetX 渲染水平偏置像素 (相对于模块左上角原点)
 * @property offsetY 渲染垂直偏置像素 (相对于模块左上角原点)
 * @property lockAspectRatio 是否锁定等比缩放
 * @property enabled 是否激活主体自适应对齐与自定义缩放
 * @property opaqueBounds 自动检测出的核心不透明内容在原始图像中的像素级包围盒 [l, t, r, b]
 */
@Stable
@Serializable
data class ImageScaleConfig(
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val lockAspectRatio: Boolean = true,
    val enabled: Boolean = false,
    val opaqueBounds: SerialRect? = null
) {
    val isDefault: Boolean
        get() = !enabled && scaleX == 1.0f && scaleY == 1.0f && offsetX == 0f && offsetY == 0f
}
