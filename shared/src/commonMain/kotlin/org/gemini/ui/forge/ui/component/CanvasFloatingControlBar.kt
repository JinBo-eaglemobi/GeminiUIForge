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
            // 视图显示模式切换 (画布舞台 vs JSON 源码)
            // ★ 图标纠正规范：非代码界面显示 <> (Icons.Default.Code)，进入代码界面后显示画布图标 (Icons.Default.Dashboard)
            val isCodeMode = state.workspaceViewMode == org.gemini.ui.forge.state.WorkspaceViewMode.JSON_CODE
            IconToggleButton(
                checked = isCodeMode,
                onCheckedChange = {
                    val target = if (it) org.gemini.ui.forge.state.WorkspaceViewMode.JSON_CODE else org.gemini.ui.forge.state.WorkspaceViewMode.CANVAS
                    viewModel.setWorkspaceViewMode(target)
                },
                modifier = Modifier.size(28.dp).tip(if (isCodeMode) "当前：JSON 源码视图 (点击切回画布舞台)" else "切换至原生 Pretty JSON 源码视图 (支持与图层树联动高亮)")
            ) {
                Icon(
                    imageVector = if (isCodeMode) Icons.Default.Dashboard else Icons.Default.Code,
                    contentDescription = "视图切换",
                    modifier = Modifier.size(18.dp),
                    tint = if (isCodeMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }

            VerticalDivider(modifier = Modifier.height(16.dp))

            // ==========================================
            // 1. 缩放控制区 (Zoom Controls，进入代码界面后禁用)
            // ==========================================

            // 缩小按钮 (-20%)
            IconButton(
                onClick = { updateZoom(zoom - 0.2f, centerOffset) },
                enabled = !isCodeMode,
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
                    textAlign = TextAlign.Center,
                    color = if (!isCodeMode) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // 放大按钮 (+20%)
            IconButton(
                onClick = { updateZoom(zoom + 0.2f, centerOffset) },
                enabled = !isCodeMode,
                modifier = Modifier.size(28.dp).tip("放大视图")
            ) {
                Icon(Icons.Default.Add, "放大", modifier = Modifier.size(16.dp))
            }

            VerticalDivider(modifier = Modifier.height(16.dp))

            // ==========================================
            // 2. 视角复位区 (Reset View，进入代码界面后禁用)
            // ==========================================
            IconButton(
                onClick = onResetZoom,
                enabled = !isCodeMode,
                modifier = Modifier.size(28.dp).tip("重置缩放并居中")
            ) {
                Icon(Icons.Default.Refresh, "复位画布", modifier = Modifier.size(18.dp))
            }

            VerticalDivider(modifier = Modifier.height(16.dp))

            // ==========================================
            // 3. 骨架网格与辅助线开关 (Wireframe Grid Toggle，进入代码界面后禁用)
            // 默认开启（高亮 GridOn），点击后一键隐藏骨架色块与边框线，进入 100% 纯净预览
            // ==========================================
            val isWireframeOn = !(state.isVisualMode && state.isHideOutlines)
            IconToggleButton(
                checked = isWireframeOn,
                onCheckedChange = { viewModel.toggleWireframe() },
                enabled = !isCodeMode,
                modifier = Modifier.size(28.dp).tip(
                    if (isWireframeOn) "骨架网格已开启，点击进入纯净预览模式"
                    else "当前为纯净预览，点击显示骨架网格与边框"
                )
            ) {
                Icon(
                    imageVector = if (isWireframeOn) Icons.Default.GridOn else Icons.Default.GridOff,
                    contentDescription = "骨架网格与辅助线",
                    modifier = Modifier.size(18.dp),
                    tint = if (!isCodeMode && isWireframeOn) MaterialTheme.colorScheme.primary 
                           else if (!isCodeMode) LocalContentColor.current
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // ==========================================
            // 4. 固定常驻功能：参考图切片预览隐藏/显示开关 (Hide/Show Reference Slices)
            // 控制所有尚未绑定独立资产的模块从参考底图裁剪显示的临时切片
            // ==========================================
            val isSliceVisible = !state.isHideReferenceSlices
            IconToggleButton(
                checked = isSliceVisible,
                onCheckedChange = { viewModel.toggleHideReferenceSlices() },
                enabled = !isCodeMode,
                modifier = Modifier.size(28.dp).tip(
                    if (isSliceVisible) "原图切片预览已显示 (点击隐藏全部临时切片)"
                    else "原图切片预览已隐藏 (点击显示未绑定模块的原图切片)"
                )
            ) {
                Icon(
                    imageVector = if (isSliceVisible) Icons.Default.ContentCut else Icons.Default.HideImage,
                    contentDescription = "参考图切割切片开关",
                    modifier = Modifier.size(18.dp),
                    tint = if (!isCodeMode && isSliceVisible) MaterialTheme.colorScheme.primary
                           else if (!isCodeMode) LocalContentColor.current
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // ==========================================
            // 5. 参考图控制区 (Reference Image Controls)
            // 仅当存在参考图 (referenceUri != null) 时渲染此区域，且进入代码界面后禁用
            // ==========================================
            if (referenceUri != null) {
                VerticalDivider(modifier = Modifier.height(16.dp))

                // 参考图全局开关：判断当前是否是非隐藏状态
                // ★ 区分优化：使用 Map / ImageSearch 图标替代泛滥的眼睛图标，避免混淆
                val isRefEnabled = state.referenceMode != ReferenceDisplayMode.HIDDEN
                val refTipText = if (isRefEnabled) {
                    "关闭参考底图对比"
                } else {
                    val modeName = if (state.lastActiveReferenceMode == ReferenceDisplayMode.SPLIT) "分屏对照" else "半透明叠加"
                    "开启参考底图对比 ($modeName)"
                }
                IconToggleButton(
                    checked = isRefEnabled,
                    onCheckedChange = { viewModel.toggleReferenceMode(it) },
                    enabled = !isCodeMode,
                    modifier = Modifier.size(28.dp).tip(refTipText)
                ) {
                    Icon(
                        imageVector = if (isRefEnabled) Icons.Default.Map else Icons.Default.ImageSearch,
                        contentDescription = "切换参考底图对比",
                        modifier = Modifier.size(18.dp),
                        tint = if (!isCodeMode && isRefEnabled) MaterialTheme.colorScheme.primary 
                               else if (!isCodeMode) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // 如果参考图已开启且处于非代码模式，展示带有明显视觉区隔的二级悬浮工具胶囊岛
                if (isRefEnabled && !isCodeMode) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 分屏模式按钮 (SPLIT)
                            IconToggleButton(
                                checked = state.referenceMode == ReferenceDisplayMode.SPLIT,
                                onCheckedChange = { viewModel.updateReferenceMode(ReferenceDisplayMode.SPLIT) },
                                modifier = Modifier.size(24.dp).tip("分屏对比模式")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerticalSplit,
                                    contentDescription = "分屏模式",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (state.referenceMode == ReferenceDisplayMode.SPLIT) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }

                            // 叠加模式按钮 (OVERLAY)
                            IconToggleButton(
                                checked = state.referenceMode == ReferenceDisplayMode.OVERLAY,
                                onCheckedChange = { viewModel.updateReferenceMode(ReferenceDisplayMode.OVERLAY) },
                                modifier = Modifier.size(24.dp).tip("半透明叠加模式")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = "叠加模式",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (state.referenceMode == ReferenceDisplayMode.OVERLAY) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }

                            // 当处于叠加模式时，展示透明度调节滑块
                            if (state.referenceMode == ReferenceDisplayMode.OVERLAY) {
                                VerticalDivider(modifier = Modifier.height(12.dp))
                                Slider(
                                    value = state.referenceOpacity,
                                    onValueChange = { viewModel.updateReferenceOpacity(it) },
                                    modifier = Modifier.width(90.dp).height(20.dp).tip("调节参考图叠加透明度"),
                                    valueRange = 0.1f..1f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
