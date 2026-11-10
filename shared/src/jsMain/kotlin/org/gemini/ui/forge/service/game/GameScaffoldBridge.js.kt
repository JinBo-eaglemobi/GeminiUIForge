package org.gemini.ui.forge.service.game

import org.gemini.ui.forge.model.gameproject.ForgeGameProjectConfig

class JsGameScaffoldBridge : GameScaffoldBridge {
    override fun validateGamePath(pathStr: String?): String? = null
    override fun readForgeGameConfig(gameRootDirPath: String): ForgeGameProjectConfig? = null
    override fun isGameProjectActivated(gameRootDirPath: String): Boolean = false
    override suspend fun activateGameProject(
        gameRootDirPath: String,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int,
        onProgress: (step: Int, total: Int, message: String) -> Unit,
        onLog: (String) -> Unit
    ): Result<ForgeGameProjectConfig> = Result.failure(UnsupportedOperationException("Web 平台不支持直接构建游戏脚手架"))
    override fun getPreviewUrl(gameRootDirPath: String): String? = null
}

actual fun createGameScaffoldBridge(): GameScaffoldBridge = JsGameScaffoldBridge()
