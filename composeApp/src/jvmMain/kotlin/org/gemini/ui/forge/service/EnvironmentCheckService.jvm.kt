package org.gemini.ui.forge.service

import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.app.EnvironmentItemStatus
import org.gemini.ui.forge.model.app.FullEnvironmentStatus
import java.io.BufferedReader
import java.io.InputStreamReader

import kotlinx.serialization.json.Json
import org.gemini.ui.forge.model.app.PipPackageJson
import org.gemini.ui.forge.model.app.PipPackageInfo
import java.net.HttpURLConnection
import java.net.URL

class JvmEnvironmentCheckService : EnvironmentCheckService {

    private val json = Json { ignoreUnknownKeys = true }

    // 推荐的包列表 (未安装时显示在推荐中)
    private val recommendedPackages = listOf(
        "opencv-python" to "开源计算机视觉库，用于图像处理",
        "matplotlib" to "强大的 2D 绘图与数据可视化库",
        "torch" to "PyTorch 深度学习框架",
        "numpy" to "高性能的科学计算与数组处理库",
        "requests" to "简洁易用的 HTTP 网络请求库",
        "fastapi" to "现代、快速的 Web 框架",
        "uvicorn" to "用于运行 FastAPI 的 ASGI 服务器",
        "pydantic" to "数据验证与设置管理库",
        "beautifulsoup4" to "用于解析 HTML 和 XML 的爬虫库",
        "tqdm" to "快速、可扩展的进度条库",
        "scikit-image" to "基于 SciPy 的图像处理算法库",
        "diffusers" to "HuggingFace 扩散模型库",
        "transformers" to "HuggingFace 预训练模型库",
        "moviepy" to "视频编辑与处理库",
        "imageio" to "读取和写入图像数据的通用库",
        "pillow" to "强大的 Python 图像处理库 (PIL fork)",
        "rembg" to "AI 自动移除图片背景库",
        "onnxruntime" to "ONNX 模型的跨平台推理引擎",
        "openai" to "OpenAI 官方 API 客户端"
    )

    override suspend fun checkAll(): FullEnvironmentStatus = withContext(Dispatchers.IO) {
        val items = mutableListOf<EnvironmentItemStatus>()

        // 1. Check Python
        val pythonVer = runCommand("python --version") ?: runCommand("python3 --version")
        items.add(
            EnvironmentItemStatus(
                name = "python",
                labelRes = Res.string.env_item_python,
                isInstalled = pythonVer != null,
                version = pythonVer?.replace("Python ", "")
            )
        )

        // 2. Check Rembg
        val rembgInfo = runCommand("pip show rembg")
        items.add(
            EnvironmentItemStatus(
                name = "rembg",
                labelRes = Res.string.env_item_rembg,
                isInstalled = rembgInfo != null,
                version = extractVersion(rembgInfo)
            )
        )

        // 3. Check Pillow
        val pillowInfo = runCommand("pip show pillow")
        items.add(
            EnvironmentItemStatus(
                name = "pillow",
                labelRes = Res.string.env_item_pillow,
                isInstalled = pillowInfo != null,
                version = extractVersion(pillowInfo)
            )
        )

        // 4. Check ONNX Runtime
        val onnxInfo = runCommand("pip show onnxruntime")
        items.add(
            EnvironmentItemStatus(
                name = "onnxruntime",
                labelRes = Res.string.env_item_onnxruntime,
                isInstalled = onnxInfo != null,
                version = extractVersion(onnxInfo)
            )
        )

        FullEnvironmentStatus(items = items, isChecking = false)
    }

    override fun installItem(name: String): Flow<String> = flow {
        val commandList = when (name) {
            "python" -> {
                val os = System.getProperty("os.name").lowercase()
                if (os.contains("win")) {
                    emit("🔍 检测到 Windows 系统，正在尝试自动安装最新版 Python 3...")
                    emit("💡 如果系统弹出用户账户控制(UAC)对话框，请点击“是”。")
                    listOf("powershell", "-Command", "winget install Python.Python.3 --silent --accept-package-agreements --accept-source-agreements")
                } else {
                    emit("🌐 暂不支持此平台的自动安装，已为您打开 Python 官网。")
                    withContext(Dispatchers.Main) {
                        org.gemini.ui.forge.getPlatform().openInBrowser("https://www.python.org/downloads/")
                    }
                    return@flow
                }
            }
            "rembg" -> listOf("pip", "install", "--upgrade", "rembg", "-i", "https://pypi.tuna.tsinghua.edu.cn/simple")
            "pillow" -> listOf("pip", "install", "--upgrade", "pillow", "-i", "https://pypi.tuna.tsinghua.edu.cn/simple")
            "onnxruntime" -> listOf("pip", "install", "--upgrade", "onnxruntime", "-i", "https://pypi.tuna.tsinghua.edu.cn/simple")
            else -> return@flow
        }

        try {
            val process = ProcessBuilder(commandList)
                .redirectErrorStream(true)
                .start()

            val charsetName = if (System.getProperty("os.name").lowercase().contains("win")) "GBK" else "UTF-8"
            val reader = BufferedReader(InputStreamReader(process.inputStream, charsetName))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line!!)
            }
            process.waitFor()
            if (process.exitValue() == 0) {
                emit("✅ $name 安装完成！")
            } else {
                emit("❌ $name 安装失败，退出码: ${process.exitValue()}")
                if (name == "python") {
                    emit("💡 尝试手动前往官网下载: https://www.python.org/downloads/")
                }
            }
        } catch (e: Exception) {
            emit("❌ 安装出错: ${e.message}")
            if (name == "python") {
                withContext(Dispatchers.Main) {
                    org.gemini.ui.forge.getPlatform().openInBrowser("https://www.python.org/downloads/")
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun isPythonAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCommand("python --version") != null || runCommand("python3 --version") != null
    }

    override fun uninstallItem(name: String): Flow<String> = flow {
        val commandList = when (name) {
            "python" -> {
                val os = System.getProperty("os.name").lowercase()
                if (os.contains("win")) {
                    emit("🔍 正在尝试通过 winget 卸载 Python 3...")
                    emit("💡 如果系统弹出用户账户控制(UAC)对话框，请点击“是”。如果失败，请前往控制面板手动卸载。")
                    listOf("powershell", "-Command", "winget uninstall Python.Python.3 --silent")
                } else {
                    emit("🌐 暂不支持自动卸载 Python，请手动删除或使用系统包管理器卸载。")
                    return@flow
                }
            }
            "rembg", "pillow", "onnxruntime" -> listOf("pip", "uninstall", "-y", name)
            else -> return@flow
        }

        try {
            val process = ProcessBuilder(commandList)
                .redirectErrorStream(true)
                .start()

            val charsetName = if (System.getProperty("os.name").lowercase().contains("win")) "GBK" else "UTF-8"
            val reader = BufferedReader(InputStreamReader(process.inputStream, charsetName))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line!!)
            }
            process.waitFor()
            if (process.exitValue() == 0) {
                emit("✅ $name 卸载完成！")
            } else {
                emit("❌ $name 卸载失败，退出码: ${process.exitValue()}")
            }
        } catch (e: Exception) {
            emit("❌ 卸载出错: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getInstalledPipPackages(): List<PipPackageInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PipPackageInfo>()
        if (!isPythonAvailable()) return@withContext result

        val installedJsonStr = runCommand("pip list --format=json")
        val installedList = try {
            if (!installedJsonStr.isNullOrBlank()) {
                json.decodeFromString<List<PipPackageJson>>(installedJsonStr)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val installedNames = mutableSetOf<String>()
        installedList.forEach { pkg ->
            installedNames.add(pkg.name.lowercase())
            result.add(
                PipPackageInfo(
                    name = pkg.name,
                    version = pkg.version,
                    isInstalled = true
                )
            )
        }

        // 追加推荐但未安装的包
        recommendedPackages.forEach { (name, desc) ->
            if (!installedNames.contains(name.lowercase())) {
                result.add(
                    PipPackageInfo(
                        name = name,
                        isInstalled = false,
                        isRecommended = true,
                        description = desc
                    )
                )
            }
        }

        result.sortedBy { it.name }
    }

    override suspend fun fetchOutdatedPipPackages(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!isPythonAvailable()) return@withContext emptyMap()
        
        val outdatedJsonStr = runCommand("pip list --outdated --format=json")
        try {
            if (!outdatedJsonStr.isNullOrBlank()) {
                val outdatedList = json.decodeFromString<List<PipPackageJson>>(outdatedJsonStr)
                outdatedList.associate { it.name to (it.latestVersion ?: "") }
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override suspend fun searchPipPackage(query: String): PipPackageInfo? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        try {
            val url = URL("https://pypi.org/pypi/$query/json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val descRegex = """"summary"\s*:\s*"([^"]*)"""".toRegex()
                val versionRegex = """"version"\s*:\s*"([^"]+)"""".toRegex()
                
                val desc = descRegex.find(responseText)?.groups?.get(1)?.value ?: "来自 PyPI 的在线扩展包"
                val version = versionRegex.find(responseText)?.groups?.get(1)?.value ?: ""
                
                PipPackageInfo(
                    name = query,
                    version = null,
                    latestVersion = version,
                    isInstalled = false,
                    isRecommended = false,
                    description = desc
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchPackageUrl(packageName: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://pypi.org/pypi/$packageName/json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                // 简单提取 info.project_urls 里的连接
                val repoRegex = """"Repository"\s*:\s*"([^"]+)"""".toRegex()
                val homeRegex = """"Homepage"\s*:\s*"([^"]+)"""".toRegex()
                val sourceRegex = """"Source"\s*:\s*"([^"]+)"""".toRegex()

                val repoMatch = repoRegex.find(responseText)?.groups?.get(1)?.value
                val sourceMatch = sourceRegex.find(responseText)?.groups?.get(1)?.value
                val homeMatch = homeRegex.find(responseText)?.groups?.get(1)?.value

                repoMatch ?: sourceMatch ?: homeMatch
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchTopPackages(): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://hugovk.github.io/top-pypi-packages/top-pypi-packages-30-days.min.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val response = json.decodeFromString<org.gemini.ui.forge.model.app.TopPipPackagesResponse>(responseText)
                response.rows.map { it.project }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun batchInstallPipPackages(names: List<String>): Flow<String> = flow {
        if (names.isEmpty()) return@flow
        val commandList = mutableListOf("pip", "install", "--upgrade")
        commandList.addAll(names)
        commandList.addAll(listOf("-i", "https://pypi.tuna.tsinghua.edu.cn/simple"))

        try {
            val process = ProcessBuilder(commandList).redirectErrorStream(true).start()
            val charsetName = if (System.getProperty("os.name").lowercase().contains("win")) "GBK" else "UTF-8"
            val reader = BufferedReader(InputStreamReader(process.inputStream, charsetName))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line!!)
            }
            process.waitFor()
            if (process.exitValue() == 0) {
                emit("✅ 批量操作完成！")
            } else {
                emit("❌ 批量操作失败，退出码: ${process.exitValue()}")
            }
        } catch (e: Exception) {
            emit("❌ 操作出错: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    override fun batchUninstallPipPackages(names: List<String>): Flow<String> = flow {
        if (names.isEmpty()) return@flow
        val commandList = mutableListOf("pip", "uninstall", "-y")
        commandList.addAll(names)

        try {
            val process = ProcessBuilder(commandList).redirectErrorStream(true).start()
            val charsetName = if (System.getProperty("os.name").lowercase().contains("win")) "GBK" else "UTF-8"
            val reader = BufferedReader(InputStreamReader(process.inputStream, charsetName))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line!!)
            }
            process.waitFor()
            if (process.exitValue() == 0) {
                emit("✅ 批量卸载完成！")
            } else {
                emit("❌ 批量卸载失败，退出码: ${process.exitValue()}")
            }
        } catch (e: Exception) {
            emit("❌ 操作出错: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    private fun runCommand(cmd: String): String? {
        return try {
            val process = ProcessBuilder(*cmd.split(" ").toTypedArray()).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && output.isNotEmpty()) output else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractVersion(pipOutput: String?): String? {
        if (pipOutput == null) return null
        return pipOutput.lines().find { it.startsWith("Version:") }?.replace("Version:", "")?.trim()
    }
}

actual fun createEnvironmentCheckService(): EnvironmentCheckService = JvmEnvironmentCheckService()
