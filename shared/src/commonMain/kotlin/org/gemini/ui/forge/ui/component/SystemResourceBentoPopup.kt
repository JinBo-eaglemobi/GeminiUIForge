package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.SystemMetricsSnapshot
import org.gemini.ui.forge.model.formatBytes
import org.gemini.ui.forge.service.SystemPerformanceMonitor
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * 悬浮展开的系统与进程性能 Bento 全景卡片 (独立 Composable)
 *
 * 遵循一文件一 Composable 铁律与设计系统 Tokens 规范。
 * 挂载自身 PointerInput 监听鼠标移入与移出，与胶囊主体协同实现优雅防抖保活。
 *
 * @param snapshot 实时系统与进程指标快照
 * @param onHoverStateChanged 悬停状态改变回调 (true=鼠标进入弹窗, false=鼠标离开弹窗)
 * @param onSizeMeasured 弹窗尺寸测量回调
 * @param onDismiss 显式关闭回调
 * @param modifier 修饰符
 */
@Composable
fun SystemResourceBentoPopup(
    snapshot: SystemMetricsSnapshot,
    onHoverStateChanged: (Boolean) -> Unit,
    onSizeMeasured: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    minAnchorWidthDp: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val gcSuccessMsg = stringResource(Res.string.sys_res_gc_triggered)
    // 弹窗外框内边距 padding(spacing.small: 8dp)，使得整体占据宽度为 minAnchorWidthDp
    val effectiveWidth = maxOf(380.dp, minAnchorWidthDp)

    Surface(
        modifier = modifier
            .width(effectiveWidth)
            .onGloballyPositioned {
                onSizeMeasured(it.size.width, it.size.height)
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> onHoverStateChanged(true)
                            PointerEventType.Exit -> onHoverStateChanged(false)
                        }
                    }
                }
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 10.dp,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 标题栏 + GC 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.sys_res_monitor_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 实时采样胶囊
                    Surface(
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ) {
                        Text(
                            text = stringResource(Res.string.sys_res_live_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // 释放内存 (GC) 按钮
                    OutlinedButton(
                        onClick = {
                            SystemPerformanceMonitor.triggerGc()
                            Toast.show(gcSuccessMsg, ToastType.SUCCESS)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp).tip("手动触发 JVM 垃圾回收，即刻回收闲置内存")
                    ) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.sys_res_action_gc), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // 2. 区块 1：当前应用程序 (Gemini UI Forge 进程)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.sys_res_process_section),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 堆内存进度条
                val heapUsed = formatBytes(snapshot.processMemory.heapUsedBytes)
                val heapCommitted = formatBytes(snapshot.processMemory.heapCommittedBytes)
                val heapMax = formatBytes(snapshot.processMemory.heapMaxBytes)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "堆内存: $heapUsed / $heapCommitted",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "上限 $heapMax",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                // 双色进度条 (深色已用 + 浅色分配)
                val usedRatio = (snapshot.processMemory.heapUsedBytes.toFloat() / snapshot.processMemory.heapMaxBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { usedRatio },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = when {
                        usedRatio > 0.85f -> Color(0xFFEF5350)
                        usedRatio > 0.70f -> Color(0xFFFFA726)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // 统计指标行 (CPU / 线程 / GC)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "进程 CPU: ${((snapshot.cpu.processCpuPercent * 10).roundToInt() / 10.0)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "线程: ${snapshot.activeThreads} 个",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "GC: ${snapshot.gcCount}次 (${snapshot.gcTimeMs}ms)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // 3. 区块 2：电脑宿主系统 (Host OS)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.sys_res_host_section),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                // 物理内存
                val usedPhysical = formatBytes(snapshot.systemMemory.usedPhysicalBytes)
                val totalPhysical = formatBytes(snapshot.systemMemory.totalPhysicalBytes)
                val sysMemRatio = (snapshot.systemMemory.usagePercent / 100f).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "物理内存: $usedPhysical / $totalPhysical",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${snapshot.systemMemory.usagePercent.roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                LinearProgressIndicator(
                    progress = { sysMemRatio },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = when {
                        sysMemRatio > 0.85f -> Color(0xFFEF5350)
                        sysMemRatio > 0.70f -> Color(0xFFFFA726)
                        else -> Color(0xFF00ACC1)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(Modifier.height(2.dp))

                // 整机总 CPU
                val sysCpuRatio = (snapshot.cpu.systemCpuPercent / 100f).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "整机 CPU (${snapshot.cpu.availableProcessors} 核心)",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${snapshot.cpu.systemCpuPercent.roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                LinearProgressIndicator(
                    progress = { sysCpuRatio },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = when {
                        sysCpuRatio > 0.85f -> Color(0xFFEF5350)
                        sysCpuRatio > 0.70f -> Color(0xFFFFA726)
                        else -> Color(0xFF7E57C2)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
