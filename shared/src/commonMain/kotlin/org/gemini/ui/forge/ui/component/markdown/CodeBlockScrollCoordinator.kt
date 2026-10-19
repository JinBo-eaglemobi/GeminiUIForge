package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.compositionLocalOf
import kotlin.concurrent.Volatile

/**
 * 代码块滚动轻量感知。
 *
 * 【深度对标 OpenCode 桌面端标准】：
 * 废除脆弱的外层像素强锁与全局手势劫持，全面拥抱局部自治与标准 Compose 嵌套手势接力！
 */
class CodeBlockScrollCoordinator {
    /** 当前活跃代码块的滚动状态引用 (轻量感知) */
    @Volatile
    var activeCodeScrollState: ScrollState? = null

    /** 安全释放指定的状态 */
    fun safeRelease(state: ScrollState) {
        if (activeCodeScrollState == state) {
            activeCodeScrollState = null
        }
    }
}

/** 提供统一的协调器实例 */
val LocalCodeScrollCoordinator = compositionLocalOf { CodeBlockScrollCoordinator() }
