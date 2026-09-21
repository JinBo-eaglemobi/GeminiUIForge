package org.gemini.ui.forge.utils

/** JVM 平台：直接使用 JDK ConcurrentHashMap */
actual fun <K, V> concurrentMapOf(): MutableMap<K, V> = java.util.concurrent.ConcurrentHashMap()
