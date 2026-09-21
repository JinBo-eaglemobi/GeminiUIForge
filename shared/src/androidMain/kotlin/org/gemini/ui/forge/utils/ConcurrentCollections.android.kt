package org.gemini.ui.forge.utils

/** Android 平台：直接使用 JDK ConcurrentHashMap (与 JVM 同源) */
actual fun <K, V> concurrentMapOf(): MutableMap<K, V> = java.util.concurrent.ConcurrentHashMap()
