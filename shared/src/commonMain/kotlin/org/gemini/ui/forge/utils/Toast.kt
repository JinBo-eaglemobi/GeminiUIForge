package org.gemini.ui.forge.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.ui.component.ToastData
import org.gemini.ui.forge.ui.component.ToastType

/**
 * 全局 Toast 轻量级通知控制器（单例）。
 *
 * 采用 [MutableStateFlow] 作为唯一数据源驱动全局通知展示：业务层在任意位置调用 [show] 即可发射
 * 一条通知数据，由界面根节点挂载的 Toast 宿主组件订阅 [toastData] 并负责浮层的渲染、自动消失
 * 与动画过渡，从而实现"一处发射、全局响应"的解耦通知机制。
 *
 * 全项目规范（红线）：任何需要轻量级提示的场景一律复用本单例，严禁在业务代码中重复手搓
 * 独立浮层或 Snackbar。
 *
 * 标准调用范式：
 * ```kotlin
 * // 常规成功提示（默认 3 秒自动消失）
 * Toast.show("已保存至外部缓存", ToastType.SUCCESS)
 *
 * // 带可点击动作的警告提示
 * Toast.show(
 *     message = "检测到未应用的更改",
 *     type = ToastType.WARNING,
 *     durationMillis = 5000L,
 *     actionLabel = "撤销",
 *     onAction = { viewModel.undo() }
 * )
 * ```
 *
 * @see ToastData 单条通知的数据实体
 * @see ToastType 通知的语义类型（成功 / 信息 / 错误等，决定视觉样式）
 */
object Toast {
    /** 内部可写的通知状态流：仅由本单例内部写入，持有当前待展示的通知数据，null 表示当前无通知 */
    private val _toastData = MutableStateFlow<ToastData?>(null)

    /** 对外只读的通知订阅流：Toast 宿主组件收集此流以渲染/隐藏全局通知浮层 */
    val toastData: StateFlow<ToastData?> = _toastData.asStateFlow()

    /**
     * 显示一条全局 Toast 通知。
     *
     * 每次调用都会以新数据覆盖 [toastData] 的当前值：若上一条通知尚未消失，将被新通知立即替换。
     * 通知到达 [durationMillis] 时长后由宿主组件自动隐藏（内部通常会回调 [hide]）。
     *
     * @param message 要展示的通知文案（业务语义文本，由调用方传入）。
     * @param type 通知的语义类型，决定图标与配色样式，默认为 [ToastType.INFO]。
     * @param durationMillis 通知的自动消失时长（毫秒），默认 3000ms。
     * @param actionLabel 可选的动作按钮文案（如"撤销"、"重试"）；为 null 时不展示动作按钮。
     * @param onAction 用户点击动作按钮时触发的回调；仅在 [actionLabel] 非空时有意义。
     */
    fun show(
        message: String,
        type: ToastType = ToastType.INFO,
        durationMillis: Long = 3000L,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        _toastData.value = ToastData(message, type, durationMillis, actionLabel, onAction)
    }

    /**
     * 立即清除/隐藏当前 Toast 通知。
     *
     * 通过将状态流置空实现，宿主组件观察到 null 后播放消失动画并移除浮层；
     * 若当前本无通知，调用无任何副作用。
     */
    fun hide() {
        _toastData.value = null
    }
}
