package org.gemini.ui.forge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.app.FullEnvironmentStatus
import org.gemini.ui.forge.service.EnvironmentCheckService
import org.gemini.ui.forge.service.createEnvironmentCheckService
import org.gemini.ui.forge.utils.AppLogger

/**
 * 专门负责 Python 运行环境检测与维护的独立 ViewModel
 */
class AppEnvViewModel(
    private val envService: EnvironmentCheckService = createEnvironmentCheckService()
) : ViewModel() {

    private val _status = MutableStateFlow(FullEnvironmentStatus())
    val status: StateFlow<FullEnvironmentStatus> = _status.asStateFlow()

    private val _pipPackages = MutableStateFlow<List<org.gemini.ui.forge.model.app.PipPackageInfo>>(emptyList())
    val pipPackages: StateFlow<List<org.gemini.ui.forge.model.app.PipPackageInfo>> = _pipPackages.asStateFlow()

    private val _isPipLoading = MutableStateFlow(false)
    val isPipLoading: StateFlow<Boolean> = _isPipLoading.asStateFlow()

    private val _searchResult = MutableStateFlow<org.gemini.ui.forge.model.app.PipPackageInfo?>(null)
    val searchResult: StateFlow<org.gemini.ui.forge.model.app.PipPackageInfo?> = _searchResult.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _pipLogs = MutableStateFlow<List<String>>(emptyList())
    val pipLogs: StateFlow<List<String>> = _pipLogs.asStateFlow()

    private val _isPipActionInProgress = MutableStateFlow(false)
    val isPipActionInProgress: StateFlow<Boolean> = _isPipActionInProgress.asStateFlow()

    // Top Packages 分页缓存
    private var allTopPackages = emptyList<String>()
    private val _topMarketPackages = MutableStateFlow<List<org.gemini.ui.forge.model.app.PipPackageInfo>>(emptyList())
    val topMarketPackages: StateFlow<List<org.gemini.ui.forge.model.app.PipPackageInfo>> = _topMarketPackages.asStateFlow()
    private val _marketPage = MutableStateFlow(0)
    val marketPage: StateFlow<Int> = _marketPage.asStateFlow()
    private val _isMarketLoading = MutableStateFlow(false)
    val isMarketLoading: StateFlow<Boolean> = _isMarketLoading.asStateFlow()

    init {
        // 启动时自动执行静默自检
        checkEnvironment()
    }

    /** 执行全量环境检查 */
    fun checkEnvironment() {
        viewModelScope.launch {
            _status.update { it.copy(isChecking = true) }
            val result = envService.checkAll()
            _status.update { result }
            
            // 顺便加载 pip 列表
            loadPipPackages()
        }
    }

    fun loadPipPackages() {
        viewModelScope.launch {
            _isPipLoading.value = true
            try {
                // 1. 快速读取本地包列表
                val localPackages = envService.getInstalledPipPackages()
                _pipPackages.value = localPackages
                
                // 2. 异步联网查询过期包
                val outdatedMap = envService.fetchOutdatedPipPackages()
                if (outdatedMap.isNotEmpty()) {
                    // 更新 Pip 面板数据
                    val updatedPackages = _pipPackages.value.map {
                        if (outdatedMap.containsKey(it.name)) {
                            it.copy(latestVersion = outdatedMap[it.name])
                        } else it
                    }
                    _pipPackages.value = updatedPackages
                    
                    // 顺带更新“核心依赖”面板中的过期状态
                    _status.update { s ->
                        val updatedItems = s.items.map { coreItem ->
                            if (outdatedMap.containsKey(coreItem.name)) {
                                coreItem.copy(latestVersion = outdatedMap[coreItem.name])
                            } else coreItem
                        }
                        s.copy(items = updatedItems)
                    }
                }
            } finally {
                _isPipLoading.value = false
            }
        }
    }

    fun loadMarketPage(pageIndex: Int = 0) {
        viewModelScope.launch {
            _isMarketLoading.value = true
            _marketPage.value = pageIndex
            AppLogger.i("AppEnvViewModel", "🌐 开始加载探索市场页面: $pageIndex")
            try {
                if (allTopPackages.isEmpty()) {
                    AppLogger.d("AppEnvViewModel", "⏳ 正在从云端拉取 Top 5000 排行榜缓存...")
                    allTopPackages = envService.fetchTopPackages()
                    AppLogger.d("AppEnvViewModel", "✅ Top 排行榜拉取成功, 共 ${allTopPackages.size} 条记录")
                }
                
                if (allTopPackages.isNotEmpty()) {
                    val pageSize = 20
                    val startIndex = pageIndex * pageSize
                    val endIndex = minOf(startIndex + pageSize, allTopPackages.size)
                    if (startIndex < allTopPackages.size) {
                        val pageNames = allTopPackages.subList(startIndex, endIndex)
                        
                        // 1. 瞬时填充占位卡片 (骨架屏)，秒开 UI
                        val placeholderDetails = pageNames.map { name ->
                            org.gemini.ui.forge.model.app.PipPackageInfo(
                                name = name, 
                                isInstalled = false, 
                                description = "正在获取包详情..."
                            )
                        }
                        _topMarketPackages.value = placeholderDetails
                        // 取消遮罩 loading，让用户立刻看到列表骨架
                        _isMarketLoading.value = false 
                        
                        AppLogger.d("AppEnvViewModel", "🚀 已渲染占位符，正在后台并行请求 ${pageNames.size} 个扩展包详情...")
                        
                        // 2. 发起非阻塞的并行请求，获取一个就刷新一个 (渐进式更新)
                        pageNames.forEachIndexed { index, name ->
                            launch {
                                try {
                                    val detail = envService.searchPipPackage(name)
                                    if (detail != null) {
                                        // 拿到详情后，无缝替换列表里对应位置的占位符
                                        _topMarketPackages.update { currentList ->
                                            val newList = currentList.toMutableList()
                                            // 防止快速切页导致的越界或数据混乱，通过包名匹配
                                            val targetIndex = newList.indexOfFirst { it.name == name }
                                            if (targetIndex != -1) {
                                                newList[targetIndex] = detail
                                            }
                                            newList
                                        }
                                    } else {
                                        _topMarketPackages.update { currentList ->
                                            val newList = currentList.toMutableList()
                                            val targetIndex = newList.indexOfFirst { it.name == name }
                                            if (targetIndex != -1) {
                                                newList[targetIndex] = newList[targetIndex].copy(description = "无描述信息")
                                            }
                                            newList
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 忽略单个包请求失败
                                }
                            }
                        }
                    } else {
                         _isMarketLoading.value = false 
                    }
                } else {
                     _isMarketLoading.value = false 
                }
            } catch (e: Exception) {
                AppLogger.e("AppEnvViewModel", "❌ 加载探索市场失败", e)
                _isMarketLoading.value = false 
            } finally {
                AppLogger.i("AppEnvViewModel", "🏁 探索市场页面 $pageIndex 初始渲染结束")
            }
        }
    }

    fun searchPipPackage(query: String) {
        if (query.isBlank()) {
            _searchResult.value = null
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                _searchResult.value = envService.searchPipPackage(query)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResult() {
        _searchResult.value = null
    }

    fun batchInstallPipPackages(names: List<String>) {
        if (names.isEmpty()) return
        viewModelScope.launch {
            _isPipActionInProgress.value = true
            _pipLogs.value = listOf("正在开始批量安装/更新: ${names.joinToString(", ")}...")
            envService.batchInstallPipPackages(names).collect { log ->
                _pipLogs.update { it + log }
            }
            _isPipActionInProgress.value = false
            loadPipPackages()
            checkEnvironment()
        }
    }

    fun batchUninstallPipPackages(names: List<String>) {
        if (names.isEmpty()) return
        viewModelScope.launch {
            _isPipActionInProgress.value = true
            _pipLogs.value = listOf("正在开始批量卸载: ${names.joinToString(", ")}...")
            envService.batchUninstallPipPackages(names).collect { log ->
                _pipLogs.update { it + log }
            }
            _isPipActionInProgress.value = false
            loadPipPackages()
            checkEnvironment()
        }
    }

    fun openPackageReleaseNotes(packageName: String) {
        viewModelScope.launch {
            val url = envService.fetchPackageUrl(packageName)
            if (url != null) {
                // 如果是 Github 链接，尝试拼接 /releases
                val finalUrl = if (url.contains("github.com")) {
                    if (url.endsWith("/")) "${url}releases" else "$url/releases"
                } else url
                getPlatform().openInBrowser(finalUrl)
            }
        }
    }

    /** 直接跳转到扩展包的 Home 界面 */
    fun openPackageHome(packageName: String) {
        viewModelScope.launch {
            val url = envService.fetchPackageUrl(packageName)
            if (url != null) {
                getPlatform().openInBrowser(url)
            }
        }
    }

    /** 安装特定的环境依赖项并记录实时日志 */
    fun installEnvironmentItem(name: String) {
        viewModelScope.launch {
            // 标记正在安装
            _status.update { s ->
                val newItems = s.items.map { 
                    if (it.name == name) it.copy(isInstalling = true, installLogs = emptyList()) else it 
                }
                s.copy(items = newItems)
            }

            envService.installItem(name).collect { log ->
                _status.update { s ->
                    val newItems = s.items.map { 
                        if (it.name == name) it.copy(installLogs = it.installLogs + log) else it 
                    }
                    s.copy(items = newItems)
                }
            }

            // 安装完成后重新扫描状态
            checkEnvironment()
        }
    }

    /** 卸载特定的环境依赖项并记录实时日志 */
    fun uninstallEnvironmentItem(name: String) {
        viewModelScope.launch {
            // 标记正在卸载 (复用 isInstalling 字段以显示日志对话框)
            _status.update { s ->
                val newItems = s.items.map {
                    if (it.name == name) it.copy(isInstalling = true, installLogs = listOf("开始卸载...")) else it
                }
                s.copy(items = newItems)
            }

            envService.uninstallItem(name).collect { log ->
                _status.update { s ->
                    val newItems = s.items.map {
                        if (it.name == name) it.copy(installLogs = it.installLogs + log) else it
                    }
                    s.copy(items = newItems)
                }
            }

            // 卸载完成后重新扫描状态
            checkEnvironment()
        }
    }
}
