package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.gemini.ui.forge.model.formatBytes
import org.gemini.ui.forge.service.SystemPerformanceMonitor
import org.gemini.ui.forge.ui.theme.AppShapes
import kotlin.math.roundToInt

/**
 * 底部状态栏系统性能监控常驻胶囊组件
 *
 * 核心人机工程学改进：
 * 1. 【双区协同保活状态机】：胶囊与上浮 Bento 卡片双向协同，解决鼠标从胶囊移向弹窗瞬间弹窗闪退的严重缺陷；
 * 2. 【250ms 优雅防抖缓冲】：离开判定加入 250ms 防抖延迟，移入新区域瞬间取消关闭任务，鼠标无论快慢均如丝般顺滑；
 * 3. 【点击图钉锁定模式】：点击胶囊支持将弹窗常驻钉在界面上，方便仔细阅读指标或多次触发 GC；
 * 4. 【一文件一 Composable 合规】：Bento 弹窗已解耦抽离至独立文件 [SystemResourceBentoPopup.kt]。
 */
@Composable
fun SystemResourceCapsule(
    modifier: Modifier = Modifier
) {
    val snapshotRaw by SystemPerformanceMonitor.metrics.collectAsState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    if (snapshotRaw == null) return
    val snapshot = snapshotRaw!!

    // 双区协同悬停状态机
    var isCapsuleHovered by remember { mutableStateOf(false) }
    var isPopupHovered by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }
    var isPopupVisible by remember { mutableStateOf(false) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleDismissCheck() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(250) // 250ms 黄金人机反应防抖缓冲
            if (!isCapsuleHovered && !isPopupHovered && !isPinned) {
                isPopupVisible = false
            }
        }
    }

    fun onCapsuleHoverChanged(hovered: Boolean) {
        isCapsuleHovered = hovered
        if (hovered) {
            dismissJob?.cancel()
            isPopupVisible = true
        } else {
            scheduleDismissCheck()
        }
    }

    fun onPopupHoverChanged(hovered: Boolean) {
        isPopupHovered = hovered
        if (hovered) {
            dismissJob?.cancel()
            isPopupVisible = true
        } else {
            scheduleDismissCheck()
        }
    }

    // 弹窗与胶囊的物理尺寸与安全间隙
    var capsuleWidthPx by remember { mutableStateOf(0) }
    var popupWidthPx by remember { mutableStateOf(0) }
    var popupHeightPx by remember { mutableStateOf(0) }
    val gapPx = with(density) { 8.dp.roundToPx() }

    // 格式化读数
    val heapUsedStr = remember(snapshot.processMemory.heapUsedBytes) {
        formatBytes(snapshot.processMemory.heapUsedBytes)
    }
    val sysMemPercentInt = remember(snapshot.systemMemory.usagePercent) {
        snapshot.systemMemory.usagePercent.roundToInt()
    }
    val indicatorColor = remember(snapshot.systemMemory.usagePercent, snapshot.cpu.systemCpuPercent) {
        when {
            snapshot.systemMemory.usagePercent > 85 || snapshot.cpu.systemCpuPercent > 85 -> Color(0xFFEF5350)
            snapshot.systemMemory.usagePercent > 70 || snapshot.cpu.systemCpuPercent > 70 -> Color(0xFFFFA726)
            else -> Color(0xFF66BB6A)
        }
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
                            PointerEventType.Enter -> onCapsuleHoverChanged(true)
                            PointerEventType.Exit -> onCapsuleHoverChanged(false)
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
                color = if (isPopupVisible || isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .clip(AppShapes.small)
                .clickable {
                    isPinned = !isPinned
                    if (isPinned) {
                        dismissJob?.cancel()
                        isPopupVisible = true
                    } else {
                        isPopupVisible = false
                    }
                }
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
        if (isPopupVisible) {
            val effectivePopupHeight = if (popupHeightPx > 0) popupHeightPx else with(density) { 250.dp.roundToPx() }
            val capsuleWidthDp = with(density) { capsuleWidthPx.toDp() }
            val effectiveWidthDp = maxOf(380.dp, capsuleWidthDp)
            val effectivePopupWidthPx = with(density) { effectiveWidthDp.roundToPx() }

            // 动态紧密贴合算法：卡片底边精确对准胶囊顶边上方 8.dp，水平居中完全对齐胶囊
            val targetY = -(effectivePopupHeight + gapPx)
            val targetX = ((capsuleWidthPx - effectivePopupWidthPx) / 2)

            Popup(
                offset = IntOffset(targetX, targetY),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = false
                )
            ) {
                SystemResourceBentoPopup(
                    snapshot = snapshot,
                    minAnchorWidthDp = capsuleWidthDp,
                    onHoverStateChanged = { hovered ->
                        onPopupHoverChanged(hovered)
                    },
                    onSizeMeasured = { w, h ->
                        popupWidthPx = w
                        popupHeightPx = h
                    },
                    onDismiss = {
                        isPinned = false
                        isPopupVisible = false
                    }
                )
            }
        }
    }
}
