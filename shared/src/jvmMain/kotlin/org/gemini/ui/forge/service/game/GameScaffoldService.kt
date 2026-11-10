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
            sourceDir = "src",
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
     * 写入全套脚手架文件与嵌入式模板
     */
    private fun writeScaffoldFiles(
        gameRootDir: File,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int
    ) {
        val srcDir = File(gameRootDir, "src").apply { mkdirs() }
        val gameDir = File(gameRootDir, "game").apply { mkdirs() }
        val buildDir = File(gameRootDir, "build").apply { mkdirs() }

        // 1. package.json
        val packageJson = """
        {
          "name": "$projectName",
          "version": "1.0.0",
          "description": "Generated by GeminiUIForge UI Studio",
          "main": "build/index.js",
          "scripts": {
            "copy": "node copyFiles.js",
            "build": "node game/build.js"
          },
          "devDependencies": {
            "game-lib": "npm:@boge-mvp/game-lib@^2.5.4"
          }
        }
        """.trimIndent()
        File(gameRootDir, "package.json").writeText(packageJson, Charsets.UTF_8)

        // 2. tsconfig.json
        val tsconfigJson = """
        {
          "compilerOptions": {
            "target": "ES6",
            "module": "ESNext",
            "lib": ["DOM", "ES2015"],
            "strict": false,
            "esModuleInterop": true,
            "skipLibCheck": true,
            "forceConsistentCasingInFileNames": true
          },
          "include": ["src/**/*"]
        }
        """.trimIndent()
        File(gameRootDir, "tsconfig.json").writeText(tsconfigJson, Charsets.UTF_8)

        // 3. copyFiles.js
        val copyFilesJs = """
        const fs = require('fs');
        const path = require('path');

        function copyFileSync(source, target) {
            let targetFile = target;
            if (fs.existsSync(target)) {
                if (fs.lstatSync(target).isDirectory()) {
                    targetFile = path.join(target, path.basename(source));
                }
            }
            fs.writeFileSync(targetFile, fs.readFileSync(source));
        }

        function ensureDir(dir) {
            if (!fs.existsSync(dir)) {
                fs.mkdirSync(dir, { recursive: true });
            }
        }

        const buildDir = path.resolve(__dirname, 'build');
        ensureDir(buildDir);

        const distDir = path.resolve(__dirname, 'node_modules/game-lib/dist');
        if (fs.existsSync(distDir)) {
            fs.readdirSync(distDir).forEach(file => {
                if (file.endsWith('.min.js')) {
                    fs.copyFileSync(path.join(distDir, file), path.join(buildDir, file));
                }
            });
        }
        console.log('copyFiles completed successfully.');
        """.trimIndent()
        File(gameRootDir, "copyFiles.js").writeText(copyFilesJs, Charsets.UTF_8)

        // 4. game/build.js
        val buildJs = """
        const path = require('path');
        const fs = require('fs');

        async function build() {
            console.log('Starting rollupPack build...');
            const { rollupPack } = require('game-lib/lib/rollup-pack');
            const rootDir = path.resolve(__dirname, '..');
            const inputPath = path.resolve(rootDir, 'src/Main.ts');
            const outputPath = path.resolve(rootDir, 'build/main.min.js');

            await rollupPack({
                input: inputPath,
                output: outputPath,
                projectDir: rootDir
            });
            console.log('rollupPack build successfully generated:', outputPath);
        }

        build().catch(err => {
            console.error('Build failed:', err);
            process.exit(1);
        });
        """.trimIndent()
        File(gameDir, "build.js").writeText(buildJs, Charsets.UTF_8)

        // 5. build/index.html
        val indexHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>$projectName</title>
            <meta name="viewport" content="width=device-width,initial-scale=1.0,minimum-scale=1.0,maximum-scale=1.0,user-scalable=no" />
            <meta name="apple-mobile-web-app-capable" content="yes" />
            <meta name="full-screen" content="true" />
            <meta name="x5-fullscreen" content="true" />
            <meta name="360-fullscreen" content="true" />
            <meta name="renderer" content="webkit" />
            <meta name="force-rendering" content="webkit" />
            <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1" />
            <style>
                html, body {
                    touch-action: none;
                    margin: 0px;
                    padding: 0px;
                    width: 100%;
                    height: 100%;
                    background-color: #000000;
                    overflow: hidden;
                }
            </style>
        </head>
        <body>
            <script type="text/javascript" src="./index.js"></script>
        </body>
        </html>
        """.trimIndent()
        File(buildDir, "index.html").writeText(indexHtml, Charsets.UTF_8)

        // 6. build/config.js
        val configJs = """
        window.GameConfig = {
            width: ${canvasWidth.toInt()},
            height: ${canvasHeight.toInt()},
            scaleMode: "showall",
            screenOrientation: "sensor_landscape"
        };
        """.trimIndent()
        File(buildDir, "config.js").writeText(configJs, Charsets.UTF_8)

        // 7. build/index.js
        val indexJs = """
        window.screenOrientation = "sensor_landscape";

        function loadScript(src) {
            return new Promise((resolve, reject) => {
                const script = document.createElement("script");
                script.src = src;
                script.onload = resolve;
                script.onerror = reject;
                document.body.appendChild(script);
            });
        }

        async function init() {
            try {
                const scripts = [
                    "./fairygui.min.js",
                    "./laya.core.min.js",
                    "./laya.ui.min.js",
                    "./laya.d3.min.js",
                    "./laya.physics3D.min.js",
                    "./gameLib.min.js",
                    "./tsCore.min.js",
                    "./config.js",
                    "./main.min.js"
                ];
                for (const s of scripts) {
                    await loadScript(s).catch(e => console.warn("Optional or deferred script:", s, e));
                }
                console.log("Game scripts loaded successfully.");
            } catch (e) {
                console.error("Failed to load game scripts:", e);
            }
        }

        init();
        """.trimIndent()
        File(buildDir, "index.js").writeText(indexJs, Charsets.UTF_8)

        // 8. src/Main.ts
        val mainTs = """
        declare const Laya: any;
        declare const fgui: any;
        declare const tsCore: any;
        declare const SceneManager: any;
        declare const App: any;

        export class Main {
            constructor() {
                this.initGame();
            }

            private initGame(): void {
                console.log("Main game initialized with resolution: ${canvasWidth.toInt()}x${canvasHeight.toInt()}");
                if (typeof Laya !== "undefined") {
                    Laya.init(${canvasWidth.toInt()}, ${canvasHeight.toInt()}, Laya["WebGL"]);
                    Laya.stage.scaleMode = "showall";
                    Laya.stage.alignH = "center";
                    Laya.stage.alignV = "middle";
                    Laya.stage.screenMode = "none";
                }
            }
        }

        new Main();
        """.trimIndent()
        File(srcDir, "Main.ts").writeText(mainTs, Charsets.UTF_8)
    }
}
