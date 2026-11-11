package org.gemini.ui.forge.model.gameproject

import kotlinx.serialization.Serializable

/**
 * 专属于 GeminiUIForge 生成/纳管的外部独立游戏项目的核心识别配置文件 (forge-game.json)。
 *
 * 缺乏此配置文件或版本不符的游戏项目系统坚决不认。
 *
 * @property forgeGameVersion 配置文件格式版本（当前固定为 "1.0.0"）
 * @property gameProjectName 游戏项目名称
 * @property gameRootPath 游戏项目的本地绝对根路径
 * @property runDir 测试运行目录（默认 "build"）
 * @property sourceDir 源码目录（默认 "game/src"）
 * @property entryHtml 入口 HTML 文件名（默认 "index.html"）
 * @property entryJs 启动引导脚本文件名（默认 "index.js"）
 * @property mainBundle 构建输出的主 JS bundle 文件相对路径（默认 "build/main.min.js"）
 * @property isActivated 游戏项目是否已成功完成初次脚手架与构建激活
 * @property canvasWidth 游戏设计基准宽度（从 UI Studio 继承）
 * @property canvasHeight 游戏设计基准高度（从 UI Studio 继承）
 * @property createdAt 项目创建时间戳（毫秒）
 * @property updatedAt 项目最后修改时间戳（毫秒）
 */
@Serializable
data class ForgeGameProjectConfig(
    val forgeGameVersion: String = "1.0.0",
    val gameProjectName: String,
    val gameRootPath: String,
    val runDir: String = "build",
    val sourceDir: String = "game/src",
    val entryHtml: String = "index.html",
    val entryJs: String = "index.js",
    val mainBundle: String = "build/main.min.js",
    val isActivated: Boolean = false,
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 1920,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
