package org.gemini.ui.forge

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * 使用 expect/actual 获取当前系统的 Unix 时间戳（毫秒）
 */
expect fun getCurrentTimeMillis(): Long

/**
 * 跨平台的鼠标水平调整大小图标
 */
expect val ResizeHorizontalIcon: androidx.compose.ui.input.pointer.PointerIcon

/**
 * 将时间戳格式化为本地时间格式 (yyyy-MM-dd HH:mm:ss)
 */
fun formatTimestamp(timeMillis: Long, format: String = "yyyy-MM-dd HH:mm:ss"): String {
    if (timeMillis <= 0L) return ""
    val instant = Instant.fromEpochMilliseconds(timeMillis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    
    // 手动拼接以实现 yyyy-MM-dd HH:mm:ss 格式 (由于 kotlinx-datetime 原生格式化受限)
    val year = localDateTime.year
    val month = localDateTime.monthNumber.toString().padStart(2, '0')
    val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    val second = localDateTime.second.toString().padStart(2, '0')
    
    return if (format == "HH:mm:ss") {
        "$hour:$minute:$second"
    } else {
        "$year-$month-$day $hour:$minute:$second"
    }
}
