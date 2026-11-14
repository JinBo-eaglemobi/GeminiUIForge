package org.gemini.ui.forge.service.game

import org.gemini.ui.forge.model.gameproject.ForgeGameProjectConfig

class JsGameScaffoldBridge : GameScaffoldBridge {
    override fun validateGamePath(pathStr: String?): String? = null
    override fun readForgeGameConfig(gameRootDirPath: String): ForgeGameProjectConfig? = null
    override fun isGameProjectActivated(gameRootDirPath: String): Boolean = false
    override fun inspectGameProject(gameRootDirPath: String): org.gemini.ui.forge.model.gameproject.GameProjectInspection =
        org.gemini.ui.forge.model.gameproject.GameProjectInspection(
            targetPath = gameRootDirPath,
            kind = org.gemini.ui.forge.model.gameproject.GameProjectKind.EMPTY_OR_NEW
        )
    override suspend fun bindExistingGameProject(
        gameRootDirPath: String,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int
    ): Result<ForgeGameProjectConfig> = Result.failure(UnsupportedOperationException("Web 平台不支持直接构建游戏脚手架"))
    override suspend fun activateGameProject(
        gameRootDirPath: String,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int,
        onProgress: (step: Int, total: Int, message: String) -> Unit,
        onLog: (String) -> Unit
    ): Result<ForgeGameProjectConfig> = Result.failure(UnsupportedOperationException("Web 平台不支持直接构建游戏脚手架"))
    override fun getPreviewUrl(gameRootDirPath: String): String? = null
    override suspend fun exportCurrentPageToGame(
        gameRootDirPath: String,
        page: org.gemini.ui.forge.model.ui.UIStudioPage,
        assetLibrary: org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary?,
        onLog: (String) -> Unit
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Web 平台不支持直接构建游戏脚手架"))
}

actual fun createGameScaffoldBridge(): GameScaffoldBridge = JsGameScaffoldBridge()
