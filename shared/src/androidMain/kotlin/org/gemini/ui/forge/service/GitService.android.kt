package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.gameproject.GitCredentialInfo

actual fun createGitService(): GitService = object : GitService {
    override suspend fun detectGlobalCredential(): GitCredentialInfo? = null
    override suspend fun testConnection(repoUrl: String, credential: GitCredentialInfo): String? = "Android 暂不支持 Git 操作"
    override suspend fun initAndBloblessFetch(
        targetDir: String,
        repoUrl: String,
        credential: GitCredentialInfo?,
        onLog: (String) -> Unit
    ): String? = "Android 暂不支持 Git 操作"
    override suspend fun listRemoteBranches(targetDir: String): List<String> = emptyList()
    override suspend fun listRemoteTags(targetDir: String): List<String> = emptyList()
    override suspend fun listRecentCommits(targetDir: String): List<CommitSummary> = emptyList()
    override suspend fun checkoutRef(
        targetDir: String,
        refType: CloneRefType,
        ref: String?,
        onLog: (String) -> Unit
    ): String? = "Android 暂不支持 Git 操作"
    override suspend fun scanGames(targetDir: String): List<String> = emptyList()
    override suspend fun listAllTreePaths(targetDir: String): List<String> = emptyList()
    override suspend fun sparseCheckout(targetDir: String, paths: List<String>, onLog: (String) -> Unit): String? = "Android 暂不支持 Git 操作"
    override suspend fun pullUpdates(targetDir: String, credential: GitCredentialInfo?, onLog: (String) -> Unit): String? = "Android 暂不支持 Git 操作"
}
