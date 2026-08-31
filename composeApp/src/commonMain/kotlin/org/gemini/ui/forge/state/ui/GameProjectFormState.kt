package org.gemini.ui.forge.state.ui

import org.gemini.ui.forge.model.gameproject.GameLanguage
import org.gemini.ui.forge.model.gameproject.GitCredentialMode
import org.gemini.ui.forge.model.gameproject.PackageManagerMode

/**
 * 游戏项目管理创建向导的表单状态。
 * 由 [org.gemini.ui.forge.viewmodel.GameProjectViewModel] 持有并驱动界面更新。
 */
data class GameProjectFormState(
    /** 游戏项目名字 */
    val name: String = "",
    /** 项目 git 仓库地址 */
    val repoUrl: String = "",
    /** 手动输入的 git 用户名 */
    val gitUserName: String = "",
    /** 手动输入的 git 访问令牌（API Key） */
    val gitToken: String = "",
    /** 是否使用本机探测到的全局 git 凭据 */
    val useGlobalCredential: Boolean = false,
    /** 凭据使用模式：SSH 密钥 / 令牌，决定凭据区交互形态与认证分流（默认 SSH 优先） */
    val credentialMode: GitCredentialMode = GitCredentialMode.SSH_KEY,
    /** SSH 模式下选定的私钥文件路径；为空时使用 git 默认密钥（~/.ssh 自动发现） */
    val sshKeyPath: String = "",
    /** 项目保存地址；为空时使用软件缓存目录下的默认路径 */
    val savePath: String = "",
    /** 游戏开发语言（当前仅 TypeScript，预留扩展） */
    val language: GameLanguage = GameLanguage.TYPESCRIPT,
    /** 包管理模式（npm / pnpm） */
    val packageManager: PackageManagerMode = PackageManagerMode.PNPM
)
