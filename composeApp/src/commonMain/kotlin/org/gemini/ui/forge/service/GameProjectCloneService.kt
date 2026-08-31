package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.gameproject.CloneProgress
import org.gemini.ui.forge.model.gameproject.CloneStep
import org.gemini.ui.forge.model.gameproject.GitCredentialInfo
import org.gemini.ui.forge.utils.AppLogger

/**
 * 版本来源类型枚举：决定克隆时把 HEAD 指向哪种引用。
 */
enum class CloneRefType {
    /** 分支（留空时使用远端默认分支） */
    BRANCH,

    /** 标签 */
    TAG,

    /** 提交哈希 */
    COMMIT
}

/**
 * 克隆目标目录已存在时的处理方式（由用户在确认弹窗中选择）。
 */
enum class CloneDirChoice {
    /** 覆盖重建：删除现有目录后全新克隆 */
    OVERWRITE,

    /** 继续使用：保留现有内容，增量拉取并重新检出 */
    REUSE
}

/**
 * 游戏项目克隆请求参数（与 GameProjectCloneService 紧密相关的小数据类）。
 */
data class CloneRequest(
    /** git 仓库地址 */
    val repoUrl: String,
    /** 本地目标目录（绝对路径） */
    val targetDir: String,
    /** 选用的 git 凭据 */
    val credential: GitCredentialInfo? = null
)

/**
 * 版本选择挂起步骤的选中结果。
 * @param refType 版本来源类型（分支 / 标签 / 提交）
 * @param ref 版本值（分支名 / 标签名 / 提交哈希）；null 表示使用远端默认分支（仅 BRANCH 类型）
 */
data class RefSelection(
    val refType: CloneRefType,
    val ref: String?
)

/**
 * 仓库提交摘要（版本选择列表展示用）。
 */
data class CommitSummary(
    /** 完整提交哈希 */
    val fullHash: String,
    /** 短哈希（列表展示与检出入参） */
    val shortHash: String,
    /** 提交说明首行 */
    val subject: String
)

/**
 * 检出版本目录：封装分支 / 标签 / 提交三类列表的懒加载查询。
 * 数据源为 blobless fetch 后的本地引用库，查询无额外网络成本；
 * 结果由调用方按类别缓存，切换类别时才拉取对应列表。
 */
class GitRefCatalog(
    private val gitService: GitService,
    private val targetDir: String
) {
    /** 远端分支名清单（数量受上限约束） */
    suspend fun branches(): List<String> = gitService.listRemoteBranches(targetDir)

    /** 远端标签名清单（按创建时间倒序，数量受上限约束） */
    suspend fun tags(): List<String> = gitService.listRemoteTags(targetDir)

    /** 最近提交摘要清单（默认分支，仅取最近若干条） */
    suspend fun commits(): List<CommitSummary> = gitService.listRecentCommits(targetDir)
}

/**
 * 游戏项目按需克隆编排服务。
 *
 * 核心流程（两段式）：
 * 1. 创建目录 → blobless 拉取（只拉提交与目录树，不下载文件内容）；
 * 2. 扫描 game/src/ 游戏列表（排除 main）→ 挂起等待用户选择目标游戏；
 * 3. 依据所选游戏 + 公共资源规则，解析真实大小写路径后执行 sparse-checkout 按需下载。
 *
 * 资源拉取清单（路径统一忽略大小写，缺失的路径自动跳过并记录日志）：
 * - 游戏专属：game/src/<game>、build/assets/<game>、build/assets/activeRes/<game>(可缺)、
 *   build/assets/sounds/<game>、build/assets/js/<game>.js 与 <game>.min.js、res/assets/<game>、res/<game>(可缺)
 * - 公共资源：game/src/main、game/libs、build/ 根目录全部文件、
 *   build/assets/{gameCommon,common,init,gold.png,font,configs,languages}、
 *   build/assets/sounds/ 根目录文件、res/ 顶层除 assets 外全部、
 *   res/common 与 res/gameCommon（兼容 res/assets/common 与 res/assets/gameCommon 双路径，缺失跳过）
 */
class GameProjectCloneService(private val gitService: GitService) {

    /**
     * 执行完整克隆流程。
     * @param onProgress 进度回调（UI 用于步骤高亮与日志滚动）
     * @param selectVersion 版本选择挂起回调：收到版本目录对象，返回用户选定的版本；返回 null 视为取消
     * @param selectGame 游戏选择挂起回调：收到扫描结果，返回用户选定的游戏名；返回 null 视为取消
     * @return 成功返回 null；失败或取消返回错误描述
     */
    suspend fun clone(
        request: CloneRequest,
        onProgress: (CloneProgress) -> Unit,
        selectVersion: suspend (GitRefCatalog) -> RefSelection?,
        selectGame: suspend (List<String>) -> String?
    ): String? {
        // 步骤 1：创建本地项目文件夹（由 git 初始化时一并创建）
        onProgress(CloneProgress(step = CloneStep.CREATE_FOLDER, stepDetail = request.targetDir))
        val createErr = gitService.initAndBloblessFetch(
            targetDir = request.targetDir,
            repoUrl = request.repoUrl,
            credential = request.credential,
            onLog = { line -> onProgress(CloneProgress(step = CloneStep.CONNECT_FETCH, logLine = line)) }
        )
        if (createErr != null) {
            onProgress(CloneProgress(step = CloneStep.FAILED, failure = createErr))
            return createErr
        }

        // 步骤 2：挂起等待用户选择要检出的版本（分支 / 标签 / 提交，列表由仓库数据懒加载）
        onProgress(CloneProgress(step = CloneStep.SELECT_VERSION))
        val selection = selectVersion(GitRefCatalog(gitService, request.targetDir))
        if (selection == null) {
            val err = "已取消选择版本"
            onProgress(CloneProgress(step = CloneStep.FAILED, failure = err))
            return err
        }
        val refCheckoutErr = gitService.checkoutRef(
            targetDir = request.targetDir,
            refType = selection.refType,
            ref = selection.ref,
            onLog = { line -> onProgress(CloneProgress(step = CloneStep.SELECT_VERSION, logLine = line)) }
        )
        if (refCheckoutErr != null) {
            onProgress(CloneProgress(step = CloneStep.FAILED, failure = refCheckoutErr))
            return refCheckoutErr
        }

        // 步骤 3：扫描 game/src/ 下的游戏列表（排除 main）
        onProgress(CloneProgress(step = CloneStep.SCAN_GAMES))
        val games = gitService.scanGames(request.targetDir)
        if (games.isEmpty()) {
            val err = "仓库中未扫描到可管理的游戏目录 (game/src/*)"
            onProgress(CloneProgress(step = CloneStep.FAILED, failure = err))
            return err
        }
        onProgress(CloneProgress(step = CloneStep.SCAN_GAMES, games = games))

        // 步骤 4：挂起等待用户选择要管理的游戏
        onProgress(CloneProgress(step = CloneStep.WAIT_SELECT_GAME))
        val selectedGame = selectGame(games)
        if (selectedGame == null) {
            val err = "已取消选择游戏"
            onProgress(CloneProgress(step = CloneStep.FAILED, failure = err))
            return err
        }

        // 步骤 5：应用仓库过滤配置（仓库根存在 .downloadignore 时解析 gitignore 规则，
        // 用于在下载清单中剔除不需要落盘的文件；配置不存在时行为不变）
        onProgress(CloneProgress(step = CloneStep.APPLY_FILTER, stepDetail = selectedGame))
        val treePaths = gitService.listAllTreePaths(request.targetDir)
        // 大小写不敏感地定位过滤配置文件的真实路径
        val filterFilePath = treePaths.firstOrNull { it.equals(DOWNLOAD_IGNORE_FILE, ignoreCase = true) }
        val excludeRules = if (filterFilePath != null) {
            onProgress(
                CloneProgress(
                    step = CloneStep.APPLY_FILTER,
                    logLine = "检测到仓库过滤配置 $filterFilePath，正在读取..."
                )
            )
            val content = gitService.readRepositoryFile(request.targetDir, filterFilePath)
            if (content != null) {
                GitIgnoreMatcher.parse(content).also { rules ->
                    onProgress(
                        CloneProgress(
                            step = CloneStep.APPLY_FILTER,
                            logLine = "已加载 ${rules.size} 条过滤规则"
                        )
                    )
                }
            } else {
                onProgress(
                    CloneProgress(
                        step = CloneStep.APPLY_FILTER,
                        logLine = "过滤配置读取失败，本次下载将忽略过滤"
                    )
                )
                emptyList()
            }
        } else {
            onProgress(
                CloneProgress(
                    step = CloneStep.APPLY_FILTER,
                    logLine = "未检测到 $DOWNLOAD_IGNORE_FILE，跳过过滤"
                )
            )
            emptyList()
        }

        // 步骤 6：解析真实大小写路径并执行 sparse-checkout 下载
        onProgress(CloneProgress(step = CloneStep.DOWNLOAD_ASSETS, stepDetail = selectedGame))
        val patterns = buildSparsePatterns(selectedGame, treePaths)
        val filteredPatterns = applyCloneFilter(patterns, excludeRules, treePaths)
        if (filteredPatterns.size != patterns.size) {
            onProgress(
                CloneProgress(
                    step = CloneStep.DOWNLOAD_ASSETS,
                    logLine = "过滤配置已剔除 ${patterns.size - filteredPatterns.size} 条路径规则"
                )
            )
        }
        AppLogger.i(TAG, "sparse-checkout 共 ${filteredPatterns.size} 条路径规则（过滤前 ${patterns.size} 条）")
        val checkoutErr = gitService.sparseCheckout(
            targetDir = request.targetDir,
            paths = filteredPatterns,
            onLog = { line -> onProgress(CloneProgress(step = CloneStep.DOWNLOAD_ASSETS, logLine = line)) }
        )
        if (checkoutErr != null) {
            onProgress(CloneProgress(step = CloneStep.FAILED, failure = checkoutErr))
            return checkoutErr
        }
        return null
    }

    /**
     * 依据过滤规则从 sparse-checkout 路径清单中剔除不需要下载的条目。
     *
     * 处理逻辑：
     * 1. 用匹配引擎逐条判定 treePaths，得到被剔除的文件集合；
     * 2. 命中剔除文件的目录规则（`dir/`）不能整体保留——把它展开为该目录下
     *    未被剔除的文件精确清单（目录内个别文件剔除得以生效）；
     * 3. 清单中的文件精确条目若属于剔除集合则直接移除。
     *
     * @param patterns 原始 sparse-checkout 路径规则（目录以 "/" 结尾，文件为精确路径）
     * @param rules 过滤规则（为空时原样返回，零开销）
     * @param treePaths 仓库 HEAD 的全部文件路径
     */
    internal fun applyCloneFilter(
        patterns: List<String>,
        rules: List<GitIgnoreMatcher.Rule>,
        treePaths: List<String>
    ): List<String> {
        if (rules.isEmpty()) return patterns
        // 被剔除的文件路径集合（仓库真实大小写）
        val excludedFiles = treePaths.filter { GitIgnoreMatcher.isExcluded(it, rules) }.toSet()
        if (excludedFiles.isEmpty()) return patterns
        val result = patterns.toMutableList()
        // 命中剔除文件的目录规则：移除后展开为该目录下未被剔除的全部文件
        val affectedDirs = result.filter { it.endsWith("/") && excludedFiles.any { f -> f.startsWith(it) } }
        for (dir in affectedDirs) {
            result.remove(dir)
            treePaths.filter { it.startsWith(dir) && it !in excludedFiles }.forEach { result.add(it) }
        }
        // 文件精确条目直接剔除
        result.removeAll { it in excludedFiles }
        return result
    }

    /**
     * 依据所选游戏与公共资源规则，把逻辑路径解析为仓库中真实大小写的
     * sparse-checkout 路径规则清单（目录以 "/" 结尾，文件为精确路径）。
     *
     * @param selectedGame 用户选定的游戏名（以仓库真实大小写为准）
     * @param treePaths 仓库 HEAD 的全部文件路径（git ls-tree -r 产物）
     */
    internal fun buildSparsePatterns(selectedGame: String, treePaths: List<String>): List<String> {
        val lowerToFile = treePaths.associateBy { it.lowercase() }
        val patterns = LinkedHashSet<String>()

        /** 大小写不敏感地解析文件路径，不存在则跳过并记日志 */
        fun addFile(logical: String) {
            val real = lowerToFile[logical.lowercase()]
            if (real != null) {
                patterns.add(real)
            } else {
                AppLogger.i(TAG, "资源文件不存在，已跳过: $logical")
            }
        }

        /** 大小写不敏感地解析目录（目录无独立条目，通过其下文件路径前缀推断），不存在则跳过并记日志 */
        fun addDir(logical: String) {
            val prefix = logical.lowercase() + "/"
            val hit = treePaths.firstOrNull { it.lowercase().startsWith(prefix) }
            if (hit != null) {
                patterns.add(hit.substring(0, prefix.length - 1) + "/")
            } else {
                AppLogger.i(TAG, "资源目录不存在，已跳过: $logical")
            }
        }

        val game = selectedGame
        // ---- 游戏专属资源 ----
        addDir("game/src/$game")
        addDir("build/assets/$game")
        addDir("build/assets/activeRes/$game") // 可能不存在，缺失即忽略
        addDir("build/assets/sounds/$game")
        addFile("build/assets/js/$game.js")
        addFile("build/assets/js/$game.min.js")
        addDir("res/assets/$game")
        addDir("res/$game") // 可能不存在，缺失即忽略

        // ---- 公共资源 ----
        // 仓库根级散文件全部拉取（package.json / pnpm-workspace.yaml / tsconfig 等配置），
        // 即需求"首先拉取根文件，文件夹不动"：只取根下文件条目（ls-tree 中不含 / 的路径），目录不拉取
        treePaths.filter { !it.contains('/') }.forEach { patterns.add(it) }
        addDir("game/src/main")
        addDir("game/libs")
        // build/ 根目录内的所有文件（不含子文件夹内容）
        treePaths.filter { it.startsWith("build/") && !it.removePrefix("build/").contains('/') }
            .forEach { patterns.add(it) }
        // build/assets/ 下的公共目录与文件
        listOf("gameCommon", "common", "init", "font", "configs", "languages").forEach { addDir("build/assets/$it") }
        addFile("build/assets/gold.png")
        // build/assets/sounds/ 根目录文件（不含子文件夹）
        treePaths.filter {
            it.startsWith("build/assets/sounds/") && !it.removePrefix("build/assets/sounds/").contains('/')
        }.forEach { patterns.add(it) }
        // res/ 顶层除 assets 外的全部内容（目录与文件）
        treePaths.filter { it.startsWith("res/") && !it.removePrefix("res/").contains('/') }
            .filterNot { it.equals("res/assets", ignoreCase = true) }
            .forEach { top ->
                // 顶层文件直接加入；顶层目录通过其下文件推导真实大小写
                val dirName = top.removePrefix("res/")
                addDir("res/$dirName")
            }
        // res/common 与 res/gameCommon：双路径保险（部分仓库位于 res/assets/ 下）
        addDir("res/common")
        addDir("res/gameCommon")
        addDir("res/assets/common")
        addDir("res/assets/gameCommon")

        return patterns.toList()
    }

    private companion object {
        /** 日志标签 */
        const val TAG = "GameProjectClone"

        /** 仓库下载过滤配置文件名（gitignore 语法，位于仓库根目录，通用命名不绑定本工具） */
        const val DOWNLOAD_IGNORE_FILE = ".downloadignore"
    }
}
