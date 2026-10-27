package org.gemini.ui.forge.model.ui

/**
 * 资产状态配置信息数据类
 *
 * @property name 状态友好显示名称（如"默认状态"、"按下状态"、"禁用状态"）
 * @property historicalIdSuffix 生成资产历史库与报文归档时使用的路径标识后缀
 */
data class AssetState(
    val name: String,
    val historicalIdSuffix: String
)
