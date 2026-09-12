package org.gemini.ui.forge

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 跨平台标识接口，提供当前运行环境的基础信息
 */
interface Platform {
    /** 平台名称描述（例如 "Android", "iOS", "JVM" 等） */
    val name: String
    /** 在浏览器中打开指定的 URL 链接 */
    fun openInBrowser(url: String)
    /** 在系统文件管理器中打开指定路径 */
    fun openInFileExplorer(path: String)
    /** 打开系统的凭据存储管理界面（如 Windows 凭据管理器 / macOS 钥匙串访问）；平台不支持时为空操作 */
    fun openCredentialStore()
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
fun getCurrentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * 跨平台的鼠标水平调整大小图标，主要用于桌面端的边界拖拽
 */
expect val ResizeHorizontalIcon: androidx.compose.ui.input.pointer.PointerIcon

/**
 * 跨平台的鼠标垂直调整大小图标，主要用于桌面端的边界拖拽
 */
expect val ResizeVerticalIcon: androidx.compose.ui.input.pointer.PointerIcon

/**
 * 获取当前设备的逻辑核心数量 (用于并行任务调度)
 */
expect fun getProcessorCount(): Int

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
    } else if (format == "yyyy_MM_dd") {
        "${year}_${month}_$day"
    } else if (format == "yyyy-MM-dd") {
        "$year-$month-$day"
    } else {
        "$year-$month-$day $hour:$minute:$second"
    }
}

/**
 * 获取当前日期的格式化字符串 (yyyy-MM-dd)
 */
fun getCurrentDate(): String = formatTimestamp(getCurrentTimeMillis(), "yyyy-MM-dd")

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

/**
 * 跨平台获取当前用户主目录（各平台真实实现如下）
 * - JVM (桌面端): System.getProperty("user.home")，如 C:\Users\xxx
 * - Android: androidContext.filesDir.absolutePath（应用私有文件目录，如 /data/user/0/<包名>/files）
 * - Web (JS): "opfs://"（OPFS 虚拟文件系统协议根路径）
 * - iOS: NSHomeDirectory()（应用沙盒主目录，如 /var/mobile/Containers/Data/Application/<UUID>）
 */
expect val userHomePath: String

/**
 * 跨平台获取当前运行工作目录路径。
 *
 * 当前进程启动时所在的工作目录（Working Directory），一般用于相对路径向绝对路径的转换和运行期环境自检。
 *各平台真实实现如下：
 * - JVM (桌面端): System.getProperty("user.dir")，即当前进程的工作目录
 * - Android: 对应 [userHomePath]（即应用私有文件目录，保障存储可用性）
 * - Web (JS): 对应 [userHomePath]（即 "opfs://" 虚拟协议根路径）
 * - iOS: 对应 [userHomePath]（即应用沙盒主目录）
 */
expect val runDir: String

/**
 * 跨平台获取应用程序安装/宿主程序包根目录路径。
 *
 * 物理程序、类路径（JAR）或二进制 Bundle 所在的绝对安装目录，可用于加载内置资产文件和自举逻辑。
 * 各平台真实实现如下：
 * - JVM (桌面端): 双模态自适应：在开发环境下（IDE/Gradle 运行）自动向上回溯定位包含 settings.gradle.kts/.git 的真实项目工程根目录；在生产打包环境下返回真实物理安装包（EXE/MSI/JAR）宿主根目录
 * - Android: 对应 `androidContext.applicationInfo.dataDir`（返回如 `/data/user/0/org.gemini.ui.forge`），即应用物理包根目录
 * - Web (JS): 对应 [userHomePath]（即 "opfs://" 虚拟协议根路径）
 * - iOS: 对应 `NSBundle.mainBundle.bundlePath`，即只读的 App Bundle 安装包根目录
 */
expect val appDir: String
