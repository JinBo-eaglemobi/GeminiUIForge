package org.gemini.ui.forge.model.app

import kotlinx.serialization.Serializable

/**
 * 编译环境配置模型。
 * 包含运行环境根目录、编译脚本路径以及资源保存目录。
 *
 * @property rootDir 编译/运行环境的根目录（通常为 Slots 游戏模板或引擎的物理根路径）
 * @property scriptPath 自定义编译或构建脚本的路径（如特定的 Python 编译或 Shell 构建脚本路径）
 * @property outputDir 相对于 rootDir 目录，导出资源（切图、图片资源等）存放的目录路径（相对路径，例如 "assets"）
 * @property playDir 项目预览/运行（播放）时的自定义运行环境目录。若配置了该路径，在播放预览时将直接在此目录下寻找 `index.html` 并用浏览器打开；否则默认从 rootDir/bin/index.html 中寻找并打开。
 */
@Serializable
data class CompileConfig(
    val rootDir: String = "",
    val scriptPath: String = "",
    val outputDir: String = "",
    val playDir: String = "",
    val obfuscateAssets: Boolean = true
)
