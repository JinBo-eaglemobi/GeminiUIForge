package org.gemini.ui.forge

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * 跨平台标识接口，提供当前运行环境的基础信息
 */
interface Platform {
    /** 平台名称描述（例如 "Android", "iOS", "JVM" 等） */
    val name: String
    /** 在浏览器中打开指定的 URL 链接 */
    fun openInBrowser(url: String)
    /** 执行软件更新替换并自动重启：传入下载好的临时文件路径 */
    fun applyUpdateAndRestart(tempFilePath: String)
}

/**
 * 获取当前运行平台的实例
 * @return 对应的 [Platform] 实现类
 */
expect fun getPlatform(): Platform

/**
 * 获取当前系统的 Unix 时间戳
 * @return 以毫秒为单位的时间戳
 */
expect fun getCurrentTimeMillis(): Long

/**
 * 跨平台的鼠标水平调整大小图标，主要用于桌面端的边界拖拽
 */
expect val ResizeHorizontalIcon: androidx.compose.ui.input.pointer.PointerIcon

/**
 * 跨平台的鼠标垂直调整大小图标，主要用于桌面端的边界拖拽
 */
expect val ResizeVerticalIcon: androidx.compose.ui.input.pointer.PointerIcon

/**
 * 将给定的时间戳格式化为指定的本地时间字符串格式
 * @param timeMillis 时间戳（毫秒）
 * @param format 目标时间格式，默认 "yyyy-MM-dd HH:mm:ss"
 * @return 格式化后的时间字符串
 */
fun formatTimestamp(timeMillis: Long, format: String = "yyyy-MM-dd HH:mm:ss"): String {
    if (timeMillis <= 0L) return ""
    val instant = Instant.fromEpochMilliseconds(timeMillis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    
    // 手动拼接以实现 yyyy-MM-dd HH:mm:ss 格式 (由于 kotlinx-datetime 原生格式化受限)
    val year = localDateTime.year
    val month = localDateTime.month.number.toString().padStart(2, '0')
    val day = localDateTime.day.toString().padStart(2, '0')
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    val second = localDateTime.second.toString().padStart(2, '0')
    
    return if (format == "HH:mm:ss") {
        "$hour:$minute:$second"
    } else {
        "$year-$month-$day $hour:$minute:$second"
    }
}

/**
 * 将 Gemini API 返回的 RFC 3339 格式字符串解析并格式化为本地时间
 * @param isoString RFC 3339 格式的时间字符串 (如 2024-05-01T12:00:00Z)
 */
fun formatIsoTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "未知"
    return try {
        // Instant.parse 支持 ISO 8601 / RFC 3339 格式
        val instant = Instant.parse(isoString)
        formatTimestamp(instant.toEpochMilliseconds())
    } catch (e: Exception) {
        isoString // 解析失败则原样返回
    }
}
