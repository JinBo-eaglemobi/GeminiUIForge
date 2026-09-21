package org.gemini.ui.forge.utils

/**
 * 跨平台并发安全 Map 工厂 (expect)
 *
 * JVM / Android 返回 ConcurrentHashMap；
 * JS / iOS 平台 UI 与访问以单线程为主，返回普通 LinkedHashMap 占位 (待适配强化)。
 */
internal expect fun <K, V> concurrentMapOf(): MutableMap<K, V>
