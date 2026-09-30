package org.gemini.ui.forge.model.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 本地 Python pip list 解析出的原始 JSON 映射实体
 *
 * @property name Python 包名称
 * @property version 当前安装的本地版本号
 * @property latestVersion 远程源最新版本号（若未探测则为 null）
 */
@Serializable
data class PipPackageJson(
    val name: String,
    val version: String,
    @SerialName("latest_version") val latestVersion: String? = null
)

/**
 * Python 依赖包状态与应用市场展示模型
 *
 * @property name 依赖包名称（如 rembg, torch, pillow 等）
 * @property version 当前安装的本地版本
 * @property latestVersion PyPI 官方最新发布版本
 * @property isInstalled 是否已在当前 Python 虚拟环境中成功安装
 * @property isRecommended 是否属于 GeminiUIForge 项目官方推荐/必需的核心依赖
 * @property description 依赖包用途概述与中文简短说明
 * @property projectUrl 依赖包在 PyPI 或 GitHub 的主页链接
 */
data class PipPackageInfo(
    val name: String,
    val version: String? = null,
    val latestVersion: String? = null,
    val isInstalled: Boolean = true,
    val isRecommended: Boolean = false,
    val description: String = "",
    val projectUrl: String? = null
) {
    /** 是否存在可用新版本（已安装且最新版本号与本地版本不一致） */
    val isOutdated: Boolean
        get() = isInstalled && latestVersion != null && version != latestVersion
}
