package org.gemini.ui.forge.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.NativeClipboard
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * 带重试兜底的剪贴板包装实现。
 *
 * 背景：Windows 系统剪贴板为独占资源，第三方程序（剪贴板增强工具 / 输入法 /
 * 云同步 / 剪贴板历史面板等）短暂持锁（通常为毫秒级）时，桌面版 Compose 在
 * [Clipboard.getClipEntry] 内部读取系统剪贴板会抛出
 * `IllegalStateException: cannot open system clipboard`（框架内部未捕获），
 * 导致粘贴失败并在控制台刷出大段异常堆栈。
 *
 * 本类委托平台默认实现：读取剪贴板时捕获该异常并做短间隔重试，重试耗尽返回 null
 * （语义 = "剪贴板暂时不可读，视为空"），粘贴表现为静默失败——用户重按即可重试，
 * 不再产生异常与日志噪音。
 * 所有粘贴入口（键盘 Ctrl+V / 右键菜单 / 系统剪贴板历史等）最终都汇聚到同一个
 * 读取调用，因此本包装对所有入口统一生效。
 *
 * @param delegate 平台默认剪贴板实现，承担实际读写
 */
@OptIn(ExperimentalComposeUiApi::class)
class RetryingClipboard(
    private val delegate: Clipboard
) : Clipboard {

    override suspend fun getClipEntry(): ClipEntry? {
        repeat(READ_RETRY_COUNT) { attempt ->
            try {
                return delegate.getClipEntry()
            } catch (_: IllegalStateException) {
                // 最后一次仍失败则不再重试，返回 null 走静默失败路径
                if (attempt == READ_RETRY_COUNT - 1) return null
                // 等待瞬锁释放后重试（总耗时约 75ms，用户几乎无感）
                delay(READ_RETRY_INTERVAL_MS.milliseconds)
            }
        }
        // 理论上不可达（循环内要么返回要么提前退出），兜底以满足编译器
        return delegate.getClipEntry()
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        // 写入路径不受瞬锁异常影响，直接透传
        delegate.setClipEntry(clipEntry)
    }

    override val nativeClipboard: NativeClipboard
        get() = delegate.nativeClipboard

    private companion object {
        /** 剪贴板读取失败时的最大重试次数 */
        const val READ_RETRY_COUNT = 4

        /** 重试间隔（毫秒） */
        const val READ_RETRY_INTERVAL_MS = 25L
    }
}
