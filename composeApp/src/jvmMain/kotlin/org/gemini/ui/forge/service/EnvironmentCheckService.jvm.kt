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

class JvmEnvironmentCheckService : EnvironmentCheckService {

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
        val command = when (name) {
            "rembg" -> "pip install rembg -i https://pypi.tuna.tsinghua.edu.cn/simple"
            "pillow" -> "pip install pillow -i https://pypi.tuna.tsinghua.edu.cn/simple"
            "onnxruntime" -> "pip install onnxruntime -i https://pypi.tuna.tsinghua.edu.cn/simple"
            else -> return@flow
        }

        try {
            val process = ProcessBuilder(*command.split(" ").toTypedArray())
                .redirectErrorStream(true)
                .start()

            val charsetName = if (System.getProperty("os.name").lowercase().contains("win")) "GBK" else "UTF-8"
            val reader = BufferedReader(InputStreamReader(process.inputStream, charsetName))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line!!)
            }
            process.waitFor()
        } catch (e: Exception) {
            emit("❌ 安装出错: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun isPythonAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCommand("python --version") != null || runCommand("python3 --version") != null
    }

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
