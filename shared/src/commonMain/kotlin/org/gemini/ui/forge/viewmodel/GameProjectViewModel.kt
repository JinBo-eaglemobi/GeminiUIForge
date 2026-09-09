package org.gemini.ui.forge.viewmodel

import org.gemini.ui.forge.getCurrentTimeMillis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.manager.GameProjectManager
import org.gemini.ui.forge.model.gameproject.CloneProgress
import org.gemini.ui.forge.model.gameproject.DebugBridgeMessage
import org.gemini.ui.forge.model.gameproject.DebugPropertyRow
import org.gemini.ui.forge.model.gameproject.DebugTreeNode
import org.gemini.ui.forge.model.gameproject.CloneStep
import org.gemini.ui.forge.model.gameproject.GameProjectInfo
import org.gemini.ui.forge.model.gameproject.GameProjectSession
import org.gemini.ui.forge.model.gameproject.GitCredentialInfo
import org.gemini.ui.forge.model.gameproject.GitCredentialMode
import org.gemini.ui.forge.model.gameproject.GitCredentialSource
import org.gemini.ui.forge.model.gameproject.InstallChoice
import org.gemini.ui.forge.model.gameproject.NodeEnvStatus
import org.gemini.ui.forge.model.gameproject.PnpmDependencyStatus
import org.gemini.ui.forge.model.gameproject.ResourceTreeNode
import org.gemini.ui.forge.service.CloneDirChoice
import org.gemini.ui.forge.service.CloneRequest
import org.gemini.ui.forge.service.GameProjectCloneService
import org.gemini.ui.forge.service.GitRefCatalog
import org.gemini.ui.forge.service.RefSelection
import org.gemini.ui.forge.service.GitService
import org.gemini.ui.forge.service.NodeEnvService
import org.gemini.ui.forge.service.PnpmDependencyService
import org.gemini.ui.forge.service.ResourceTreeService
import org.gemini.ui.forge.service.createGitService
import org.gemini.ui.forge.service.createNodeEnvService
import org.gemini.ui.forge.service.createPnpmDependencyService
import org.gemini.ui.forge.service.createResourceTreeService
import org.gemini.ui.forge.state.ui.GameProjectFormState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.looseJson

/**
 * 游戏项目管理模块的 ViewModel。
 * 承载创建向导表单、本机凭据探测、Node 环境检测、连接测试、
 * 按需克隆全流程（含游戏选择挂起交互）、pnpm 依赖检查与安装、
 * 以及已纳管项目列表的增删查。
 */
class GameProjectViewModel(
    storage: LocalFileStorage,
    configManager: ConfigManager
) : ViewModel() {

    /** 私有协程域：随 ViewModel 清理自动取消 */
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 项目注册表管理器 */
    private val manager = GameProjectManager(storage, configManager)

    /** git 操作服务（桌面端为 git CLI 实现） */
    private val gitService: GitService = createGitService()

    /** Node 环境检测服务 */
    private val nodeEnvService: NodeEnvService = createNodeEnvService()

    /** pnpm 依赖服务 */
    private val pnpmService: PnpmDependencyService = createPnpmDependencyService()

    /** 按需克隆编排服务 */
    private val cloneService = GameProjectCloneService(gitService)

    /** 项目资源树服务 */
    private val treeService: ResourceTreeService = createResourceTreeService()

    // ---------- 界面状态 ----------

    /** 已纳管项目列表（最近打开优先） */
    var projects by mutableStateOf<List<GameProjectInfo>>(emptyList())
        private set

    /** 创建向导表单 */
    var form by mutableStateOf(GameProjectFormState())
        private set

    /** 本机探测到的全部 git 凭据列表（可包含多条：全局配置、凭据文件逐行、SSH 密钥） */
    var localCredentials by mutableStateOf<List<GitCredentialInfo>>(emptyList())
        private set

    /** 用户从本机凭据列表中选中的凭据；使用本机凭据模式且未选中时回退列表首项 */
    var selectedCredential by mutableStateOf<GitCredentialInfo?>(null)
        private set

    /** Node 环境检测结果快照 */
    var envStatus by mutableStateOf(NodeEnvStatus())
        private set

    /** 环境检测进行中标记 */
    var envChecking by mutableStateOf(false)
        private set

    /** 连接测试进行中标记 */
    var testing by mutableStateOf(false)
        private set

    /** 连接测试结果：null=未测试，true=成功，false=失败 */
    var testSuccess by mutableStateOf<Boolean?>(null)
        private set

    /** 连接测试失败时的错误信息 */
    var testMessage by mutableStateOf<String?>(null)
        private set

    /** 克隆流程弹窗可见性 */
    var cloneDialogVisible by mutableStateOf(false)
        private set

    /** 克隆流程当前进度（步骤 + 附加数据） */
    var cloneProgress by mutableStateOf(CloneProgress())
        private set

    /** 克隆流程实时日志（滚动窗口，最多保留 600 行） */
    var cloneLogs by mutableStateOf<List<String>>(emptyList())
        private set

    /** 待用户选择的游戏列表；非空时弹层显示单选框 */
    var pendingGames by mutableStateOf<List<String>?>(null)
        private set

    /** 依赖检查结果列表 */
    var dependencies by mutableStateOf<List<PnpmDependencyStatus>>(emptyList())
        private set

    /** 依赖安装进行中标记 */
    var depsInstalling by mutableStateOf(false)
        private set

    /** 依赖安装实时日志（独立于克隆日志，供依赖面板展示；限长裁剪） */
    var installLogs by mutableStateOf<List<String>>(emptyList())
        private set

    /** 克隆失败错误信息 */
    var cloneErrorMessage by mutableStateOf<String?>(null)
        private set

    /** 工作台左侧的项目资源树（null 表示未加载或目录不存在） */
    var resourceTree by mutableStateOf<ResourceTreeNode?>(null)
        private set

    /** build 目录下可预览的 HTML 文件绝对路径列表 */
    var htmlFiles by mutableStateOf<List<String>>(emptyList())
        private set

    /** 当前选中的 HTML 文件绝对路径 */
    var selectedHtml by mutableStateOf<String?>(null)
        private set

    /** 调试模式开关（注入 laya.debugtool.js 并建立调试桥） */
    var debugMode by mutableStateOf(false)
        private set

    /** 调试节点树（由调试桥推送的 JSON 解析而来） */
    var debugTree by mutableStateOf<DebugTreeNode?>(null)
        private set

    /** 当前选中调试节点的属性列表（单行一条） */
    var debugProps by mutableStateOf<List<DebugPropertyRow>>(emptyList())
        private set

    /** 当前选中的调试节点 ID */
    var debugSelectedId by mutableStateOf<String?>(null)
        private set

    /** 待查询属性的节点 ID（值变化驱动预览面板执行一次查询） */
    var inspectTarget by mutableStateOf<String?>(null)
        private set

    /** 调试桥日志输出（限 300 行） */
    var debugLogs by mutableStateOf<List<String>>(emptyList())
        private set

    /** 游戏选择挂起句柄（clone 流程与 UI 选择弹层之间的桥） */
    private var gameDeferred: CompletableDeferred<String>? = null

    /** 版本选择挂起等待器：完成值=选中结果；取消=终止克隆流程 */
    private var versionDeferred: CompletableDeferred<RefSelection>? = null

    /** 当前等待用户选择版本的目录对象（非空时弹窗显示版本选择覆盖层） */
    var pendingVersionCatalog by mutableStateOf<GitRefCatalog?>(null)
        private set

    /** 目录冲突选择挂起等待器：完成值=用户选择；取消=终止创建 */
    private var dirChoiceDeferred: CompletableDeferred<CloneDirChoice>? = null

    /** 冲突目录路径（非空时界面显示"目录已存在"确认弹窗） */
    var pendingExistingDir by mutableStateOf<String?>(null)
        private set

    /** 正在创建中的项目信息（流程完成后写入注册表） */
    private var pendingProjectInfo: GameProjectInfo? = null

    init {
        // 进入模块即预探测本机凭据与 Node 环境
        detectCredential()
        checkEnv()
        refreshProjects()
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }

    // ---------- 项目列表 ----------

    /** 刷新已纳管项目列表 */
    fun refreshProjects() {
        vmScope.launch { projects = manager.listProjects() }
    }

    /** 打开已纳管项目：写入会话、刷新最近打开时间并加载工作台数据 */
    fun openProject(info: GameProjectInfo) {
        GameProjectSession.currentProject = info
        loadWorkspace(info)
        vmScope.launch {
            manager.updateLastOpened(info.id)
            refreshProjects()
        }
    }

    /**
     * 删除已纳管项目注册记录。
     * @param deleteFiles 是否同时删除磁盘上的项目目录（默认仅移除注册记录）
     */
    fun deleteProject(id: String, deleteFiles: Boolean = false) {
        vmScope.launch {
            manager.deleteProject(id, deleteFiles)
            refreshProjects()
        }
    }

    // ---------- 表单与环境 ----------

    /** 更新表单字段；包管理模式变化时自动触发环境重检 */
    fun updateForm(transform: (GameProjectFormState) -> GameProjectFormState) {
        val old = form
        form = transform(form)
        if (old.packageManager != form.packageManager) {
            checkEnv()
        }
    }

    /** 探测本机全部 git 凭据（多凭据列表） */
    fun detectCredential() {
        vmScope.launch { localCredentials = gitService.detectLocalCredentials() }
    }

    /** 记录用户从本机凭据下拉列表中选中的凭据，并联动凭据模式切换 */
    fun selectCredential(credential: GitCredentialInfo) {
        selectedCredential = credential
        if (credential.source == GitCredentialSource.SSH_KEY) {
            // SSH 私钥条目：自动切换到 SSH 模式并回填密钥文件路径
            updateForm { it.copy(credentialMode = GitCredentialMode.SSH_KEY, sshKeyPath = credential.keyFilePath.orEmpty()) }
        } else {
            // 令牌类条目：切回令牌模式
            updateForm { it.copy(credentialMode = GitCredentialMode.GIT_TOKEN) }
        }
    }

    /** 重新执行 Node 环境检测 */
    fun checkEnv() {
        vmScope.launch {
            envChecking = true
            envStatus = nodeEnvService.checkNodeEnv()
            envChecking = false
        }
    }

    /**
     * 组装当前生效的凭据：优先本机凭据模式，其次已输入令牌时。
     * 用户名非必需：留空时由 Git 服务层默认 oauth2 虚拟账户。
     * 两者皆不满足返回 null。
     */
    private fun currentCredential(): GitCredentialInfo? {
        return when (form.credentialMode) {
            GitCredentialMode.SSH_KEY ->
                // SSH 模式：路径为空时交由 git 默认密钥发现；指定了则显式下发私钥路径
                GitCredentialInfo(
                    source = GitCredentialSource.SSH_KEY,
                    keyFilePath = form.sshKeyPath.trim().takeIf { it.isNotBlank() }
                )
            GitCredentialMode.GIT_TOKEN -> when {
                form.useGlobalCredential -> selectedCredential ?: localCredentials.firstOrNull()
                form.gitToken.isNotBlank() -> GitCredentialInfo(
                    source = GitCredentialSource.MANUAL,
                    userName = form.gitUserName.trim().ifBlank { null },
                    token = form.gitToken.trim()
                )
                // 令牌模式允许留空（公开仓库或交由 git 凭据助手自行认证）
                else -> null
            }
        }
    }

    /**
     * 仓库地址是否与当前凭据模式匹配（单一校验来源，供界面提示与创建/测试按钮共用）。
     * 空地址返回 false（等同待填写，由调用方决定展示形态）。
     */
    fun isRepoUrlValid(): Boolean = when {
        form.repoUrl.isBlank() -> false
        // 令牌模式：要求 http(s) 协议地址（令牌经 URL 内嵌或凭据助手下发）
        form.credentialMode == GitCredentialMode.GIT_TOKEN ->
            form.repoUrl.startsWith("http://") || form.repoUrl.startsWith("https://")
        // SSH 模式：ssh:// 协议或 git@host:path 形态
        else ->
            form.repoUrl.startsWith("ssh://") ||
                    (form.repoUrl.contains("@") && form.repoUrl.contains(":"))
    }

    /**
     * 表单是否满足创建条件（node 硬阻断 + 必填校验 + 地址格式校验）。
     * 名称查重等异步校验在提交时执行。
     */
    fun canCreate(): Boolean {
        // 凭据不再是必填项：令牌与 SSH 密钥均可留空（SSH 地址走本机密钥，公开仓库匿名访问）
        return !envStatus.isHardBlocked &&
                form.name.isNotBlank() &&
                isRepoUrlValid()
    }

    /** 解析项目最终保存目录（与 startCreate 的克隆目标逻辑一致），供界面实时预览 */
    suspend fun resolveSaveDir(): String =
        manager.resolveProjectDir(form.savePath.trim(), form.name.trim())

    /** 测试仓库连通性 */
    fun testConnection() {
        val credential = currentCredential() ?: return
        vmScope.launch {
            testing = true
            testSuccess = null
            testMessage = null
            val error = gitService.testConnection(form.repoUrl.trim(), credential)
            testSuccess = error == null
            testMessage = error
            testing = false
        }
    }

    // ---------- 克隆全流程 ----------

    /**
     * 启动创建流程：按需克隆 + 游戏选择 + 依赖检查。
     * 流程结束后（无论成败）通过 [cloneProgress]/[cloneErrorMessage] 暴露状态。
     */
    fun startCreate() {
        if (!canCreate()) return
        val credential = currentCredential() ?: return
        vmScope.launch {
            val name = form.name.trim()
            if (manager.isNameExists(name)) {
                Toast.show("项目名字已被占用，请更换", ToastType.INFO)
                return@launch
            }
            // 最终落盘目录 = 基础目录 + 项目名（未填地址时基础目录为默认 gameProjects 根）
            val targetDir = manager.resolveProjectDir(form.savePath.trim(), name)
            // 目标目录已存在（上次克隆残留或手工创建）：挂起等待用户选择覆盖重建 / 继续使用 / 取消
            if (manager.dirExists(targetDir)) {
                when (awaitExistingDirChoice(targetDir)) {
                    CloneDirChoice.OVERWRITE -> {
                        // 覆盖重建：递归删除旧目录，后续全新克隆
                        manager.deleteDirectory(targetDir)
                    }
                    CloneDirChoice.REUSE -> {
                        // 继续使用：保留现有内容；服务层会自动改写已登记的远端并增量拉取
                    }
                    null -> return@launch
                }
            }
            val info = GameProjectInfo(
                id = manager.newProjectId(),
                name = name,
                repoUrl = form.repoUrl.trim(),
                selectedGame = "",
                language = form.language,
                packageManager = form.packageManager,
                localPath = targetDir,
                gitUserName = credential.userName,
                hasGitToken = credential.source == GitCredentialSource.MANUAL,
                useGlobalCredential = credential.source != GitCredentialSource.MANUAL,
                createdAt = getCurrentTimeMillis()
            )
            pendingProjectInfo = info
            // 重置流程状态并打开弹窗
            cloneDialogVisible = true
            cloneProgress = CloneProgress()
            cloneLogs = emptyList()
            dependencies = emptyList()
            depsInstalling = false
            cloneErrorMessage = null

            val request = CloneRequest(
                repoUrl = form.repoUrl.trim(),
                targetDir = targetDir,
                credential = credential
            )
            val error = cloneService.clone(
                request,
                ::onCloneProgress,
                selectVersion = { catalog -> awaitVersionChoice(catalog) },
                selectGame = { games -> awaitGameChoice(games) }
            )
            if (error != null) {
                cloneErrorMessage = error
                return@launch
            }
            // 克隆成功：执行 pnpm 依赖检查
            updateProgress(CloneProgress(step = CloneStep.CHECK_DEPENDENCIES))
            dependencies = pnpmService.checkDependencies(targetDir)
            // 写入注册表与令牌
            manager.saveProject(pendingProjectInfo ?: info)
            if (credential.source == GitCredentialSource.MANUAL) {
                manager.saveGitToken(info.id, credential.token)
            }
            manager.updateLastOpened(info.id)
            refreshProjects()
            updateProgress(CloneProgress(step = CloneStep.COMPLETE))
        }
    }

    /** 克隆进度回调：更新步骤与日志 */
    private fun onCloneProgress(progress: CloneProgress) {
        updateProgress(progress)
        progress.logLine?.let { appendLog(it) }
    }

    /** 更新进度状态（保留日志由 appendLog 管理） */
    private fun updateProgress(progress: CloneProgress) {
        cloneProgress = progress
    }

    /** 追加一行流程日志（限长裁剪） */
    private fun appendLog(line: String) {
        cloneLogs = (cloneLogs + line).takeLast(MAX_LOG_LINES)
    }

    /**
     * 挂起等待用户在弹层中选择目标游戏。
     * 用户确认返回所选名字；取消流程返回 null。
     */
    private suspend fun awaitGameChoice(games: List<String>): String? {
        val deferred = CompletableDeferred<String>()
        gameDeferred = deferred
        pendingGames = games
        return try {
            deferred.await()
        } catch (e: Exception) {
            null
        } finally {
            pendingGames = null
            gameDeferred = null
        }
    }

    /** 用户在弹层中确认选择游戏 */
    fun confirmGame(name: String) {
        pendingProjectInfo = pendingProjectInfo?.copy(selectedGame = name)
        gameDeferred?.complete(name)
    }

    /**
     * 挂起等待用户在弹层中选择检出版本。
     * 用户确认返回选中结果；取消流程返回 null。
     */
    private suspend fun awaitVersionChoice(catalog: GitRefCatalog): RefSelection? {
        val deferred = CompletableDeferred<RefSelection>()
        versionDeferred = deferred
        pendingVersionCatalog = catalog
        return try {
            deferred.await()
        } catch (e: Exception) {
            null
        } finally {
            pendingVersionCatalog = null
            versionDeferred = null
        }
    }

    /** 用户在弹层中确认检出版本 */
    fun confirmVersion(selection: RefSelection) {
        versionDeferred?.complete(selection)
    }

    /** 用户在弹层中取消版本选择（终止克隆流程） */
    fun cancelVersionChoice() {
        versionDeferred?.cancel()
    }

    /**
     * 挂起等待用户对已存在目录的处理选择。
     * 选择覆盖/继续返回对应枚举；取消创建返回 null。
     */
    private suspend fun awaitExistingDirChoice(dir: String): CloneDirChoice? {
        val deferred = CompletableDeferred<CloneDirChoice>()
        dirChoiceDeferred = deferred
        pendingExistingDir = dir
        return try {
            deferred.await()
        } catch (e: Exception) {
            null
        } finally {
            pendingExistingDir = null
            dirChoiceDeferred = null
        }
    }

    /** 用户在目录冲突弹窗中做出选择 */
    fun confirmExistingDir(choice: CloneDirChoice) {
        dirChoiceDeferred?.complete(choice)
    }

    /** 用户取消目录冲突弹窗（终止创建流程） */
    fun cancelExistingDirChoice() {
        dirChoiceDeferred?.cancel()
    }

    /** 取消克隆流程（关闭弹窗并中止等待） */
    fun cancelClone() {
        gameDeferred?.cancel()
        versionDeferred?.cancel()
        cloneDialogVisible = false
    }

    // ---------- 工作台数据 ----------

    /**
     * 加载工作台数据：构建资源树、扫描 HTML 入口并默认选中第一个。
     * 重复调用安全（幂等重载）。
     */
    fun loadWorkspace(info: GameProjectInfo) {
        vmScope.launch {
            resourceTree = treeService.buildTree(info.localPath)
            htmlFiles = treeService.listHtmlFiles(info.localPath)
            if (selectedHtml == null || selectedHtml !in htmlFiles) {
                selectedHtml = htmlFiles.firstOrNull()
            }
        }
    }

    /** 重新扫描当前项目的资源树 */
    fun refreshTree() {
        val info = GameProjectSession.currentProject ?: return
        vmScope.launch {
            resourceTree = treeService.buildTree(info.localPath)
        }
    }

    /** 切换当前预览的 HTML 页面 */
    fun selectHtml(path: String?) {
        selectedHtml = path
    }

    // ---------- 调试桥（阶段 3） ----------

    /** 切换调试模式；关闭时清空调试状态 */
    fun toggleDebugMode(enabled: Boolean) {
        debugMode = enabled
        if (!enabled) {
            debugTree = null
            debugProps = emptyList()
            debugSelectedId = null
            inspectTarget = null
            debugLogs = emptyList()
        }
    }

    /** 调试桥消息入口：解析预览面板回传的 JSON 并分发到对应状态 */
    fun onDebugBridgeMessage(json: String) {
        val message = runCatching { looseJson.decodeFromString<DebugBridgeMessage>(json) }.getOrNull() ?: return
        when (message.type) {
            "tree" -> debugTree = message.tree
            "props" -> debugProps = message.props
            "log" -> appendDebugLog(message.text)
            "close" -> {
                selectedHtml = null
                debugTree = null
                debugProps = emptyList()
            }
            else -> Unit
        }
    }

    /** 选中调试树节点并请求查询其属性 */
    fun selectDebugNode(id: String?) {
        debugSelectedId = id
        debugProps = emptyList()
        inspectTarget = id
    }

    /** 追加调试日志（限制最大行数，避免无限增长） */
    private fun appendDebugLog(line: String) {
        if (line.isBlank()) return
        debugLogs = (debugLogs + line).takeLast(MAX_DEBUG_LOG_LINES)
    }

    /** 清空控制台调试日志 */
    fun clearDebugLogs() {
        debugLogs = emptyList()
    }

    // ---------- 依赖检查与安装 ----------

    /** 更新某个依赖的安装方式选择 */
    fun updateDepChoice(name: String, choice: InstallChoice) {
        dependencies = dependencies.map {
            if (it.name == name) it.copy(installChoice = choice) else it
        }
    }

    /** 重新执行依赖检查 */
    fun recheckDependencies() {
        val dir = pendingProjectInfo?.localPath ?: return
        vmScope.launch { dependencies = pnpmService.checkDependencies(dir) }
    }

    /** 对用户标记为 pnpm 安装的缺失依赖执行安装 */
    fun installSelected() {
        val dir = pendingProjectInfo?.localPath ?: return
        vmScope.launch {
            depsInstalling = true
            installLogs = emptyList()
            val error = pnpmService.installDependencies(dir) { line ->
                // 安装日志写入独立列表（依赖面板内实时展示），并限长裁剪
                installLogs = (installLogs + line).takeLast(MAX_INSTALL_LOG_LINES)
            }
            depsInstalling = false
            if (error == null) {
                installLogs = (installLogs + "—— 依赖安装完成 ——").takeLast(MAX_INSTALL_LOG_LINES)
                Toast.show("依赖安装完成", ToastType.INFO)
                dependencies = pnpmService.checkDependencies(dir)
            } else {
                installLogs = (installLogs + "—— 安装失败: $error ——").takeLast(MAX_INSTALL_LOG_LINES)
                Toast.show(error, ToastType.INFO)
            }
        }
    }

    /**
     * 完成创建流程：写入会话并关闭弹窗。
     * @return 创建完成的项目信息；流程未完成时返回 null
     */
    fun finishProject(): GameProjectInfo? {
        val info = pendingProjectInfo ?: return null
        if (cloneProgress.step != CloneStep.COMPLETE) return null
        GameProjectSession.currentProject = info
        cloneDialogVisible = false
        return info
    }

    private companion object {
        /** 流程日志最大保留行数 */
        const val MAX_LOG_LINES = 600

        /** 依赖安装日志最大保留行数 */
        const val MAX_INSTALL_LOG_LINES = 200

        /** 调试桥日志最大保留行数 */
        const val MAX_DEBUG_LOG_LINES = 300
    }
}
