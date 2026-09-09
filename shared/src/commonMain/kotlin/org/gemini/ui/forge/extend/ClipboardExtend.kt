package org.gemini.ui.forge.extend

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.Toast

/**
 * 字符串转当前平台的 ClipEntry 容器
 */
expect fun String.toClipEntry(): ClipEntry

/**
 * 异步复制字符串至指定 Clipboard 对象
 */
suspend fun String.toClipboard(clipboard: Clipboard) {
    clipboard.setClipEntry(this.toClipEntry())
}

/**
 * 剪贴板动作触发器句柄，支持通过函数调用或 invoke 触发复制。
 */
class ClipboardAction(
    private val copyImpl: (text: String, toastMessage: String?, onResult: ((Boolean) -> Unit)?) -> Unit
) {
    operator fun invoke(
        text: String,
        toastMessage: String? = null,
        onResult: ((Boolean) -> Unit)? = null
    ) = copyImpl(text, toastMessage, onResult)
}

/**
 * 获取可复用的剪贴板操作句柄。
 *
 * 统一封装了 LocalClipboard 获取、协程异步调度、toClipEntry 跨端打包、
 * 系统瞬锁异常防御、成功 Toast 提示与结果回调。
 *
 * @return [ClipboardAction] 动作句柄，支持传入目标文本、轻量提示以及结果回调
 */
@Composable
fun rememberClipboardAction(): ClipboardAction {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        ClipboardAction { text, toastMessage, onResult ->
            scope.launch {
                try {
                    clipboard.setClipEntry(text.toClipEntry())
                    if (!toastMessage.isNullOrBlank()) {
                        Toast.show(toastMessage, ToastType.SUCCESS)
                    }
                    onResult?.invoke(true)
                } catch (e: Throwable) {
                    AppLogger.e("Clipboard", "剪贴板写入失败: ${e.message}", e)
                    onResult?.invoke(false)
                }
            }
        }
    }
}

/**
 * 链式修饰符：点击当前组件直接复制文本并弹出提示与触发结果回调。
 */
@Composable
fun Modifier.copyOnClick(
    text: String,
    toastMessage: String? = null,
    onResult: ((Boolean) -> Unit)? = null
): Modifier {
    val action = rememberClipboardAction()
    return this.clickable { action(text, toastMessage, onResult) }
}
