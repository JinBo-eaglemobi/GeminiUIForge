package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.app.ReferenceDisplayMode
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import kotlin.math.roundToInt

/**
 * 全局浮动控制栏组件 (Canvas Floating Control Bar)。
 *
 * 悬浮在画布上方，提供对整个工作区的全局控制能力，包含：
 * 1. 画布缩放控制（放大、缩小、显示当前比例）。
 * 2. 视角复位（一键恢复 100% 缩放并居中）。
 * 3. 视觉模式切换（线框模式与纯视觉模式的切换）。
 * 4. 描边隐藏控制（Hide Outlines）。
 * 5. 参考图的高级控制（隐藏、分屏对比、叠加半透明对比）。
 *
 * @param zoom 当前画布的缩放比例（1.0 代表 100%）。
 * @param updateZoom 触发缩放更新的回调，接收新的缩放值和缩放的中心坐标 (Centroid)。
 * @param onResetZoom 触发复位操作的回调，将画布恢复初始状态。
 * @param centerOffset 当前视口的中心点坐标，用于基于屏幕中心进行缩放。
 * @param state 项目工作区状态
 * @param viewModel 项目工作区视图模型
 * @param modifier 修饰符。
 */
@Composable
fun CanvasFloatingControlBar(
    zoom: Float,
    updateZoom: (Float, Offset) -> Unit,
    onResetZoom: () -> Unit,
    centerOffset: Offset,
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val referenceUri = state.referenceImageUri?.getAbsolutePath()

    // 浮动面板的外层容器设置，包含圆角、背景色、边框和阴影，确保在画布上清晰可见
    Surface(
        modifier = modifier.padding(top = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shadowElevation = 6.dp
    ) {
        // 控制栏的内容主体为水平排列的工具组
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ==========================================
            // 1. 缩放控制区 (Zoom Controls)
            // ==========================================

            // 缩小按钮 (-20%)
            IconButton(
                onClick = { updateZoom(zoom - 0.2f, centerOffset) },
                modifier = Modifier.size(28.dp).tip("缩小视图")
            ) {
                Icon(Icons.Default.Remove, "缩小", modifier = Modifier.size(16.dp))
            }

            // 当前比例显示
            Box(
                modifier = Modifier.height(28.dp).width(42.dp).tip("当前缩放比例"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${(zoom * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }

            // 放大按钮 (+20%)
            IconButton(
                onClick = { updateZoom(zoom + 0.2f, centerOffset) },
                modifier = Modifier.size(28.dp).tip("放大视图")
            ) {
                Icon(Icons.Default.Add, "放大", modifier = Modifier.size(16.dp))
            }

            VerticalDivider(modifier = Modifier.height(16.dp))

            // ==========================================
            // 2. 视角复位区 (Reset View)
            // ==========================================
            IconButton(
                onClick = onResetZoom,
                modifier = Modifier.size(28.dp).tip("重置缩放并居中")
            ) {
                Icon(Icons.Default.Refresh, "复位画布", modifier = Modifier.size(18.dp))
            }

            VerticalDivider(modifier = Modifier.height(16.dp))

            // ==========================================
            // 3. 骨架网格与辅助线开关 (Wireframe Grid Toggle 二合一合并版)
            // 默认开启（高亮 GridOn），点击后一键隐藏骨架色块与边框线，进入 100% 纯净预览
            // ==========================================
            val isWireframeOn = !(state.isVisualMode && state.isHideOutlines)
            IconToggleButton(
                checked = isWireframeOn,
                onCheckedChange = { viewModel.toggleWireframe() },
                modifier = Modifier.size(28.dp).tip(
                    if (isWireframeOn) "骨架网格已开启，点击进入纯净预览模式"
                    else "当前为纯净预览，点击显示骨架网格与边框"
                )
            ) {
                Icon(
                    imageVector = if (isWireframeOn) Icons.Default.GridOn else Icons.Default.GridOff,
                    contentDescription = "骨架网格与辅助线",
                    modifier = Modifier.size(18.dp),
                    tint = if (isWireframeOn) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }

            // ==========================================
            // 4. 参考图控制区 (Reference Image Controls)
            // 仅当存在参考图 (referenceUri != null) 时才渲染此区域
            // ==========================================
            if (referenceUri != null) {
                VerticalDivider(modifier = Modifier.height(16.dp))

                // 参考图全局开关：判断当前是否是非隐藏状态
                val isRefEnabled = state.referenceMode != ReferenceDisplayMode.HIDDEN
                IconToggleButton(
                    checked = isRefEnabled,
                    onCheckedChange = {
                        // 开启时默认进入分屏模式，关闭时设为隐藏
                        viewModel.updateReferenceMode(if (it) ReferenceDisplayMode.SPLIT else ReferenceDisplayMode.HIDDEN)
                    },
                    modifier = Modifier.size(28.dp).tip("显示/隐藏参考图")
                ) {
                    Icon(
                        imageVector = if (isRefEnabled) Icons.Default.Image else Icons.Default.VisibilityOff,
                        contentDescription = "切换参考图",
                        modifier = Modifier.size(18.dp),
                        tint = if (isRefEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 如果参考图已开启，则展示详细的展示模式选择工具
                if (isRefEnabled) {
                    VerticalDivider(modifier = Modifier.height(16.dp))

                    // 分屏模式按钮 (SPLIT)
                    IconToggleButton(
                        checked = state.referenceMode == ReferenceDisplayMode.SPLIT,
                        onCheckedChange = { viewModel.updateReferenceMode(ReferenceDisplayMode.SPLIT) },
                        modifier = Modifier.size(28.dp).tip("分屏对比模式")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalSplit,
                            contentDescription = "分屏模式",
                            modifier = Modifier.size(18.dp),
                            tint = if (state.referenceMode == ReferenceDisplayMode.SPLIT) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }

                    // 叠加模式按钮 (OVERLAY)
                    IconToggleButton(
                        checked = state.referenceMode == ReferenceDisplayMode.OVERLAY,
                        onCheckedChange = { viewModel.updateReferenceMode(ReferenceDisplayMode.OVERLAY) },
                        modifier = Modifier.size(28.dp).tip("半透明叠加模式")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "叠加模式",
                            modifier = Modifier.size(18.dp),
                            tint = if (state.referenceMode == ReferenceDisplayMode.OVERLAY) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }

                    // 当处于叠加模式时，展示透明度调节滑块
                    if (state.referenceMode == ReferenceDisplayMode.OVERLAY) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Slider(
                            value = state.referenceOpacity,
                            onValueChange = { viewModel.updateReferenceOpacity(it) },
                            modifier = Modifier.width(100.dp).height(24.dp).tip("调节参考图透明度"),
                            // 透明度限制在 10% 到 100% 之间
                            valueRange = 0.1f..1f
                        )
                    }
                }
            }
        }
    }
}
