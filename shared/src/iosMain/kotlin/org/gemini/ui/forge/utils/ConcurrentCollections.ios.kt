package org.gemini.ui.forge.utils

/** iOS 平台：普通 Map 占位 (待适配强化) */
actual fun <K, V> concurrentMapOf(): MutableMap<K, V> = mutableMapOf()
