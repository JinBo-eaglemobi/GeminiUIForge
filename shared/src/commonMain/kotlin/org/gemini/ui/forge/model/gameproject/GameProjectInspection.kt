package org.gemini.ui.forge.model.gameproject

import kotlinx.serialization.Serializable

/**
 * 目标游戏工程目录的多维特征智能探测结果。
 *
 * @property targetPath 探测的目标物理绝对路径
 * @property kind 工程类型分类
 * @property hasPackageJson 是否存在 package.json
 * @property hasSourceDir 是否存在源码目录（如 game/src 或 src）
 * @property hasBuildArtifacts 是否存在核心运行入口与产物（如 build/index.html、build/index.js、build/main.min.js）
 * @property hasNodeModules 是否已安装依赖 (node_modules)
 * @property detectedName 从既有配置中识别出的工程名称（若有）
 * @property summaryText 针对用户的结构化提示摘要
 */
@Serializable
data class GameProjectInspection(
    val targetPath: String,
    val kind: GameProjectKind,
    val hasPackageJson: Boolean = false,
    val hasSourceDir: Boolean = false,
    val hasBuildArtifacts: Boolean = false,
    val hasNodeModules: Boolean = false,
    val detectedName: String? = null,
    val summaryText: String = ""
)
