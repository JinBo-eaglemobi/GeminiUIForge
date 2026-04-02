package org.gemini.ui.forge

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * 获取当前系统的 Unix 时间戳（毫秒）
 */
expect fun getCurrentTimeMillis(): Long

/**
 * 将时间戳格式化为指定的日期字符串
 * @param format 格式模板，例如 "yyyy-MM-dd HH:mm:ss" 或 "HH:mm:ss"
 */
expect fun Long.formatTimestamp(format: String = "HH:mm:ss"): String
