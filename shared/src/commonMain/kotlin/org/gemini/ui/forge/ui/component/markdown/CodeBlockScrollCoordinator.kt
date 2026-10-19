package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.Volatile
import kotlin.math.abs

/**
 * 代码块与外层聊天列表的吸顶联动协调器。
 *
 * 核心机制（视口级物理悬浮吸顶 Sticky Header）：
 * 1. 活跃代码块向协调器登记自身的滚动状态、折叠状态、语言标签及 Y 坐标；
 * 2. 状态驱动外层视口最顶端的悬浮折叠头：当代码块顶部滑入视口上方且未离开视口时，
 *    视口最顶部的折叠头立即吸附呈现，保证折叠头 100% 永远可见；
 * 3. 严格手势分流：未吸顶全滚页面，吸顶后滚代码内部，见底后解锁推走卡片。
 */
class CodeBlockScrollCoordinator {
    /** 当前活跃代码块的滚动状态 (物理引用) */
    @Volatile
    var activeCodeScrollState: ScrollState? = null

    /** 代码框在窗口中的顶部物理 Y 坐标 */
    @Volatile
    var activeBlockTopInWindow: Float = Float.MAX_VALUE

    /** 代码框在窗口中的底部物理 Y 坐标 */
    @Volatile
    var activeBlockBottomInWindow: Float = Float.MAX_VALUE

    /** 外层视口顶部基准线在窗口中的物理 Y 坐标 */
    @Volatile
    var viewportTopInWindow: Float = 0f

    /** 外层视口底部基准线在窗口中的物理 Y 坐标 */
    @Volatile
    var viewportBottomInWindow: Float = Float.MAX_VALUE

    /** 响应式吸顶折叠头元数据 */
    var activeLanguage by mutableStateOf<String?>(null)
    var activeLineCount by mutableStateOf(0)
    var isCodeBlockCollapsed by mutableStateOf(false)
    var isFloatingHeaderVisible by mutableStateOf(false)
    var onToggleActiveCollapse by mutableStateOf<(() -> Unit)?>(null)
    var onCopyActiveCode by mutableStateOf<(() -> Unit)?>(null)

    /** 刷新悬浮吸顶折叠头的可见性 */
    fun updateStickyState() {
        if (isCodeBlockCollapsed || activeBlockTopInWindow == Float.MAX_VALUE || viewportTopInWindow == 0f) {
            isFloatingHeaderVisible = false
            return
        }
        // 当代码框的顶部已经滑至或越过视口顶部，且代码框底边尚未完全离开视口时，吸顶折叠头必须常驻可见！
        isFloatingHeaderVisible = activeBlockTopInWindow <= (viewportTopInWindow + 10f) &&
                activeBlockBottomInWindow > (viewportTopInWindow + 50f)
    }

    /** 是否处于代码内滚激活区 */
    val isReadyToScrollCode: Boolean
        get() {
            if (activeBlockTopInWindow == Float.MAX_VALUE || viewportTopInWindow == 0f) return false
            return activeBlockTopInWindow <= (viewportTopInWindow + 10f)
        }

    /**
     * 判断是否应该将滚轮/滑动量拦截并引导至代码块内部消费。
     *
     * 基于三阶段纯物理状态机：
     * 1. 未吸顶（未到位）：100% 外层滚动；
     * 2. 已到底（按钮已翻出露出）：向上反向回滚 100% 优先滚外层退走按钮；
     * 3. 内部滚动区间：100% 内层消费，零锁分发。
     */
    fun shouldTrapScroll(deltaY: Float, outerScrollValue: Int): Boolean {
        val state = activeCodeScrollState
        if (state == null) {
            println("[SCROLL_PROBE] shouldTrap=false (reason: activeCodeScrollState is null)")
            return false
        }
        if (!isReadyToScrollCode) {
            println("[SCROLL_PROBE] shouldTrap=false (reason: !isReadyToScrollCode, top=$activeBlockTopInWindow, vpTop=$viewportTopInWindow)")
            return false
        }

        // 向上滚轮 (deltaY < 0，向下翻看后续代码)：代码内部还有剩余行没滚完
        if (deltaY < 0f) {
            val hasMore = state.value < state.maxValue
            org.gemini.ui.forge.utils.AppLogger.i("SCROLL_PROBE", "DOWN deltaY=$deltaY | codeVal=${state.value}/${state.maxValue} | trap=$hasMore")
            return hasMore
        }

        // 向下滚轮 (deltaY > 0，向上回看上方代码)：
        if (deltaY > 0f) {
            val outerStickOffset = targetOuterStickOffset
            if (outerStickOffset >= 0 && outerScrollValue > outerStickOffset) {
                org.gemini.ui.forge.utils.AppLogger.i("SCROLL_PROBE", "UP deltaY=$deltaY | outerScroll=$outerScrollValue > stick=$outerStickOffset | trap=false (Priority: Outer Roll Button)")
                return false
            }
            val canScrollUp = state.value > 0
            org.gemini.ui.forge.utils.AppLogger.i("SCROLL_PROBE", "UP deltaY=$deltaY | codeVal=${state.value}/${state.maxValue} | stick=$outerStickOffset | trap=$canScrollUp")
            return canScrollUp
        }

        return false
    }

    /** 记录代码框刚进入完全吸顶时的外层绝对滚动偏移 (整数) */
    @Volatile
    var targetOuterStickOffset: Int = -1

    /** 外层滚轮/拖拽量转移消费给代码内部 (纯无锁直接派发，杜绝协程互斥锁丢帧) */
    fun consumeScroll(deltaY: Float): Float {
        val state = activeCodeScrollState ?: return 0f
        return if (deltaY < 0f) {
            val remaining = (state.maxValue - state.value).toFloat()
            val toConsume = minOf(abs(deltaY), remaining)
            state.dispatchRawDelta(toConsume)
            org.gemini.ui.forge.utils.AppLogger.i("SCROLL_PROBE", "CONSUME DOWN deltaY=$deltaY -> consumed=$toConsume, newVal=${state.value}/${state.maxValue}")
            -toConsume
        } else {
            val remaining = state.value.toFloat()
            val toConsume = minOf(deltaY, remaining)
            state.dispatchRawDelta(-toConsume)
            org.gemini.ui.forge.utils.AppLogger.i("SCROLL_PROBE", "CONSUME UP deltaY=$deltaY -> consumed=$toConsume, newVal=${state.value}/${state.maxValue}")
            toConsume
        }
    }

    /**
     * 高速惯性 Fling 冲量消费：
     * 彻底废除带有协程锁的 animateScrollTo，全部使用无锁直接派发 dispatchRawDelta，
     * 无论用户滑动多快速、多猛烈，0 延迟响应，绝对不死锁！
     */
    fun consumeFlingNonBlocking(availableY: Float, outerScrollValue: Int): Float {
        val state = activeCodeScrollState ?: return 0f
        if (!isReadyToScrollCode) return 0f

        if (availableY < 0f && state.value < state.maxValue) {
            val remainingPx = (state.maxValue - state.value).toFloat()
            val toConsume = minOf(abs(availableY) * 0.1f, remainingPx)
            state.dispatchRawDelta(toConsume)
            println("[SCROLL_PROBE] FLING DOWN avail=$availableY -> consumed=$toConsume, newVal=${state.value}")
            return -toConsume
        }

        if (availableY > 0f && state.value > 0) {
            val outerStickOffset = targetOuterStickOffset
            if (outerStickOffset >= 0 && outerScrollValue > outerStickOffset) {
                println("[SCROLL_PROBE] FLING UP outerScroll=$outerScrollValue > stick=$outerStickOffset -> consumed=0")
                return 0f
            }
            val remainingPx = state.value.toFloat()
            val toConsume = minOf(availableY * 0.1f, remainingPx)
            state.dispatchRawDelta(-toConsume)
            println("[SCROLL_PROBE] FLING UP avail=$availableY -> consumed=$toConsume, newVal=${state.value}")
            return toConsume
        }

        return 0f
    }

    /** 安全释放指定的状态 */
    fun safeRelease(state: ScrollState) {
        if (activeCodeScrollState == state) {
            activeCodeScrollState = null
            activeBlockTopInWindow = Float.MAX_VALUE
            activeBlockBottomInWindow = Float.MAX_VALUE
            isFloatingHeaderVisible = false
            onToggleActiveCollapse = null
            onCopyActiveCode = null
        }
    }
}

val LocalCodeScrollCoordinator = compositionLocalOf { CodeBlockScrollCoordinator() }
