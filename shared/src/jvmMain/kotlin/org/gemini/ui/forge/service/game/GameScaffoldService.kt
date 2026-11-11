package org.gemini.ui.forge.service.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.gemini.ui.forge.model.gameproject.ForgeGameProjectConfig
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.looseJson
import org.jetbrains.skiko.hostOs
import java.io.File

/**
 * UI Studio 游戏项目脚手架与全自动构建服务 (JVM 平台)。
 * 负责游戏目录安全性校验、专属配置文件 forge-game.json 识别、模板灌入与 pnpm 构建流水线调度。
 */
object GameScaffoldService {

    private const val TAG = "GameScaffoldService"
    const val FORGE_GAME_CONFIG_FILE = "forge-game.json"

    /**
     * 校验游戏保存目录是否合法。
     * 铁律：游戏保存目录必须由用户指定，且严禁存放在程序自身私有缓存目录（~/.geminiuiforge）下！
     *
     * @return 错误文案，若返回 null 表示路径完全合法有效。
     */
    fun validateGamePath(pathStr: String?): String? {
        if (pathStr.isNullOrBlank()) {
            return "游戏保存目录不能为空，请选择或指定一个有效磁盘目录"
        }
        val file = File(pathStr.trim())
        val absPath = file.absolutePath.replace("\\", "/").lowercase()

        val userHome = System.getProperty("user.home")?.replace("\\", "/")?.lowercase()
        val privateCachePath = if (userHome != null) "$userHome/.geminiuiforge" else null

        if (privateCachePath != null && (absPath == privateCachePath || absPath.startsWith("$privateCachePath/"))) {
            return "游戏保存目录严禁存放在程序私有缓存目录 (~/.geminiuiforge) 下，请选择外部独立的磁盘工作目录！"
        }
        return null
    }

    /**
     * 读取指定目录下的 forge-game.json 识别配置文件。
     * 若文件不存在或损坏，返回 null。
     */
    fun readForgeGameConfig(gameRootDir: File): ForgeGameProjectConfig? {
        val configFile = File(gameRootDir, FORGE_GAME_CONFIG_FILE)
        if (!configFile.exists() || !configFile.isFile) return null
        return try {
            val content = configFile.readText(Charsets.UTF_8)
            looseJson.decodeFromString<ForgeGameProjectConfig>(content)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse $FORGE_GAME_CONFIG_FILE in ${gameRootDir.absolutePath}", e)
            null
        }
    }

    /**
     * 判断一个游戏工程是否已经被成功激活过。
     * 条件：
     * 1. 存在 forge-game.json 且 isActivated == true；
     * 2. build 目录下存在 index.html 与 index.js。
     */
    fun isGameProjectActivated(gameRootDir: File): Boolean {
        val config = readForgeGameConfig(gameRootDir) ?: return false
        val runDir = File(gameRootDir, config.runDir)
        val indexHtml = File(runDir, config.entryHtml)
        val indexJs = File(runDir, config.entryJs)
        return config.isActivated && indexHtml.exists() && indexJs.exists()
    }

    /**
     * 执行完整的脚手架初始化与构建流水线。
     * 流程：
     * 1. 环境自检与自愈 (Node.js & pnpm)
     * 2. 写入 forge-game.json、package.json、tsconfig.json、copyFiles.js、game/build.js
     * 3. 写入 build/index.html、build/index.js、build/config.js、src/Main.ts
     * 4. 调度执行 pnpm install
     * 5. 调度执行 node copyFiles.js
     * 6. 调度执行 node game/build.js
     * 7. 物理断言产物，标记 isActivated = true 并保存！
     */
    suspend fun activateGameProject(
        gameRootDir: File,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int,
        onProgress: (step: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
        onLog: (String) -> Unit
    ): Result<ForgeGameProjectConfig> = withContext(Dispatchers.IO) {
        val pathError = validateGamePath(gameRootDir.absolutePath)
        if (pathError != null) {
            onLog("❌ $pathError")
            return@withContext Result.failure(IllegalArgumentException(pathError))
        }

        if (!gameRootDir.exists()) {
            gameRootDir.mkdirs()
        }

        onLog("🚀 开始激活游戏项目: $projectName")
        onLog("📁 项目目标目录: ${gameRootDir.absolutePath}")

        // Step 1: 环境自检与自愈
        onProgress(1, 6, "检查 Node.js 与 pnpm 运行环境...")
        onLog("\n[步骤 1/6] 检查 Node.js 与 pnpm 运行环境...")
        val envReady = GameEnvironmentManager.ensureEnvironment(onLog)
        if (!envReady) {
            val msg = "运行环境自愈未通过，无法继续构建"
            onLog("❌ $msg")
            return@withContext Result.failure(IllegalStateException(msg))
        }

        // Step 2 & 3: 写入脚手架与嵌入式模板
        onProgress(2, 6, "写入游戏脚手架、配置文件与嵌入式模板...")
        onLog("\n[步骤 2/6] 写入游戏脚手架、配置文件与嵌入式模板...")
        try {
            writeScaffoldFiles(gameRootDir, projectName, canvasWidth, canvasHeight)
            onLog("✅ 脚手架核心文件与源码模板写入完成")
        } catch (e: Exception) {
            val msg = "写入脚手架文件失败: ${e.message}"
            onLog("❌ $msg")
            return@withContext Result.failure(e)
        }

        // Step 4: 执行 pnpm install
        onProgress(3, 6, "执行 pnpm install (安装 game-lib 依赖)...")
        onLog("\n[步骤 3/6] 执行 pnpm install (安装 game-lib 依赖)...")
        val pnpmInstallCmd = if (hostOs.isWindows) {
            listOf("cmd", "/c", "pnpm", "install")
        } else {
            listOf("pnpm", "install")
        }
        val (pnpmOk, pnpmMsg) = GameEnvironmentManager.runProcess(
            workDir = gameRootDir,
            command = pnpmInstallCmd,
            timeoutMinutes = 15,
            onLog = onLog
        )
        if (!pnpmOk) {
            val msg = "pnpm install 执行失败"
            onLog("❌ $msg: $pnpmMsg")
            return@withContext Result.failure(IllegalStateException(msg))
        }
        onLog("✅ pnpm install 依赖安装完成")

        // Step 5: 执行 node copyFiles.js
        onProgress(4, 6, "执行 node copyFiles.js (分发核心库与资源)...")
        onLog("\n[步骤 4/6] 执行 node copyFiles.js (分发核心库与资源)...")
        val nodeCopyCmd = if (hostOs.isWindows) {
            listOf("cmd", "/c", "node", "copyFiles.js")
        } else {
            listOf("node", "copyFiles.js")
        }
        val (copyOk, copyMsg) = GameEnvironmentManager.runProcess(
            workDir = gameRootDir,
            command = nodeCopyCmd,
            timeoutMinutes = 5,
            onLog = onLog
        )
        if (!copyOk) {
            val msg = "node copyFiles.js 执行失败"
            onLog("❌ $msg: $copyMsg")
            return@withContext Result.failure(IllegalStateException(msg))
        }
        onLog("✅ copyFiles.js 静态文件分发完成")

        // Step 6: 执行 node game/build.js
        onProgress(5, 6, "执行 node game/build.js (打包 TypeScript 源码)...")
        onLog("\n[步骤 5/6] 执行 node game/build.js (打包 TypeScript 源码)...")
        val nodeBuildCmd = if (hostOs.isWindows) {
            listOf("cmd", "/c", "node", "game/build.js")
        } else {
            listOf("node", "game/build.js")
        }
        val (buildOk, buildMsg) = GameEnvironmentManager.runProcess(
            workDir = gameRootDir,
            command = nodeBuildCmd,
            timeoutMinutes = 10,
            onLog = onLog
        )
        if (!buildOk) {
            val msg = "node game/build.js 编译打包失败"
            onLog("❌ $msg: $buildMsg")
            return@withContext Result.failure(IllegalStateException(msg))
        }
        onLog("✅ TypeScript 打包完成，产物已输出至 build/main.min.js")

        // Step 7: 物理断言产物
        onProgress(6, 6, "物理校验运行目录产物与入口文件...")
        onLog("\n[步骤 6/6] 物理校验运行目录产物与入口文件...")
        val runDir = File(gameRootDir, "build")
        val indexHtml = File(runDir, "index.html")
        val indexJs = File(runDir, "index.js")
        val mainBundle = File(runDir, "main.min.js")

        if (!indexHtml.exists() || !indexJs.exists() || !mainBundle.exists()) {
            val missing = buildList {
                if (!indexHtml.exists()) add("build/index.html")
                if (!indexJs.exists()) add("build/index.js")
                if (!mainBundle.exists()) add("build/main.min.js")
            }.joinToString(", ")
            val msg = "构建完成但关键产物缺失: $missing"
            onLog("❌ $msg")
            return@withContext Result.failure(IllegalStateException(msg))
        }

        // 更新识别配置并落盘
        val finalConfig = ForgeGameProjectConfig(
            forgeGameVersion = "1.0.0",
            gameProjectName = projectName,
            gameRootPath = gameRootDir.absolutePath.replace("\\", "/"),
            runDir = "build",
            sourceDir = "game/src",
            entryHtml = "index.html",
            entryJs = "index.js",
            mainBundle = "build/main.min.js",
            isActivated = true,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val configFile = File(gameRootDir, FORGE_GAME_CONFIG_FILE)
        configFile.writeText(looseJson.encodeToString(finalConfig), Charsets.UTF_8)

        onLog("🎉 游戏项目 [$projectName] 激活成功！全部构建产物已就绪！")
        Result.success(finalConfig)
    }

    /**
     * 安全读取 resources/templates/game_scaffold/ 目录下的模板文件
     */
    private fun loadTemplate(relativePath: String): String {
        val resourcePath = "/templates/game_scaffold/$relativePath"
        val stream = GameScaffoldService::class.java.getResourceAsStream(resourcePath)
            ?: error("脚手架模板资源缺失: $resourcePath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * 安全拷贝 resources/templates/game_scaffold/ 目录下的二进制/库资源
     */
    private fun copyTemplateBinary(relativePath: String, targetFile: File) {
        val resourcePath = "/templates/game_scaffold/$relativePath"
        val stream = GameScaffoldService::class.java.getResourceAsStream(resourcePath)
            ?: error("脚手架二进制资源缺失: $resourcePath")
        targetFile.parentFile?.mkdirs()
        stream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * 写入全套脚手架文件与嵌入式模板（从资源目录动态读取与变量注入）
     */
    private fun writeScaffoldFiles(
        gameRootDir: File,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int
    ) {
        val gameDir = File(gameRootDir, "game").apply { mkdirs() }
        val gameSrcDir = File(gameDir, "src").apply { mkdirs() }
        val gameSrcUiDir = File(gameSrcDir, "ui").apply { mkdirs() }
        val buildDir = File(gameRootDir, "build").apply { mkdirs() }

        // 1. package.json
        val packageJson = loadTemplate("package.json.tpl")
            .replace("{{PROJECT_NAME}}", projectName)
        File(gameRootDir, "package.json").writeText(packageJson, Charsets.UTF_8)

        // 2. tsconfig.json
        val tsconfigJson = loadTemplate("tsconfig.json.tpl")
        File(gameRootDir, "tsconfig.json").writeText(tsconfigJson, Charsets.UTF_8)

        // 3. copyFiles.js
        val copyFilesJs = loadTemplate("copyFiles.js.tpl")
        File(gameRootDir, "copyFiles.js").writeText(copyFilesJs, Charsets.UTF_8)

        // 4. game/build.js
        val buildJs = loadTemplate("game/build.js.tpl")
        File(gameDir, "build.js").writeText(buildJs, Charsets.UTF_8)

        // 5. game/src/Main.ts
        val mainTs = loadTemplate("game/src/Main.ts.tpl")
            .replace("{{CANVAS_WIDTH}}", canvasWidth.toString())
            .replace("{{CANVAS_HEIGHT}}", canvasHeight.toString())
        File(gameSrcDir, "Main.ts").writeText(mainTs, Charsets.UTF_8)

        // 6. game/src/ui/layoutConfig.ts
        val layoutConfigTs = loadTemplate("game/src/ui/layoutConfig.ts.tpl")
            .replace("{{CANVAS_WIDTH}}", canvasWidth.toString())
            .replace("{{CANVAS_HEIGHT}}", canvasHeight.toString())
        File(gameSrcUiDir, "layoutConfig.ts").writeText(layoutConfigTs, Charsets.UTF_8)

        // 7. game/libs/common.d.ts (类型定义)
        copyTemplateBinary("game/libs/common.d.ts", File(gameDir, "libs/common.d.ts"))

        // 8. build/index.html
        val indexHtml = loadTemplate("build/index.html.tpl")
            .replace("{{PROJECT_NAME}}", projectName)
        File(buildDir, "index.html").writeText(indexHtml, Charsets.UTF_8)

        // 9. build/config.js
        val configJs = loadTemplate("build/config.js.tpl")
            .replace("{{CANVAS_WIDTH}}", canvasWidth.toString())
            .replace("{{CANVAS_HEIGHT}}", canvasHeight.toString())
        File(buildDir, "config.js").writeText(configJs, Charsets.UTF_8)

        // 10. build/index.js (动态脚本批加载流水线)
        val indexJs = loadTemplate("build/index.js.tpl")
        File(buildDir, "index.js").writeText(indexJs, Charsets.UTF_8)

        // 11. build/common.min.js (通用业务基础库)
        copyTemplateBinary("build/common.min.js", File(buildDir, "common.min.js"))
    }
}
