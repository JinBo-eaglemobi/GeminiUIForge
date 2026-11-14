package org.gemini.ui.forge.model.gameproject

import kotlinx.serialization.Serializable

/**
 * 目标游戏目录的智能探测类别。
 */
@Serializable
enum class GameProjectKind {
    /** 已存在合法的 forge-game.json 且已被完全激活过 */
    ALREADY_ACTIVATED,

    /** 完整成熟的已有游戏工程（具备 package.json、源码及完整的运行产物，可直接无损关联运行） */
    COMPLETE_PROJECT,

    /** 已有源码工程（具备源码或 package.json，但尚未安装依赖或缺少构建产物） */
    PARTIAL_SOURCE_PROJECT,

    /** 空白目录或尚未初始化的全新目录 */
    EMPTY_OR_NEW
}
