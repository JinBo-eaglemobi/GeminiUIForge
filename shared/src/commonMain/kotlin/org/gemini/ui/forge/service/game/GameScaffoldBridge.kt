package org.gemini.ui.forge.service.game

import org.gemini.ui.forge.model.gameproject.ForgeGameProjectConfig

/**
 * UI Studio 游戏项目脚手架与激活服务跨平台契约接口。
 */
interface GameScaffoldBridge {

    /** 校验路径合法性（严禁私有缓存目录），返回错误信息（合法则返回 null） */
    fun validateGamePath(pathStr: String?): String?

    /** 读取指定根目录下的 forge-game.json 配置 */
    fun readForgeGameConfig(gameRootDirPath: String): ForgeGameProjectConfig?

    /** 判断项目是否已成功完成初次脚手架与构建激活 */
    fun isGameProjectActivated(gameRootDirPath: String): Boolean

    /** 执行全套环境自愈、脚手架写入、pnpm install 与 rollup 打包流水线 */
    suspend fun activateGameProject(
        gameRootDirPath: String,
        projectName: String,
        canvasWidth: Int,
        canvasHeight: Int,
        onProgress: (step: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
        onLog: (String) -> Unit = {}
    ): Result<ForgeGameProjectConfig>

    /** 获取可用于在浏览器中运行 index.html 的本地服务 URL 地址 */
    fun getPreviewUrl(gameRootDirPath: String): String?
}

expect fun createGameScaffoldBridge(): GameScaffoldBridge
