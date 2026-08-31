package org.gemini.ui.forge.service

import me.friwi.jcefmaven.CefAppBuilder
import org.cef.CefApp
import org.gemini.ui.forge.utils.AppLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * CEF（Chromium 嵌入式浏览器）运行环境管理器（仅桌面端）。
 *
 * 设计目标：支持用户手动预置 Chromium natives 资源，避免每次启动在线下载：
 * 1. 约定安装目录为 ~/.geminiuiforge/cef；
 * 2. 用户可将下载好的 natives 压缩包（.jar/.zip/.tar.gz/.tgz）直接放入该目录；
 * 3. [extractPlacedArchives] 会自动解压这些压缩包到目录根部，之后 jcefmaven
 *    检测到已安装的 natives 将跳过下载，实现"一次预置、后续秒开"；
 * 4. 若目录既无已解压资源也无压缩包，[obtainApp] 仍可走 jcefmaven 默认在线下载兜底。
 */
object CefEnvironment {

    /** jcefmaven 版本号（与 gradle/libs.versions.toml 中保持一致） */
    private const val JCEFMAVEN_VERSION = "146.0.10"

    /** natives 安装目录：~/.geminiuiforge/cef */
    private val installDirFile: File by lazy {
        File(System.getProperty("user.home") ?: ".", ".geminiuiforge${File.separator}cef")
    }

    /**
     * natives 版本标签：jcefmaven 的 natives 是独立 artifact（me.friwi:jcef-natives-{platform}），
     * 其版本 tag 与 jcefmaven 版本号不同步，而是由 jcef-api 依赖内的 build_meta.json 定义。
     * 该值取自 jcef-api:jcef-d3de827+cef-146.0.10+g8219561+chromium-146.0.7680.179 的 release_tag 字段。
     */
    private const val NATIVES_TAG = "jcef-d3de827+cef-146.0.10+g8219561+chromium-146.0.7680.179"

    /** Windows amd64 平台 natives 压缩包直链（Maven Central 镜像，已 HEAD 实证 200 / 154.6 MB） */
    const val NATIVES_URL_WINDOWS =
        "https://repo.maven.apache.org/maven2/me/friwi/jcef-natives-windows-amd64/$NATIVES_TAG/jcef-natives-windows-amd64-$NATIVES_TAG.jar"

    /** 下载页面（GitHub Release tag 无 v 前缀，已 HEAD 实证 200） */
    const val DOWNLOAD_PAGE_URL = "https://github.com/jcefmaven/jcefmaven/releases/tag/$JCEFMAVEN_VERSION"

    /** 已构建的单例 CefApp（构建过程线程安全，可重复调用） */
    @Volatile
    private var cachedApp: CefApp? = null

    /** 安装目录绝对路径（供 UI 展示与打开目录） */
    val installDirPath: String get() = installDirFile.absolutePath

    /**
     * 探测 natives 压缩包的总大小（HEAD 请求 Content-Length）。
     * 供下载进度面板展示"已下载 / 总大小"统计；网络异常时返回 -1（视为未知）。
     */
    fun probeNativesSize(): Long = try {
        val conn = URL(NATIVES_URL_WINDOWS).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val len = conn.contentLengthLong
        conn.disconnect()
        if (len > 0) len else -1L
    } catch (e: Exception) {
        AppLogger.w(TAG, "探测 natives 大小失败: ${e.message}")
        -1L
    }

    /**
     * 判断 natives 是否已解压就绪（按平台检查 libcef 核心库标记文件）。
     */
    fun isExtracted(): Boolean = markerFile().exists()

    /**
     * 确保安装目录存在（返回目录对象）。
     */
    fun ensureInstallDir(): File {
        if (!installDirFile.exists()) {
            installDirFile.mkdirs()
        }
        return installDirFile
    }

    /**
     * 补写 jcefmaven 的安装标记文件（install.lock + build_meta.json）。
     *
     * 背景：jcefmaven 的 CefAppBuilder 在安装目录校验不通过时会【递归删除整个安装目录】
     * 并重新在线下载——而 install.lock 只由其安装流程创建，不会出现在任何 natives
     * 压缩包中，用户手动预置的文件因此必然被误删。
     * 本地 natives 解压完整时调用本方法补齐标记，使校验必过：jcefmaven 直接初始化，
     * 不再删除文件、不再触发下载。
     */
    fun ensureInstalledMarkers() {
        if (!isExtracted()) return
        val lock = File(installDirFile, "install.lock")
        if (!lock.exists()) lock.createNewFile()
        val buildInfo = File(installDirFile, "build_meta.json")
        if (!buildInfo.exists()) {
            // 与 jcef-api 依赖一致的构建信息（platform 需为具体平台标识而非压缩包内的通配符 "*",
            // 否则 jcefmaven 校验不通过仍会触发删除重下）
            buildInfo.writeText(
                "{\"jcef_url\":\"\",\"release_tag\":\"$NATIVES_TAG\",\"release_url\":\"\",\"platform\":\"${currentPlatformId()}\"}"
            )
        }
    }

    /** 当前平台标识（与 jcefmaven EnumPlatform 的 os-arch 小写格式一致，如 windows-amd64） */
    private fun currentPlatformId(): String {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        val arch = System.getProperty("os.arch")?.lowercase() ?: "amd64"
        val osPart = when {
            os.contains("win") -> "windows"
            os.contains("mac") || os.contains("darwin") -> "macos"
            else -> "linux"
        }
        val archPart = if (arch.contains("64")) "amd64" else arch
        return "$osPart-$archPart"
    }

    /**
     * 解压安装目录内的 natives 压缩包（支持多层嵌套：jar/zip → tar.gz → 内容）。
     * 使用系统自带 tar（Windows 10+ 内置 bsdtar，兼容 zip/tar.gz）。
     * 每轮解压成功后删除对应压缩包（保持安装目录干净并避免下轮重复解压），
     * 循环直到 natives 就绪或无更多压缩包（最多 3 轮）。
     * @return natives 是否已就绪（无压缩包且未就绪视为失败，交由调用方引导）
     */
    fun extractPlacedArchives(onLog: (String) -> Unit = {}): Boolean {
        val dir = installDirFile
        if (!dir.isDirectory) return false
        var round = 0
        while (round < 3) {
            if (isExtracted()) return true
            val archives = dir.listFiles { file ->
                file.isFile && (
                        file.extension.equals("jar", true) ||
                                file.extension.equals("zip", true) ||
                                file.name.endsWith(".tar.gz", true) ||
                                file.extension.equals("tgz", true)
                        )
            } ?: return false
            if (archives.isEmpty()) return false
            for (archive in archives) {
                onLog("正在解压 ${archive.name} ...")
                val ok = try {
                    val process = ProcessBuilder(
                        "tar", "-xf", archive.absolutePath, "-C", dir.absolutePath
                    ).redirectErrorStream(true).start()
                    process.inputStream.bufferedReader().forEachLine { line -> onLog(line) }
                    process.waitFor(10, TimeUnit.MINUTES) && process.exitValue() == 0
                } catch (e: Exception) {
                    AppLogger.e(TAG, "解压 natives 压缩包失败: ${archive.name}", e)
                    false
                }
                // 解压成功即清理压缩包：既是安装目录的整洁约定，也防止下轮被再次解压
                if (ok) archive.delete()
            }
            round++
        }
        return isExtracted()
    }

    /**
     * 获取（或构建）CefApp 单例。
     * 若 natives 缺失，将触发 jcefmaven 默认的在线下载流程（github / maven central 双镜像）。
     * 该方法可能长时间阻塞，建议在 IO 调度器中调用。
     */
    @Synchronized
    fun obtainApp(onProgress: (Float) -> Unit = {}): CefApp {
        cachedApp?.let { return it }
        ensureInstallDir()
        val builder = CefAppBuilder()
        // 指定安装目录：已预置 natives 时不会重复下载
        builder.setInstallDir(installDirFile)
        // 使用屏幕内渲染模式（配合 Swing 嵌入，避免 OSR 模式的 JOGL 依赖与 JVM 参数）
        builder.getCefSettings().windowless_rendering_enabled = false
        // 进度透出：EXTRACTING / DOWNLOADING 阶段回传归一化进度 [0,1]；-1（不可预估）原值透传
        builder.setProgressHandler { state, percent ->
            onProgress(if (percent < 0f) -1f else percent / 100f)
        }
        val app = builder.build()
        cachedApp = app
        return app
    }

    /**
     * 按当前操作系统返回 CEF 核心库标记文件。
     */
    private fun markerFile(): File {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        val markerName = when {
            os.contains("win") -> "libcef.dll"
            os.contains("mac") || os.contains("darwin") -> "libcef.dylib"
            else -> "libcef.so"
        }
        return File(installDirFile, markerName)
    }

    private const val TAG = "CefEnvironment"
}
