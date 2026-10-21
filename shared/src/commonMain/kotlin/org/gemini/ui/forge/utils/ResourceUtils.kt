package org.gemini.ui.forge.utils

/**
 * 同步读取 Compose 内置资源字节（主要用于纯数据模型等无协程上下文场景的按需初次加载）。
 */
expect fun readResourceBytesSync(path: String): ByteArray
