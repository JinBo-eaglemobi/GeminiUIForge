package org.gemini.ui.forge.model.gameproject

import kotlinx.serialization.Serializable

/**
 * 一个已纳管的游戏项目完整信息。
 * 持久化到注册表 JSON 中；其中 git 令牌不落 JSON，而是通过 ConfigManager 以
 * "git_token_<id>" 键名单独加密级存储（与现有 API Key 同级保护策略）。
 */
@Serializable
data class GameProjectInfo(
    /** 项目唯一标识（创建时生成） */
    val id: String,
    /** 游戏项目名字（用户填写） */
    val name: String,
    /** git 仓库地址 */
    val repoUrl: String,
    /** 当前管理的游戏名（如 fruitSlots） */
    val selectedGame: String,
    /** 游戏开发语言 */
    val language: GameLanguage,
    /** 包管理模式 */
    val packageManager: PackageManagerMode,
    /** 本地保存的绝对路径 */
    val localPath: String,
    /** git 用户名（token 模式下使用） */
    val gitUserName: String? = null,
    /** 是否已保存 git 令牌（令牌本体存 ConfigManager，此处仅做存在性标记） */
    val hasGitToken: Boolean = false,
    /** 是否使用本机全局 git 凭据 */
    val useGlobalCredential: Boolean = false,
    /** 创建时间戳（毫秒） */
    val createdAt: Long,
    /** 最近一次打开时间戳（毫秒） */
    val lastOpenedAt: Long = createdAt
)
