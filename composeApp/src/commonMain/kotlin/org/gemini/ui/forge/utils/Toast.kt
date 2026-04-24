package org.gemini.ui.forge.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.ui.component.ToastData
import org.gemini.ui.forge.ui.component.ToastType

/**
 * 全局 Toast 控制器
 */
object Toast {
    private val _toastData = MutableStateFlow<ToastData?>(null)
    val toastData: StateFlow<ToastData?> = _toastData.asStateFlow()

    /**
     * 显示全局 Toast
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
     * 清除/隐藏当前 Toast
     */
    fun hide() {
        _toastData.value = null
    }
}
