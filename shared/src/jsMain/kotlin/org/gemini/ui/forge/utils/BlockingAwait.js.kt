package org.gemini.ui.forge.utils

/**
 * 跨平台阻塞式等待工具 (JS 实现)。
 * JS 平台为单线程事件循环模型，无法阻塞当前线程同步等待协程结果；
 * 该平台的阻塞等待调用属不支持操作，直接抛出异常以暴露调用点。
 */
actual fun <T> blockingAwait(block: suspend () -> T): T =
    throw UnsupportedOperationException("blockingAwait 暂不支持 JS 平台（单线程事件循环无法阻塞等待）")
