package org.gemini.ui.forge.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.gameproject.CredentialStorageKind
import org.gemini.ui.forge.model.gameproject.GitCredentialInfo
import org.gemini.ui.forge.model.gameproject.GitCredentialSource
import org.gemini.ui.forge.utils.AppLogger
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Git 服务的 JVM（桌面端）实现：基于系统 git CLI 与 ProcessBuilder。
 *
 * 采用 blobless partial clone（git fetch --filter=blob:none，只拉提交与目录树）
 * 结合 no-cone sparse-checkout 实现按需拉取，避免下载整个大型游戏仓库。
 *
 * 认证策略：
 * - MANUAL：手动输入的用户名 + 令牌，以 https://user:token@host 形式内嵌到远端地址（不落盘）；
 * - GIT_CREDENTIALS_FILE：解析 ~/.git-credentials 中匹配主机的明文凭据并内嵌；
 * - GLOBAL_CONFIG / SSH_KEY：交由 git 已配置的 credential helper / SSH 自行完成认证。
 */
class JvmGitService : GitService {

    /** 命令执行结果：退出码 + 合并后的标准输出（stdout/stderr） */
    private data class ExecResult(val exitCode: Int, val output: String)

    /** 常见 SSH 私钥文件名集合（~/.ssh 目录扫描用） */
    private val SSH_KEY_FILENAMES = listOf("id_ed25519", "id_rsa", "id_ecdsa")

    /** 静默执行 git 命令并收集全部输出 */
    private fun exec(
        workingDir: File? = null,
        args: List<String>,
        timeoutSec: Long = 600,
        extraEnv: Map<String, String> = emptyMap()
    ): ExecResult {
        return try {
            val process = ProcessBuilder(listOf("git") + args).apply {
                workingDir?.let { directory(it) }
                redirectErrorStream(true)
                environment().putAll(extraEnv)
            }.start()
            // git 管道输出统一按 UTF-8 解码
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            ExecResult(if (finished) process.exitValue() else -1, output)
        } catch (e: Exception) {
            ExecResult(-1, e.message ?: "git 命令执行异常")
        }
    }

    /**
     * 执行任意命令并向标准输入写入指定内容后关闭流（用于 git credential fill 这类
     * 通过 stdin 提交查询参数、从 stdout 返回结果的交互式查询命令）。
     * [args] 首元素为可执行程序名，[extraEnv] 用于注入环境变量强制非交互模式。
     */
    private fun execWithStdin(
        args: List<String>,
        stdinText: String,
        extraEnv: Map<String, String> = emptyMap(),
        timeoutSec: Long = 30
    ): ExecResult {
        return try {
            val process = ProcessBuilder(args).apply {
                redirectErrorStream(true)
                environment().putAll(extraEnv)
            }.start()
            process.outputStream.use { stream ->
                stream.write(stdinText.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            ExecResult(if (finished) process.exitValue() else -1, output)
        } catch (e: Exception) {
            ExecResult(-1, e.message ?: "命令执行异常")
        }
    }

    /** 凭据管理器中一条 git 凭据的主机记录：访问协议 + 主机名 */
    private data class StoredHost(val scheme: String, val host: String)

    /**
     * 枚举系统凭据管理器中已存储的 git 凭据主机列表。
     * Windows 端通过 cmdkey /list 解析（输出形如 Target: LegacyGeneric:target=git:https://github.com）；
     * 非 Windows 平台暂无通用的凭据管理器枚举手段，返回空列表由调用方走占位回退。
     */
    private fun listStoredHosts(): List<StoredHost> {
        val osName = System.getProperty("os.name").lowercase()
        if (!osName.contains("windows")) return emptyList()
        val result = execWithStdin(listOf("cmdkey", "/list"), stdinText = "", timeoutSec = 15)
        if (result.exitCode != 0 && result.output.isBlank()) return emptyList()
        val regex = Regex("""target=git:(https?)://(\S+)""", RegexOption.IGNORE_CASE)
        return result.output.lineSequence()
            .mapNotNull { line -> regex.find(line)?.destructured?.let { (scheme, host) -> StoredHost(scheme.lowercase(), host.trimEnd('/', '.')) } }
            .distinct()
            .toList()
    }

    /**
     * 通过官方机制 git credential fill 从凭据助手（GCM/wincred/store 等）读取已存的真实凭据。
     * 安全约束：注入 GIT_TERMINAL_PROMPT=0 与 GCM_INTERACTIVE=never 强制完全非交互，
     * 既不会有终端提示也不会弹出浏览器/GUI 认证窗口，仅当助手本地已有存储时直接命中返回。
     *
     * @return 成功时为 (用户名, 令牌)；未命中或不含令牌数据时返回 null
     */
    private fun readStoredCredential(scheme: String, host: String): Pair<String?, String>? {
        val result = execWithStdin(
            args = listOf("git", "credential", "fill"),
            stdinText = "protocol=$scheme\nhost=$host\n\n",
            extraEnv = mapOf(
                "GIT_TERMINAL_PROMPT" to "0",
                "GCM_INTERACTIVE" to "never"
            ),
            timeoutSec = 20
        )
        if (result.exitCode != 0 || !result.output.contains("password=")) return null
        var user: String? = null
        var token: String? = null
        result.output.lines().forEach { line ->
            when {
                line.startsWith("username=") -> user = line.substringAfter('=', "").takeIf { it.isNotBlank() }
                line.startsWith("password=") -> token = line.substringAfter('=', "").takeIf { it.isNotBlank() }
            }
        }
        return token?.let { user to it }
    }

    /** 流式执行 git 命令并逐行回调日志（用于 fetch / checkout 等长任务） */
    private fun execStreaming(
        workingDir: File,
        args: List<String>,
        timeoutSec: Long = 3600,
        extraEnv: Map<String, String> = emptyMap(),
        onLog: (String) -> Unit
    ): Int {
        val process = ProcessBuilder(listOf("git") + args).apply {
            directory(workingDir)
            redirectErrorStream(true)
            environment().putAll(extraEnv)
        }.start()
        return try {
            val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                onLog(line!!)
            }
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (finished) process.exitValue() else {
                process.destroyForcibly()
                -1
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            AppLogger.e(TAG, "流式命令执行异常: git ${args.firstOrNull()}", e)
            -1
        }
    }

    override suspend fun detectGlobalCredential(): GitCredentialInfo? = detectLocalCredentials().firstOrNull()

    override suspend fun detectLocalCredentials(): List<GitCredentialInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<GitCredentialInfo>()
        // 1. 凭据助手配置（合并全部 scope 查询：helper 通常配在系统级而非用户级），
        //    并尝试从系统凭据管理器读取已存凭据的实体数据（含真实令牌）逐条纳入候选列表；
        //    仅当配置了助手却未能读取到实体数据时（如非 Windows 或枚举受限），回退保留描述性占位条目
        val helper = exec(null, listOf("config", "--get", "credential.helper"))
        if (helper.exitCode == 0 && helper.output.isNotBlank()) {
            val stored = listStoredHosts().mapNotNull { storedHost ->
                readStoredCredential(storedHost.scheme, storedHost.host)?.let { (user, token) ->
                    GitCredentialInfo(
                        source = GitCredentialSource.GLOBAL_CONFIG,
                        userName = user,
                        token = token,
                        repoHostHint = storedHost.host,
                        storageLocation = "git:${storedHost.scheme}://${storedHost.host}",
                        storageKind = CredentialStorageKind.SYSTEM_STORE
                    )
                }
            }
            if (stored.isNotEmpty()) {
                result += stored
            } else {
                val user = exec(null, listOf("config", "--global", "--get", "user.name"))
                result += GitCredentialInfo(
                    source = GitCredentialSource.GLOBAL_CONFIG,
                    userName = user.output.takeIf { it.isNotBlank() },
                    token = null
                )
            }
        }
        // 2. ~/.git-credentials 明文凭据文件：每行均为一条独立凭据（https://user:token@host），
        //    逐行解析出用户名、令牌与主机，全部纳入候选列表供 UI 选择
        val credFile = File(System.getProperty("user.home"), ".git-credentials")
        if (credFile.exists()) {
            credFile.readLines().forEach { line ->
                if (!line.contains("://")) return@forEach
                // 含特殊字符的凭据行可能导致 URI 解析失败，逐行兜底跳过不影响其余条目
                runCatching {
                    val uri = URI(line.trim())
                    val userInfo = uri.userInfo
                    val entry = GitCredentialInfo(
                        source = GitCredentialSource.GIT_CREDENTIALS_FILE,
                        userName = userInfo?.substringBefore(':')?.takeIf { it.isNotBlank() },
                        token = userInfo?.substringAfter(':')?.takeIf { it.isNotBlank() },
                        repoHostHint = uri.host?.takeIf { it.isNotBlank() },
                        storageLocation = credFile.absolutePath,
                        storageKind = CredentialStorageKind.FILE
                    )
                    // 用户名与主机均为空的行视为无效，跳过
                    if (entry.userName != null || entry.repoHostHint != null) result += entry
                }
            }
        }
        // 3. ~/.ssh 下的私钥文件：以文件路径形式纳入候选（SSH 模式选中后经 GIT_SSH_COMMAND 指定密钥）
        val sshDir = File(System.getProperty("user.home"), ".ssh")
        if (sshDir.isDirectory) {
            sshDir.listFiles { file -> file.isFile && SSH_KEY_FILENAMES.contains(file.name) }
                ?.sortedBy { it.name }
                ?.forEach { keyFile ->
                    result += GitCredentialInfo(
                        source = GitCredentialSource.SSH_KEY,
                        userName = null,
                        token = null,
                        keyFilePath = keyFile.absolutePath,
                        storageLocation = keyFile.absolutePath,
                        storageKind = CredentialStorageKind.FILE
                    )
                }
        }
        result
    }

    override suspend fun testConnection(repoUrl: String, credential: GitCredentialInfo): String? =
        withContext(Dispatchers.IO) {
            val authUrl = authenticatedUrl(repoUrl, credential)
            val result = exec(null, listOf("ls-remote", "--heads", authUrl), timeoutSec = 30, extraEnv = sshEnvOf(credential))
            if (result.exitCode == 0) {
                null
            } else {
                val detail = result.output.ifBlank { "无法访问仓库或认证被拒绝" }
                "连接失败: ${sanitize(detail, credential)}"
            }
        }

    override suspend fun initAndBloblessFetch(
        targetDir: String,
        repoUrl: String,
        credential: GitCredentialInfo?,
        onLog: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val dir = File(targetDir)
        if (!dir.exists() && !dir.mkdirs()) {
            return@withContext "无法创建项目目录: $targetDir"
        }
        val authUrl = authenticatedUrl(repoUrl, credential)

        // 1. 初始化空仓库并登记远端
        val initResult = exec(dir, listOf("init"))
        if (initResult.exitCode != 0) {
            return@withContext "git init 失败: ${sanitize(initResult.output, credential)}"
        }
        var remoteResult = exec(dir, listOf("remote", "add", "origin", authUrl))
        if (remoteResult.exitCode != 0) {
            // 目录已存在旧 .git 且登记过远端（如上次克隆残留）时，自动改写远端地址续用（幂等自愈）
            remoteResult = exec(dir, listOf("remote", "set-url", "origin", authUrl))
            if (remoteResult.exitCode != 0) {
                return@withContext "登记远端失败: ${sanitize(remoteResult.output, credential)}"
            }
            onLog("检测到已登记的远端，已自动改写为当前仓库地址")
        }

        // 2. blobless 拉取（仅提交与目录树，不含文件内容），附带拉取全部引用与标签，流式输出进度
        val fetchCode = execStreaming(dir, listOf("fetch", "--filter=blob:none", "--tags", "--progress", "origin"), extraEnv = sshEnvOf(credential)) { line ->
            onLog(sanitize(line, credential))
        }
        if (fetchCode != 0) {
            return@withContext "拉取远端数据失败 (退出码 $fetchCode)，请检查仓库地址与凭据"
        }

        // 3. 解析远端默认分支符号引用（供版本列表与 checkoutRef 默认分支兜底使用）
        exec(dir, listOf("remote", "set-head", "origin", "-a"))
        AppLogger.i(TAG, "blobless 拉取完成（未检出，等待版本选择）")
        null
    }

    override suspend fun checkoutRef(
        targetDir: String,
        refType: CloneRefType,
        ref: String?,
        onLog: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val dir = File(targetDir)
        // 1. 按版本来源类型解析目标引用（分支 / 标签 / 提交哈希）
        val (targetRef, targetDesc) = when (refType) {
            CloneRefType.BRANCH -> {
                // 未指定分支时解析远端默认分支（origin/HEAD 符号引用），兜底 master
                val branchRef = ref
                    ?: exec(dir, listOf("symbolic-ref", "--short", "refs/remotes/origin/HEAD")).output
                        .takeIf { it.isNotBlank() }
                        ?.removePrefix("origin/")
                    ?: "master"
                "refs/remotes/origin/$branchRef" to "分支 $branchRef"
            }
            CloneRefType.TAG -> "refs/tags/$ref" to "标签 $ref"
            CloneRefType.COMMIT -> ref!! to "提交 $ref"
        }

        // 2. 校验目标引用在本地对象库中存在（全量 fetch 后分支/标签引用与提交对象均已就位）
        val verify = when (refType) {
            CloneRefType.COMMIT -> exec(dir, listOf("rev-parse", "--verify", "$targetRef^{commit}"))
            else -> exec(dir, listOf("rev-parse", "--verify", targetRef))
        }
        if (verify.exitCode != 0) {
            val reason = when (refType) {
                CloneRefType.BRANCH -> "远端分支不存在: $ref"
                CloneRefType.TAG -> "远端标签不存在: $ref"
                CloneRefType.COMMIT -> "提交不存在或不在已拉取的历史中: $ref"
            }
            return@withContext reason
        }

        // 3. 将 HEAD 指向目标引用（仅移动引用，不触发工作区检出）
        val updateRef = exec(dir, listOf("update-ref", "HEAD", targetRef))
        if (updateRef.exitCode != 0) {
            return@withContext "设置当前版本失败: ${updateRef.output}"
        }
        AppLogger.i(TAG, "版本检出完成，目标版本: $targetDesc")
        null
    }

    override suspend fun listRemoteBranches(targetDir: String): List<String> = withContext(Dispatchers.IO) {
        val result = exec(File(targetDir), listOf("for-each-ref", "--format=%(refname:short)", "refs/remotes/origin"))
        if (result.exitCode != 0) {
            AppLogger.w(TAG, "列出远端分支失败: ${result.output}")
            return@withContext emptyList()
        }
        // 剔除 origin/ 前缀与 HEAD 符号引用，按名称排序并截断至上限
        result.output.lines()
            .map { it.removePrefix("origin/").trim() }
            .filter { it.isNotBlank() && it != "HEAD" }
            .sorted()
            .take(MAX_REF_ENTRIES)
    }

    override suspend fun listRemoteTags(targetDir: String): List<String> = withContext(Dispatchers.IO) {
        // 按创建时间倒序（最新标签在前），截断至上限
        val result = exec(File(targetDir), listOf("for-each-ref", "--sort=-creatordate", "--format=%(refname:short)", "refs/tags"))
        if (result.exitCode != 0) {
            AppLogger.w(TAG, "列出远端标签失败: ${result.output}")
            return@withContext emptyList()
        }
        result.output.lines().map { it.trim() }.filter { it.isNotBlank() }.take(MAX_REF_ENTRIES)
    }

    override suspend fun listRecentCommits(targetDir: String): List<CommitSummary> = withContext(Dispatchers.IO) {
        val dir = File(targetDir)
        // 优先读远端默认分支历史；origin/HEAD 缺失时兜底全部引用
        var result = exec(dir, listOf("log", "--max-count=$RECENT_COMMIT_COUNT", "--format=%H|%h|%s", "origin/HEAD"))
        if (result.exitCode != 0) {
            result = exec(dir, listOf("log", "--max-count=$RECENT_COMMIT_COUNT", "--format=%H|%h|%s", "--all"))
        }
        if (result.exitCode != 0) {
            AppLogger.w(TAG, "列出最近提交失败: ${result.output}")
            return@withContext emptyList()
        }
        // 行格式：完整哈希|短哈希|提交说明（limit=3 保证说明内含 | 时不被截断）
        result.output.lines().mapNotNull { line ->
            val parts = line.split('|', limit = 3)
            if (parts.size == 3) CommitSummary(parts[0], parts[1], parts[2]) else null
        }
    }

    override suspend fun scanGames(targetDir: String): List<String> = withContext(Dispatchers.IO) {
        val result = exec(File(targetDir), listOf("ls-tree", "HEAD:game/src"))
        if (result.exitCode != 0) {
            AppLogger.w(TAG, "扫描 game/src 失败: ${result.output}")
            return@withContext emptyList()
        }
        // ls-tree 行格式：<mode> <type> <sha>\t<name>，仅保留 tree 类型（目录）且排除 main
        result.output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("\t", limit = 2)
            val meta = parts.getOrNull(0)?.split(" ") ?: return@mapNotNull null
            val name = parts.getOrNull(1) ?: return@mapNotNull null
            if (meta.getOrNull(1) == "tree" && !name.equals("main", ignoreCase = true)) name else null
        }.sorted()
    }

    override suspend fun listAllTreePaths(targetDir: String): List<String> = withContext(Dispatchers.IO) {
        val result = exec(File(targetDir), listOf("ls-tree", "-r", "--name-only", "HEAD"))
        if (result.exitCode != 0) {
            AppLogger.w(TAG, "列出仓库文件树失败: ${result.output}")
            return@withContext emptyList()
        }
        result.output.lines().filter { it.isNotBlank() }
    }

    /**
     * 读取仓库文件在 HEAD 检出版本下的内容（git show）。
     * blobless 模式下缺失的 blob 会由 git 按需从远端拉取，适用于小型配置文件。
     */
    override suspend fun readRepositoryFile(targetDir: String, filePath: String): String? = withContext(Dispatchers.IO) {
        val result = exec(File(targetDir), listOf("show", "HEAD:$filePath"))
        if (result.exitCode != 0) {
            AppLogger.w(TAG, "读取仓库文件失败: $filePath → ${result.output}")
            return@withContext null
        }
        result.output
    }

    override suspend fun sparseCheckout(
        targetDir: String,
        paths: List<String>,
        onLog: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val dir = File(targetDir)
        // 1. 启用 no-cone 稀疏检出（支持目录与具体文件混合规则）
        exec(dir, listOf("config", "core.sparseCheckout", "true"))
        exec(dir, listOf("config", "core.sparseCheckoutCone", "false"))
        // 2. 直接写入模式文件（规避 Windows 命令行长度限制）
        val infoDir = File(dir, ".git/info").apply { mkdirs() }
        File(infoDir, "sparse-checkout").writeText(paths.joinToString("\n") + "\n")
        // 3. 强制检出 HEAD，按稀疏规则落盘文件
        val code = execStreaming(dir, listOf("checkout", "-f", "--progress")) { line -> onLog(line) }
        if (code != 0) "稀疏检出失败 (退出码 $code)" else null
    }

    override suspend fun pullUpdates(
        targetDir: String,
        credential: GitCredentialInfo?,
        onLog: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val code = execStreaming(File(targetDir), listOf("pull", "--progress", "origin"), extraEnv = sshEnvOf(credential)) { line ->
            onLog(sanitize(line, credential))
        }
        if (code != 0) "拉取更新失败 (退出码 $code)" else null
    }

    // ---------- 认证地址构造与脱敏工具 ----------

    /**
     * 依据凭据构造 SSH 指定密钥的环境变量：
     * 携带私钥路径时注入 GIT_SSH_COMMAND 强制 git 使用该密钥并禁用其它候选密钥；
     * 非密钥凭据或路径为空时返回空 Map（走 git 默认密钥发现机制）。
     */
    private fun sshEnvOf(credential: GitCredentialInfo?): Map<String, String> =
        credential?.keyFilePath
            ?.takeIf { it.isNotBlank() }
            ?.let { mapOf("GIT_SSH_COMMAND" to "ssh -i \"$it\" -o IdentitiesOnly=yes") }
            ?: emptyMap()

    /**
     * 依据凭据来源构造带认证信息的远端地址。
     * SSH 地址与 GLOBAL_CONFIG / SSH_KEY 来源不改写，交由 git 自身机制认证。
     */
    private fun authenticatedUrl(repoUrl: String, credential: GitCredentialInfo?): String {
        if (credential == null || !repoUrl.startsWith("http", ignoreCase = true)) return repoUrl
        return when (credential.source) {
            GitCredentialSource.MANUAL -> embedCredentials(
                repoUrl,
                credential.userName?.takeIf { it.isNotBlank() } ?: "oauth2",
                credential.token.orEmpty()
            )
            GitCredentialSource.GIT_CREDENTIALS_FILE -> {
                val host = extractHost(repoUrl) ?: return repoUrl
                findStoredCredential(host)?.let { (user, pass) -> embedCredentials(repoUrl, user, pass) } ?: repoUrl
            }
            else -> repoUrl
        }
    }

    /** 将用户名与令牌内嵌到 https 地址中（特殊字符经 URL 编码） */
    private fun embedCredentials(url: String, user: String, token: String): String {
        val prefix = if (url.startsWith("https://", ignoreCase = true)) "https://" else "http://"
        val rest = url.substring(prefix.length)
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        return "$prefix$encodedUser:$encodedToken@$rest"
    }

    /** 提取 http(s) 地址中的主机名 */
    private fun extractHost(url: String): String? = try {
        URI(url).host
    } catch (e: Exception) {
        null
    }

    /** 从 ~/.git-credentials 行中解析匹配主机的明文用户名与令牌 */
    private fun findStoredCredential(host: String): Pair<String, String>? {
        return try {
            val file = File(System.getProperty("user.home"), ".git-credentials")
            if (!file.exists()) return null
            file.readLines().forEach { line ->
                if (!line.contains("://")) return@forEach
                val authPart = line.substringAfter("://").substringBefore('@')
                val hostPart = line.substringAfter("://").substringAfter('@')
                if (hostPart.startsWith(host, ignoreCase = true) && authPart.contains(':')) {
                    return authPart.substringBefore(':') to authPart.substringAfter(':')
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 日志脱敏：将令牌内容替换为 ***，避免凭据泄露到界面日志 */
    private fun sanitize(text: String, credential: GitCredentialInfo?): String {
        var result = text
        credential?.token?.takeIf { it.isNotBlank() }?.let { result = result.replace(it, "***") }
        return result
    }

    private companion object {
        /** 日志标签 */
        const val TAG = "JvmGitService"

        /** 版本列表条目上限（分支 / 标签），防止超大仓库生成超长清单造成卡顿 */
        const val MAX_REF_ENTRIES = 200

        /** 最近提交列表条数上限 */
        const val RECENT_COMMIT_COUNT = 50
    }
}

/** JVM 平台的服务实例化工厂实现 */
actual fun createGitService(): GitService = JvmGitService()
