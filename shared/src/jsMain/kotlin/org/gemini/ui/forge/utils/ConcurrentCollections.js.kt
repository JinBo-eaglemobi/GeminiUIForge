package org.gemini.ui.forge.utils

/** JS 平台：单线程事件循环模型下普通 Map 占位 (待适配强化) */
actual fun <K, V> concurrentMapOf(): MutableMap<K, V> = mutableMapOf()
