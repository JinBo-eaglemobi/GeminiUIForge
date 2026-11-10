package org.gemini.ui.forge.service.game

import org.gemini.ui.forge.model.gameproject.ForgeGameProjectConfig

class AndroidGameScaffoldBridge : GameScaffoldBridge {
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
    ): Result<ForgeGameProjectConfig> = Result.failure(UnsupportedOperationException("Android 不支持直接构建游戏脚手架"))
    override fun getPreviewUrl(gameRootDirPath: String): String? = null
}

actual fun createGameScaffoldBridge(): GameScaffoldBridge = AndroidGameScaffoldBridge()
