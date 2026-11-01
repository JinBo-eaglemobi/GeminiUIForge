package org.gemini.ui.forge.utils

import kotlinx.coroutines.runBlocking

/**
 * 跨平台阻塞式等待工具 (iOS 实现)。
 * 委托 kotlinx.coroutines.runBlocking 阻塞当前线程直至挂起块完成并返回结果。
 */
actual fun <T> blockingAwait(block: suspend () -> T): T = runBlocking { block() }
