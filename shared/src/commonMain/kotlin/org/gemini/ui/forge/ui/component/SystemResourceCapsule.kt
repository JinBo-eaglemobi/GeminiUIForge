package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
 * 底部状态栏系统与进程性能监控微胶囊组件
 * 常驻显示实时 CPU/RAM 读数，光标悬停 (Hover) 向上紧凑贴合弹出 Bento 性能面板
 */
@Composable
fun SystemResourceCapsule(
    modifier: Modifier = Modifier
) {
    val metricsSnapshot by SystemPerformanceMonitor.metrics.collectAsState()
    var isHovered by remember { mutableStateOf(false) }

    val snapshot = metricsSnapshot ?: return
    val density = LocalDensity.current
    val gapPx = remember(density) { with(density) { 8.dp.roundToPx() } }

    var capsuleWidthPx by remember { mutableStateOf(0) }
    var popupWidthPx by remember { mutableStateOf(0) }
    var popupHeightPx by remember { mutableStateOf(0) }

    val indicatorColor = when (snapshot.severityLevel) {
        2 -> Color(0xFFEF5350) // 告警红
        1 -> Color(0xFFFFA726) // 预警黄
        else -> Color(0xFF66BB6A) // 健康绿
    }

    // 格式化读数
    val heapUsedStr = remember(snapshot.processMemory.heapUsedBytes) {
        formatBytes(snapshot.processMemory.heapUsedBytes)
    }
    val sysMemPercentInt = remember(snapshot.systemMemory.usagePercent) {
        snapshot.systemMemory.usagePercent.roundToInt()
    }
    val procCpuOneDecimal = remember(snapshot.cpu.processCpuPercent) {
        ((snapshot.cpu.processCpuPercent * 10).roundToInt() / 10.0).toString()
    }
    val sysCpuInt = remember(snapshot.cpu.systemCpuPercent) {
        snapshot.cpu.systemCpuPercent.roundToInt()
    }

    Box(
        modifier = modifier
            .onGloballyPositioned {
                capsuleWidthPx = it.size.width
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
    ) {
        // 常驻胶囊外壳
        Surface(
            shape = AppShapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                width = 1.dp,
                color = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .clip(AppShapes.small)
                .clickable { isHovered = !isHovered }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                // 负载呼吸指示灯
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(indicatorColor, shape = CircleShape)
                )
                Spacer(Modifier.width(6.dp))

                // 内存读数 (程序 + 宿主)
                Text(
                    text = "RAM: $heapUsedStr (宿主 $sysMemPercentInt%)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(6.dp))
                Text(
                    text = "|",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    fontSize = 11.sp
                )
                Spacer(Modifier.width(6.dp))

                // CPU 读数 (程序 + 宿主)
                Text(
                    text = "CPU: $procCpuOneDecimal% (宿主 $sysCpuInt%)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 向上紧凑弹出的 Bento 悬浮性能监视面板
        if (isHovered) {
            val effectivePopupHeight = if (popupHeightPx > 0) popupHeightPx else with(density) { 250.dp.roundToPx() }
            val effectivePopupWidth = if (popupWidthPx > 0) popupWidthPx else with(density) { 380.dp.roundToPx() }

            // 动态紧密贴合算法：卡片底边精确对准胶囊顶边上方 8.dp，水平相对居中对齐
            val targetY = -(effectivePopupHeight + gapPx)
            val targetX = if (capsuleWidthPx > 0) {
                ((capsuleWidthPx - effectivePopupWidth) / 2)
            } else {
                -with(density) { 40.dp.roundToPx() }
            }

            Popup(
                offset = IntOffset(targetX, targetY),
                onDismissRequest = { isHovered = false },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = true
                )
            ) {
                SystemResourceBentoPopup(
                    snapshot = snapshot,
                    onSizeMeasured = { w, h ->
                        popupWidthPx = w
                        popupHeightPx = h
                    },
                    onDismiss = { isHovered = false }
                )
            }
        }
    }
}

/**
 * 悬浮展开的系统与进程性能 Bento 全景卡片
 */
@Composable
private fun SystemResourceBentoPopup(
    snapshot: SystemMetricsSnapshot,
    onSizeMeasured: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val gcSuccessMsg = stringResource(Res.string.sys_res_gc_triggered)

    Surface(
        modifier = Modifier
            .width(380.dp)
            .padding(spacing.small)
            .onGloballyPositioned {
                onSizeMeasured(it.size.width, it.size.height)
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
