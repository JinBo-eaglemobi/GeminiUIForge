package org.gemini.ui.forge.ui.uistudio.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import kotlin.math.roundToInt

/**
 * 图集切片预审校对弹窗顶部操作栏
 *
 * @param imageName 图集原始文件名
 * @param totalCount 当前图元总数
 * @param selectedItem 当前选中的图元（用于删除和吸附可用性）
 * @param zoomScale 当前视口缩放比例
 * @param toolMode 当前工具模式（选择或画框）
 * @param isSnapping 是否正在执行智能吸附
 * @param onToolModeChange 切换工具模式回调
 * @param onResetZoom 视口居中与缩放重置回调
 * @param onSmartSnap 触发一键智能吸附校准回调
 * @param onDeleteSelected 删除当前选中图元回调
 * @param onResetAll 重置全部图元至初始状态回调
 * @param onClose 关闭/取消弹窗回调
 * @param onConfirm 确认切图并入库回调
 */
@Composable
fun AssetSliceVerifyTopBar(
    imageName: String,
    totalCount: Int,
    selectedItem: EditableSliceItem?,
    zoomScale: Float,
    toolMode: VerifyToolMode,
    isSnapping: Boolean,
    autoRemoveBg: Boolean = true,
    onToggleAutoRemoveBg: (Boolean) -> Unit = {},
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onToolModeChange: (VerifyToolMode) -> Unit,
    onResetZoom: () -> Unit,
    onSmartSnap: () -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onDeleteSelected: () -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：图集信息与统计胶囊
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "图元切片预审与校对",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 文件名药丸
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = imageName.ifBlank { "未命名图集" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 统计药丸
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "共 $totalCount 个图元",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 中间：核心工具栏（选择模式、画框模式、吸附、删除、重置、缩放）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                // 工具模式分段胶囊
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = toolMode == VerifyToolMode.SELECT,
                        onClick = { onToolModeChange(VerifyToolMode.SELECT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.tip("选择与 8 向拉伸变换模式 (快捷键: V)")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("选择", fontSize = 12.sp)
                        }
                    }
                    SegmentedButton(
                        selected = toolMode == VerifyToolMode.DRAW_RECT,
                        onClick = { onToolModeChange(VerifyToolMode.DRAW_RECT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.tip("手动画框新增图元模式 (快捷键: R)")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("补录", fontSize = 12.sp)
                        }
                    }
                }

                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

                // 一键智能吸附校准按钮
                FilledTonalButton(
                    onClick = onSmartSnap,
                    enabled = !isSnapping && totalCount > 0,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp).tip("基于原图像素 Alpha 通道与前景边缘自动微观吸附纠偏 (快捷键: Ctrl+Shift+A)")
                ) {
                    if (isSnapping) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("吸附中...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("智能吸附", fontSize = 12.sp)
                    }
                }

                // 删除选中按钮
                IconButton(
                    onClick = onDeleteSelected,
                    enabled = selectedItem != null,
                    modifier = Modifier.size(34.dp).tip("删除当前选中图元 (快捷键: Delete / Backspace)")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "删除图元",
                        tint = if (selectedItem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 撤销上一步
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(34.dp).tip("撤销上一步操作 (快捷键: Ctrl+Z)")
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "撤销",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 重做恢复下一步
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(34.dp).tip("恢复下一步操作 (快捷键: Ctrl+Y / Ctrl+Shift+Z)")
                ) {
                    Icon(
                        imageVector = Icons.Default.Redo,
                        contentDescription = "重做",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 重置选区
                IconButton(
                    onClick = onResetAll,
                    modifier = Modifier.size(34.dp).tip("重置全部图元及视口坐标至初始状态 (快捷键: Ctrl+R)")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "重置全部",
                        modifier = Modifier.size(18.dp)
                    )
                }

                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

                // 缩放百分比胶囊与居中按钮
                Surface(
                    onClick = onResetZoom,
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.tip("点击重置视口居中与 100% 原寸")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${(zoomScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 右侧：自动去背开关、取消与确认切图按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                // 自动去背胶囊式轻量切换器
                FilterChip(
                    selected = autoRemoveBg,
                    onClick = { onToggleAutoRemoveBg(!autoRemoveBg) },
                    label = {
                        Text(
                            text = if (autoRemoveBg) "🪄 自动去背" else "保留背景",
                            fontSize = 11.sp,
                            fontWeight = if (autoRemoveBg) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.height(30.dp).tip("切片时自动调用本地 Python rembg 抠图去背，输出带透明 Alpha 通道的 PNG 图片")
                )

                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp).tip("放弃校对并返回上一步")
                ) {
                    Text("取消", fontSize = 12.sp)
                }

                Button(
                    onClick = onConfirm,
                    enabled = totalCount > 0,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp).tip("确认当前所有图元坐标并进入物理切片入库")
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("确认切图并入库 ($totalCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
