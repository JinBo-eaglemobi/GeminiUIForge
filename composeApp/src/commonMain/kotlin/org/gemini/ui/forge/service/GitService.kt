package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.gameproject.GitCredentialInfo

/**
 * Git 操作服务接口。
 * 桌面端通过系统 git CLI（ProcessBuilder）实现按需拉取（partial clone + sparse-checkout）。
 */
interface GitService {

    /**
     * 探测本机全局 git 凭据信息。
     * 检查顺序：全局 credential 配置 → ~/.git-credentials 明文凭据 → ~/.ssh 密钥。
     * @return 探测到的凭据信息；完全未探测到可用凭据时返回 null
     */
    suspend fun detectGlobalCredential(): GitCredentialInfo?

    /**
     * 探测本机全部可用 git 凭据（多凭据列表）。
     * 收集三类来源：全局配置助手中的真实令牌、凭据文件中的每一条明文凭据、
     * ~/.ssh 下的 SSH 私钥文件（以文件路径形式提供，供 SSH 模式选用）。
     * 默认实现基于单凭据探测结果包装为列表，各平台可按需覆写以收集多个凭据。
     * @return 探测到的凭据列表；未探测到任何可用凭据时返回空列表
     */
    suspend fun detectLocalCredentials(): List<GitCredentialInfo> =
        detectGlobalCredential()?.let { listOf(it) } ?: emptyList()

    /**
     * 测试仓库连通性（git ls-remote）。
     * @param repoUrl 仓库地址
     * @param credential 选用的凭据（手动输入或本机凭据）
     * @return 成功返回 null；失败返回错误描述
     */
    suspend fun testConnection(repoUrl: String, credential: GitCredentialInfo): String?

    /**
     * 在目标目录初始化仓库并执行 blobless 拉取（仅拉取提交与目录树，附带全部引用与标签）。
     * 不做版本检出：检出由 [checkoutRef] 在用户选定版本后执行。
     * 执行过程通过 onLog 回调输出原始日志。
     * @return 成功返回 null；失败返回错误描述
     */
    suspend fun initAndBloblessFetch(
        targetDir: String,
        repoUrl: String,
        credential: GitCredentialInfo?,
        onLog: (String) -> Unit
    ): String?

    /**
     * 列出远端分支名清单（已剔除 origin/ 前缀与 HEAD，按名称排序，数量受上限约束）。
     * 需在 initAndBloblessFetch 成功后调用（数据来自本地引用库，无网络请求）。
     */
    suspend fun listRemoteBranches(targetDir: String): List<String>

    /**
     * 列出远端标签名清单（按创建时间倒序，数量受上限约束）。
     */
    suspend fun listRemoteTags(targetDir: String): List<String>

    /**
     * 列出最近提交摘要（默认分支历史，仅取最近若干条，防止超大仓库卡顿）。
     */
    suspend fun listRecentCommits(targetDir: String): List<CommitSummary>

    /**
     * 依据 refType 把 HEAD 指向目标版本：分支（远端引用）/ 标签 / 提交哈希。
     * 执行过程通过 onLog 回调输出原始日志。
     * @param ref 版本值（分支名 / 标签名 / 完整或短提交哈希）；为 null 时使用远端默认分支（仅 BRANCH 类型生效）
     * @return 成功返回 null；失败返回错误描述
     */
    suspend fun checkoutRef(
        targetDir: String,
        refType: CloneRefType,
        ref: String?,
        onLog: (String) -> Unit
    ): String?

    /**
     * 列出仓库中 game/src/ 目录下的全部游戏文件夹名（不递归，排除 main）。
     * 需在 initAndBloblessFetch 成功后调用。
     */
    suspend fun scanGames(targetDir: String): List<String>

    /**
     * 列出仓库 HEAD 的全部文件路径（用于大小写不敏感的真实路径匹配）。
     */
    suspend fun listAllTreePaths(targetDir: String): List<String>

    /**
     * 读取仓库中指定文件在 HEAD 检出版本下的文本内容。
     * 主要用于克隆过滤配置（.downloadignore）的读取：
     * blobless 拉取模式下文件内容按需从远端获取（配置文件极小，开销可忽略）。
     * @param targetDir 本地仓库目录（绝对路径）
     * @param filePath 仓库相对路径（真实大小写）
     * @return 文件文本内容；读取失败或平台不支持时返回 null
     */
    suspend fun readRepositoryFile(targetDir: String, filePath: String): String? = null

    /**
     * 按路径清单执行 sparse-checkout 下载。
     * @param paths 需要检出的仓库相对路径（目录或文件，已解析为真实大小写）
     * @return 成功返回 null；失败返回错误描述
     */
    suspend fun sparseCheckout(targetDir: String, paths: List<String>, onLog: (String) -> Unit): String?

    /**
     * 在 sparse 范围内拉取远端更新（git pull）。
     * @return 成功返回 null；失败返回错误描述
     */
    suspend fun pullUpdates(targetDir: String, credential: GitCredentialInfo?, onLog: (String) -> Unit): String?
}

/**
 * 跨平台的服务实例化工厂。
 */
expect fun createGitService(): GitService
