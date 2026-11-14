package org.gemini.ui.forge.service.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.gemini.ui.forge.model.gameproject.ForgeGameProjectConfig
import org.gemini.ui.forge.model.gameproject.GameProjectInspection
import org.gemini.ui.forge.model.gameproject.GameProjectKind
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
     * 智能探测目标目录的游戏工程多维特征。
     * 识别是否为已激活工程、完整成熟已有工程、半成品源码工程或空白目录。
     */
    fun inspectGameProject(gameRootDir: File): GameProjectInspection {
        val pathStr = gameRootDir.absolutePath.replace("\\", "/")
        if (!gameRootDir.exists() || !gameRootDir.isDirectory) {
            return GameProjectInspection(
                targetPath = pathStr,
                kind = GameProjectKind.EMPTY_OR_NEW,
                summaryText = "目标目录尚不存在或为空，将全新创建游戏脚手架工程。"
            )
        }

        if (isGameProjectActivated(gameRootDir)) {
            val cfg = readForgeGameConfig(gameRootDir)
            return GameProjectInspection(
                targetPath = pathStr,
                kind = GameProjectKind.ALREADY_ACTIVATED,
                hasPackageJson = File(gameRootDir, "package.json").exists(),
                hasSourceDir = File(gameRootDir, "game/src").isDirectory || File(gameRootDir, "src").isDirectory,
                hasBuildArtifacts = true,
                hasNodeModules = File(gameRootDir, "node_modules").isDirectory,
                detectedName = cfg?.gameProjectName,
                summaryText = "当前工程已完成激活并生成完整运行产物，可直接运行预览！"
            )
        }

        val pkgFile = File(gameRootDir, "package.json")
        val hasPackageJson = pkgFile.exists() && pkgFile.isFile
        var detectedName: String? = null
        if (hasPackageJson) {
            try {
                val pkgContent = pkgFile.readText(Charsets.UTF_8)
                val match = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(pkgContent)
                detectedName = match?.groupValues?.get(1)
            } catch (_: Exception) {
            }
        }

        val hasSourceDir = File(gameRootDir, "game/src").isDirectory || File(gameRootDir, "src").isDirectory
        val runDir = File(gameRootDir, "build")
        val hasIndexHtml = File(runDir, "index.html").exists()
        val hasEntryJs = File(runDir, "index.js").exists() || File(runDir, "main.min.js").exists() || File(runDir, "main.js").exists()
        val hasBuildArtifacts = hasIndexHtml && hasEntryJs
        val hasNodeModules = File(gameRootDir, "node_modules").isDirectory

        val kind = if (hasPackageJson && hasBuildArtifacts && (hasSourceDir || hasNodeModules)) {
            GameProjectKind.COMPLETE_PROJECT
        } else if (hasPackageJson || hasSourceDir) {
            GameProjectKind.PARTIAL_SOURCE_PROJECT
        } else {
            GameProjectKind.EMPTY_OR_NEW
        }

        val summary = when (kind) {
            GameProjectKind.COMPLETE_PROJECT ->
                "检测到完整成熟游戏工程（具备 package.json、源码与运行产物）。将保留所有既有代码与依赖，无需重复初始化，可直接秒级关联！"
            GameProjectKind.PARTIAL_SOURCE_PROJECT ->
                "检测到已有游戏源码工程。将保护现有代码与配置，仅按需安装依赖并生成运行产物。"
            GameProjectKind.EMPTY_OR_NEW ->
                "目标目录为空或全新工程，将创建全套游戏脚手架并执行初次构建。"
            GameProjectKind.ALREADY_ACTIVATED ->
                "当前工程已激活，可直接运行！"
        }

        return GameProjectInspection(
            targetPath = pathStr,
            kind = kind,
            hasPackageJson = hasPackageJson,
            hasSourceDir = hasSourceDir,
            hasBuildArtifacts = hasBuildArtifacts,
            hasNodeModules = hasNodeModules,
            detectedName = detectedName,
            summaryText = summary
        )
    }

    /**
     * 将目标成熟游戏工程直接无损关联接入（绝对保护现有代码与依赖，无需重复初始化）。
     * 仅在根目录写入/同步 forge-game.json，毫秒级就绪。
     */
    suspend fun bindExistingGameProject(
        gameRootDir: File,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int
    ): Result<ForgeGameProjectConfig> = withContext(Dispatchers.IO) {
        val pathError = validateGamePath(gameRootDir.absolutePath)
        if (pathError != null) {
            return@withContext Result.failure(IllegalArgumentException(pathError))
        }

        val runDir = "build"
        val entryHtml = "index.html"
        val entryJs = if (File(gameRootDir, "build/index.js").exists()) "index.js" else "main.min.js"
        val sourceDir = if (File(gameRootDir, "game/src").exists()) "game/src" else "src"
        val mainBundle = if (File(gameRootDir, "build/main.min.js").exists()) "build/main.min.js" else "build/index.js"

        val finalConfig = ForgeGameProjectConfig(
            forgeGameVersion = "1.0.0",
            gameProjectName = projectName,
            gameRootPath = gameRootDir.absolutePath.replace("\\", "/"),
            runDir = runDir,
            sourceDir = sourceDir,
            entryHtml = entryHtml,
            entryJs = entryJs,
            mainBundle = mainBundle,
            isActivated = true,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        try {
            val configFile = File(gameRootDir, FORGE_GAME_CONFIG_FILE)
            configFile.writeText(looseJson.encodeToString(finalConfig), Charsets.UTF_8)
            AppLogger.i(TAG, "Successfully bound existing game project at ${gameRootDir.absolutePath}")
            Result.success(finalConfig)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to bind existing game project at ${gameRootDir.absolutePath}", e)
            Result.failure(e)
        }
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
        val nodeModulesDir = File(gameRootDir, "node_modules")
        if (nodeModulesDir.isDirectory && nodeModulesDir.listFiles()?.isNotEmpty() == true) {
            onProgress(3, 6, "检测到已存在 node_modules，跳过重复依赖安装...")
            onLog("\n[步骤 3/6] ✅ 检测到已有 node_modules 目录，跳过重复依赖安装")
        } else {
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
        }

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
     * 写入全套脚手架文件与嵌入式模板（从资源目录动态读取与变量注入）。
     * 铁律：对目标目录下已存在的任何文件执行绝对保护，严禁覆写用户既有代码与配置！
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

        fun safeWrite(file: File, tplName: String, transform: (String) -> String = { it }) {
            if (file.exists()) return // 严禁覆写用户既有文件
            val content = transform(loadTemplate(tplName))
            file.writeText(content, Charsets.UTF_8)
        }

        fun safeCopy(relPath: String, targetFile: File) {
            if (targetFile.exists()) return // 严禁覆写
            copyTemplateBinary(relPath, targetFile)
        }

        // 1. package.json
        safeWrite(File(gameRootDir, "package.json"), "package.json.tpl") {
            it.replace("{{PROJECT_NAME}}", projectName)
        }

        // 2. tsconfig.json
        safeWrite(File(gameRootDir, "tsconfig.json"), "tsconfig.json.tpl")

        // 3. copyFiles.js
        safeWrite(File(gameRootDir, "copyFiles.js"), "copyFiles.js.tpl")

        // 4. game/build.js
        safeWrite(File(gameDir, "build.js"), "game/build.js.tpl")

        // 5. game/src/Main.ts
        safeWrite(File(gameSrcDir, "Main.ts"), "game/src/Main.ts.tpl") {
            it.replace("{{CANVAS_WIDTH}}", canvasWidth.toString())
                .replace("{{CANVAS_HEIGHT}}", canvasHeight.toString())
        }

        // 6. game/src/ui/layoutConfig.ts
        safeWrite(File(gameSrcUiDir, "layoutConfig.ts"), "game/src/ui/layoutConfig.ts.tpl") {
            it.replace("{{CANVAS_WIDTH}}", canvasWidth.toString())
                .replace("{{CANVAS_HEIGHT}}", canvasHeight.toString())
        }

        // 7. game/libs/common.d.ts (类型定义)
        safeCopy("game/libs/common.d.ts", File(gameDir, "libs/common.d.ts"))

        // 8. build/index.html
        safeWrite(File(buildDir, "index.html"), "build/index.html.tpl") {
            it.replace("{{PROJECT_NAME}}", projectName)
        }

        // 9. build/config.js
        safeWrite(File(buildDir, "config.js"), "build/config.js.tpl") {
            it.replace("{{CANVAS_WIDTH}}", canvasWidth.toString())
                .replace("{{CANVAS_HEIGHT}}", canvasHeight.toString())
        }

        // 10. build/index.js (动态脚本批加载流水线)
        safeWrite(File(buildDir, "index.js"), "build/index.js.tpl")

        // 11. build/common.min.js (通用业务基础库)
        safeCopy("build/common.min.js", File(buildDir, "common.min.js"))
    }
}
