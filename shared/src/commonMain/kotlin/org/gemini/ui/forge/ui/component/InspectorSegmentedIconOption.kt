package org.gemini.ui.forge.ui.component

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分段式图标切换器单项配置模型。
 *
 * @property key 选项唯一键值（如 "left" / "center" / "right"）
 * @property icon 展示的矢量图标
 * @property tooltip 悬停提示与无障碍描述文案
 */
data class InspectorSegmentedIconOption(
    val key: String,
    val icon: ImageVector,
    val tooltip: String
)
