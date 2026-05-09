package org.gemini.ui.forge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gemini.ui.forge.model.app.FullEnvironmentStatus
import org.gemini.ui.forge.service.EnvironmentCheckService
import org.gemini.ui.forge.service.createEnvironmentCheckService

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
                org.gemini.ui.forge.getPlatform().openInBrowser(finalUrl)
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
