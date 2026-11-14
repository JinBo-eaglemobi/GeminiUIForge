package org.gemini.ui.forge.service.game

import org.gemini.ui.forge.model.gameproject.ForgeGameProjectConfig
import org.gemini.ui.forge.model.gameproject.GameProjectInspection
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import java.io.File

/**
 * JVM 平台的 GameScaffoldBridge 实现。
 * 委托至单例 GameScaffoldService。
 */
class JvmGameScaffoldBridge : GameScaffoldBridge {

    override fun validateGamePath(pathStr: String?): String? =
        GameScaffoldService.validateGamePath(pathStr)

    override fun readForgeGameConfig(gameRootDirPath: String): ForgeGameProjectConfig? =
        GameScaffoldService.readForgeGameConfig(File(gameRootDirPath))

    override fun isGameProjectActivated(gameRootDirPath: String): Boolean =
        GameScaffoldService.isGameProjectActivated(File(gameRootDirPath))

    override fun inspectGameProject(gameRootDirPath: String): GameProjectInspection =
        GameScaffoldService.inspectGameProject(File(gameRootDirPath))

    override suspend fun bindExistingGameProject(
        gameRootDirPath: String,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int
    ): Result<ForgeGameProjectConfig> =
        GameScaffoldService.bindExistingGameProject(
            gameRootDir = File(gameRootDirPath),
            projectName = projectName,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )

    override suspend fun activateGameProject(
        gameRootDirPath: String,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int,
        onProgress: (step: Int, total: Int, message: String) -> Unit,
        onLog: (String) -> Unit
    ): Result<ForgeGameProjectConfig> =
        GameScaffoldService.activateGameProject(
            gameRootDir = File(gameRootDirPath),
            projectName = projectName,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            onProgress = onProgress,
            onLog = onLog
        )

    override fun getPreviewUrl(gameRootDirPath: String): String? {
        val rootDir = File(gameRootDirPath)
        val config = GameScaffoldService.readForgeGameConfig(rootDir)
        val runDirName = config?.runDir ?: "build"
        val entryHtmlName = config?.entryHtml ?: "index.html"
        val htmlFile = File(rootDir, "$runDirName/$entryHtmlName")
        if (!htmlFile.exists()) return null

        val absPath = htmlFile.absolutePath.replace("\\", "/")
        val driveMatch = Regex("^([a-zA-Z]):/(.*)$").find(absPath)
        return if (driveMatch != null) {
            val drive = driveMatch.groupValues[1].uppercase()
            val rest = driveMatch.groupValues[2]
            "http://localhost:1234/$drive/$rest"
        } else {
            "http://localhost:1234/${absPath.trimStart('/')}"
        }
    }

    override suspend fun exportCurrentPageToGame(
        gameRootDirPath: String,
        page: UIStudioPage,
        assetLibrary: UIAssetLibrary?,
        onLog: (String) -> Unit
    ): Result<Unit> =
        GameUiExportService.exportPageToGameProject(
            gameRootDir = File(gameRootDirPath),
            page = page,
            assetLibrary = assetLibrary,
            onLog = onLog
        )
}

actual fun createGameScaffoldBridge(): GameScaffoldBridge = JvmGameScaffoldBridge()
