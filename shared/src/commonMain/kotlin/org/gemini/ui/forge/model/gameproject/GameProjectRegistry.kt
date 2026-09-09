package org.gemini.ui.forge.model.gameproject

import kotlinx.serialization.Serializable

/**
 * 已纳管游戏项目的注册表（持久化根数据）。
 * 存储于应用数据根目录的 gameProjects/registry.json。
 */
@Serializable
data class GameProjectRegistry(
    /** 全部已纳管项目列表 */
    val projects: List<GameProjectInfo> = emptyList()
)
