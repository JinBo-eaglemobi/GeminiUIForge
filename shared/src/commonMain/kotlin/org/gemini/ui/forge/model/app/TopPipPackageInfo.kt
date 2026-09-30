package org.gemini.ui.forge.model.app

import kotlinx.serialization.Serializable

/**
 * PyPI 官方热门/高下载量 Python 依赖包统计行项
 *
 * @property project Python 包项目名称
 * @property download_count 官方统计的总下载次数
 */
@Serializable
data class TopPipPackageRow(
    val project: String,
    val download_count: Long
)

/**
 * PyPI 官方热门排行依赖包查询响应数据体
 *
 * @property rows 热门依赖包列表
 */
@Serializable
data class TopPipPackagesResponse(
    val rows: List<TopPipPackageRow>
)