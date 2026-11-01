package org.gemini.ui.forge.utils

/**
 * 跨平台阻塞式等待工具 (expect 声明)。
 *
 * 在非挂起上下文中同步等待一个挂起计算块的结果。
 * 典型场景：UI 事件回调（非 suspend）中需要兜底同步获取异步 IO 计算结果
 * （如素材内容哈希计算）时使用，避免在 commonMain 直接依赖 JVM 专属的
 * kotlinx.coroutines.runBlocking 导致多平台解析失败。
 *
 * 平台行为：
 * - JVM / Android / iOS：内部委托 runBlocking 真实阻塞当前线程直至结果返回；
 * - JS：单线程事件循环模型无法阻塞，调用将抛出 UnsupportedOperationException。
 */
expect fun <T> blockingAwait(block: suspend () -> T): T
