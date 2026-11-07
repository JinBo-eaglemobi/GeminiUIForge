package org.gemini.ui.forge.utils

import org.jetbrains.skiko.hostOs
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 跨平台标准系统目录 Python / Pip / 包管理器动态寻址器
 *
 * 严格遵循 Windows / macOS / Linux 传统操作系统官方标准安装目录进行探测，
 * 解决 JVM 进程启动后环境变量 PATH 不自动刷新、Windows Store 0 字节假别名干扰、
 * 以及 Scripts 目录未配置 PATH 导致找不到 pip 等问题。
 */
object PythonEnvResolver {

    private const val TAG = "PythonEnvResolver"

    @Volatile
    private var cachedWingetExecutable: String? = null

    /**
     * 解析当前系统中真实可用的 Python 3 可执行文件命令或绝对路径
     */
    fun resolvePythonExecutable(): String? {
        // 1. 优先尝试当前环境变量 PATH 中的标准命令
        val pathCommands = if (hostOs.isWindows) {
            listOf("python", "python3")
        } else {
            listOf("python3", "python")
        }
        for (cmd in pathCommands) {
            if (isValidPythonExecutable(cmd)) {
                return cmd
            }
        }

        // 2. 扫描操作系统传统标准安装目录（突破 JVM 缓存的旧 PATH 环境变量）
        val standardCandidates = getStandardOsPythonPaths()
        for (candidate in standardCandidates) {
            if (candidate.exists() && candidate.isFile && isValidPythonExecutable(candidate.absolutePath)) {
                AppLogger.d(TAG, "🔍 从系统标准安装目录定位到 Python: ${candidate.absolutePath}")
                return candidate.absolutePath
            }
        }

        return null
    }

    /**
     * 获取当前系统中已安装 Python 的版本号（如 "3.12.8"），未安装则返回 null
     */
    fun getPythonVersion(): String? {
        val pythonExe = resolvePythonExecutable() ?: return null
        val output = runQuickCommand(listOf(pythonExe, "--version")) ?: return null
        return if (output.startsWith("Python ", ignoreCase = true)) {
            output.substringAfter("Python ", "").trim().lineSequence().firstOrNull()
        } else {
            null
        }
    }

    /**
     * 解析当前系统中真实可用的 Pip 基础调用命令列表
     * 优先采用 `<pythonExe> -m pip`，彻底规避 Scripts 未加入 PATH 或 pip.exe 路径损坏问题
     */
    fun resolvePipCommand(): List<String> {
        val pythonExe = resolvePythonExecutable()
        if (pythonExe != null) {
            val pipVer = runQuickCommand(listOf(pythonExe, "-m", "pip", "--version"))
            if (pipVer != null) {
                return listOf(pythonExe, "-m", "pip")
            }
        }
        if (runQuickCommand(listOf("pip", "--version")) != null) {
            return listOf("pip")
        }
        if (runQuickCommand(listOf("pip3", "--version")) != null) {
            return listOf("pip3")
        }
        return if (pythonExe != null) listOf(pythonExe, "-m", "pip") else listOf("pip")
    }

    /**
     * 探测 Windows 系统下可用的 winget 可执行程序路径（兼容标准用户、内置 Administrator 账户、独立解压目录及 WindowsApps 实体目录）
     * 若系统未安装 App Installer 或应用执行别名不可用，则返回 null
     */
    fun resolveWingetExecutable(forceRefresh: Boolean = false): String? {
        if (!hostOs.isWindows) return null
        if (!forceRefresh) {
            cachedWingetExecutable?.let { return it }
        }

        // 1. 尝试系统 PATH 中的 winget
        if (runQuickCommand(listOf("winget", "--version")) != null) {
            cachedWingetExecutable = "winget"
            return "winget"
        }

        val localAppData = System.getenv("LOCALAPPDATA")
        val userHome = System.getProperty("user.home") ?: ""
        val appDataLocalDir = if (!localAppData.isNullOrBlank()) File(localAppData) else File(userHome, "AppData\\Local")

        // 2. 尝试 WindowsApps 应用执行别名（注意：AppExecLink 为 0 字节重解析点，直接执行验证而非依赖 File.exists）
        val wingetAlias = File(appDataLocalDir, "Microsoft\\WindowsApps\\winget.exe")
        if (runQuickCommand(listOf(wingetAlias.absolutePath, "--version")) != null) {
            cachedWingetExecutable = wingetAlias.absolutePath
            return wingetAlias.absolutePath
        }

        // 3. 尝试独立部署目录 (%LOCALAPPDATA%\Programs\WinGet\winget.exe)
        val standaloneWinget = File(appDataLocalDir, "Programs\\WinGet\\winget.exe")
        if (standaloneWinget.exists() && runQuickCommand(listOf(standaloneWinget.absolutePath, "--version")) != null) {
            cachedWingetExecutable = standaloneWinget.absolutePath
            return standaloneWinget.absolutePath
        }

        // 4. 尝试扫描 C:\Program Files\WindowsApps\Microsoft.DesktopAppInstaller_*\winget.exe
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val windowsAppsDir = File(programFiles, "WindowsApps")
        val matchedInstallers = windowsAppsDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("Microsoft.DesktopAppInstaller_", ignoreCase = true)
        }?.sortedByDescending { it.name }.orEmpty()
        for (dir in matchedInstallers) {
            val exe = File(dir, "winget.exe")
            if (exe.exists() && runQuickCommand(listOf(exe.absolutePath, "--version")) != null) {
                cachedWingetExecutable = exe.absolutePath
                return exe.absolutePath
            }
        }

        // 5. 通过 PowerShell Get-AppxPackage 探测 InstallLocation（兼容内置 Administrator 账户或受限目录列表权限）
        val psPath = queryAppxDesktopInstallerWingetPath()
        if (psPath != null && runQuickCommand(listOf(psPath, "--version")) != null) {
            cachedWingetExecutable = psPath
            return psPath
        }

        cachedWingetExecutable = null
        return null
    }

    /**
     * 通过 PowerShell 查询 Microsoft.DesktopAppInstaller 的真实安装路径并定位 winget.exe
     */
    private fun queryAppxDesktopInstallerWingetPath(): String? {
        val psScript = """
            ${'$'}pkg = Get-AppxPackage -Name 'Microsoft.DesktopAppInstaller' -ErrorAction SilentlyContinue | Sort-Object Version -Descending | Select-Object -First 1
            if (-not ${'$'}pkg) {
                ${'$'}pkg = Get-AppxPackage -AllUsers -Name 'Microsoft.DesktopAppInstaller' -ErrorAction SilentlyContinue | Sort-Object Version -Descending | Select-Object -First 1
            }
            if (${'$'}pkg -and ${'$'}pkg.InstallLocation) { Write-Output ${'$'}pkg.InstallLocation }
        """.trimIndent()
        val output = runQuickCommand(listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", psScript)) ?: return null
        val location = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val exe = File(location, "winget.exe")
        return if (exe.exists()) exe.absolutePath else null
    }

    /**
     * 探测 macOS 系统下可用的 Homebrew 可执行程序路径
     */
    fun resolveBrewExecutable(): String? {
        if (!hostOs.isMacOS) return null
        val candidates = listOf(
            "brew",
            "/opt/homebrew/bin/brew",
            "/usr/local/bin/brew"
        )
        for (cmd in candidates) {
            if (runQuickCommand(listOf(cmd, "--version")) != null) {
                return cmd
            }
        }
        return null
    }

    /**
     * 获取各操作系统传统官方标准 Python 安装目录候选列表
     */
    private fun getStandardOsPythonPaths(): List<File> {
        val candidates = mutableListOf<File>()
        val userHome = System.getProperty("user.home") ?: ""

        if (hostOs.isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA")
                ?: if (userHome.isNotEmpty()) "$userHome\\AppData\\Local" else ""
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            val programData = System.getenv("ProgramData") ?: "C:\\ProgramData"

            // 1. Python 官方安装程序默认用户级目录: %LOCALAPPDATA%\Programs\Python\Python3*\python.exe
            if (localAppData.isNotEmpty()) {
                collectWindowsPythonSubdirs(File(localAppData, "Programs\\Python"), candidates)
            }

            // 2. Python 官方安装程序系统级目录: C:\Program Files\Python3*\python.exe & C:\Program Files (x86)\Python3*
            collectWindowsPythonSubdirs(File(programFiles), candidates)
            collectWindowsPythonSubdirs(File(programFilesX86), candidates)

            // 3. Windows 传统 C 盘根目录: C:\Python3*\python.exe
            collectWindowsPythonSubdirs(File("C:\\"), candidates)

            // 4. 标准 Conda / Pyenv 默认安装路径
            if (userHome.isNotEmpty()) {
                candidates.add(File(userHome, "miniconda3\\python.exe"))
                candidates.add(File(userHome, "anaconda3\\python.exe"))
                candidates.add(File(userHome, "AppData\\Local\\miniconda3\\python.exe"))
                candidates.add(File(userHome, "AppData\\Local\\anaconda3\\python.exe"))
                candidates.add(File(userHome, ".pyenv\\pyenv-win\\shims\\python.bat"))
            }
            candidates.add(File(programData, "miniconda3\\python.exe"))
            candidates.add(File(programData, "anaconda3\\python.exe"))
        } else if (hostOs.isMacOS) {
            // 1. Homebrew 标准目录 (Apple Silicon & Intel)
            candidates.add(File("/opt/homebrew/bin/python3"))
            candidates.add(File("/usr/local/bin/python3"))

            // 2. Python.org 官方 macOS .pkg 框架标准目录: /Library/Frameworks/Python.framework/Versions/*/bin/python3
            candidates.add(File("/Library/Frameworks/Python.framework/Versions/Current/bin/python3"))
            val frameworkVersionsDir = File("/Library/Frameworks/Python.framework/Versions")
            if (frameworkVersionsDir.exists() && frameworkVersionsDir.isDirectory) {
                frameworkVersionsDir.listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("3.") }
                    ?.sortedByDescending { parseVersionScore(it.name) }
                    ?.forEach { verDir ->
                        candidates.add(File(verDir, "bin/python3"))
                    }
            }

            // 3. 标准 Conda / Pyenv 目录与系统目录
            if (userHome.isNotEmpty()) {
                candidates.add(File(userHome, ".pyenv/shims/python3"))
                candidates.add(File(userHome, "miniconda3/bin/python3"))
                candidates.add(File(userHome, "anaconda3/bin/python3"))
            }
            candidates.add(File("/opt/miniconda3/bin/python3"))
            candidates.add(File("/opt/anaconda3/bin/python3"))
            candidates.add(File("/usr/bin/python3"))
        } else {
            // Linux / Unix 传统系统标准目录
            candidates.add(File("/usr/bin/python3"))
            candidates.add(File("/usr/local/bin/python3"))
            candidates.add(File("/bin/python3"))
            if (userHome.isNotEmpty()) {
                candidates.add(File(userHome, ".local/bin/python3"))
                candidates.add(File(userHome, ".pyenv/shims/python3"))
                candidates.add(File(userHome, "miniconda3/bin/python3"))
                candidates.add(File(userHome, "anaconda3/bin/python3"))
            }
        }

        return candidates
    }

    /**
     * 扫描指定父目录下形如 Python312、Python311 的标准子目录并按版本号从高到低排序
     */
    private fun collectWindowsPythonSubdirs(parentDir: File, outList: MutableList<File>) {
        if (!parentDir.exists() || !parentDir.isDirectory) return
        val matchedDirs = parentDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("Python3", ignoreCase = true)
        } ?: return

        matchedDirs
            .sortedByDescending { parseVersionScore(it.name) }
            .forEach { dir ->
                val exe = File(dir, "python.exe")
                if (exe.exists()) {
                    outList.add(exe)
                }
            }
    }

    /**
     * 提取目录名称中的版本号数值以便高版本优先排序（例如 Python312 -> 312, 3.12 -> 312）
     */
    private fun parseVersionScore(dirName: String): Int {
        val digits = dirName.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    /**
     * 验证目标可执行文件是否为真实可用的 Python 3 解释器（过滤 WindowsApps 0字节应用商店假别名）
     */
    private fun isValidPythonExecutable(executable: String): Boolean {
        val output = runQuickCommand(listOf(executable, "--version")) ?: return false
        return output.startsWith("Python 3", ignoreCase = true) || output.startsWith("Python 2", ignoreCase = true)
    }

    /**
     * 快速执行探测命令并返回标准输出文本（带 5 秒超时防阻塞）
     */
    fun runQuickCommand(command: List<String>, timeoutSeconds: Long = 5L): String? {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use {
                it.readText().trim()
            }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() == 0 && output.isNotEmpty()) output else null
        } catch (e: Exception) {
            null
        }
    }
}
