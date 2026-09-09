package org.gemini.ui.forge.model.gameproject

/**
 * Git 凭据来源类型枚举。
 * 用于区分用户手动输入的令牌与从本机全局 Git 配置中自动探测到的凭据。
 */
enum class GitCredentialSource {
    /** 用户在表单中手动输入的账户与 API Key */
    MANUAL,

    /** 本机全局 git 配置中的 credential helper（git config --global） */
    GLOBAL_CONFIG,

    /** 本机 ~/.git-credentials 明文凭据文件 */
    GIT_CREDENTIALS_FILE,

    /** 本机 ~/.ssh 目录下存在 SSH 密钥，可直接走 SSH 协议 */
    SSH_KEY
}

/**
 * Git 凭据信息模型。
 * 描述一次连接测试或克隆操作所使用的认证方式与凭据内容。
 */
data class GitCredentialInfo(
    /** 来源类型 */
    val source: GitCredentialSource,
    /** Git 用户名（token 模式必填；本机凭据模式下可为 null） */
    val userName: String? = null,
    /** 访问令牌（API Key），仅在手动输入或从凭据文件解析时存在 */
    val token: String? = null,
    /** 凭据对应的主机地址提示（用于 UI 展示，区分多条本机凭据） */
    val repoHostHint: String? = null,
    /** 凭据数据的存储位置描述：文件型为绝对路径；系统凭据库为其条目标识（如 git:https://github.com） */
    val storageLocation: String? = null,
    /** 存储位置类型，决定 UI 一键定位按钮的打开行为方式 */
    val storageKind: CredentialStorageKind = CredentialStorageKind.NONE,
    /** SSH 认证使用的私钥文件绝对路径；null 表示使用 git 默认密钥 */
    val keyFilePath: String? = null
)

/**
 * 凭据使用模式枚举。
 * 决定凭据区的交互形态与认证分流方式。
 */
enum class GitCredentialMode {
    /** HTTPS 访问令牌（API Key） */
    GIT_TOKEN,

    /** SSH 私钥文件 */
    SSH_KEY
}

/**
 * 凭据存储位置类型枚举。
 * 决定凭据详情中一键定位按钮的打开行为方式。
 */
enum class CredentialStorageKind {
    /** 无可定位的存储位置 */
    NONE,

    /** 存储位置为本地文件路径，走系统资源管理器高亮打开 */
    FILE,

    /** 系统级凭据库（Windows 凭据管理器 / macOS 钥匙串等），打开对应管理界面 */
    SYSTEM_STORE
}
