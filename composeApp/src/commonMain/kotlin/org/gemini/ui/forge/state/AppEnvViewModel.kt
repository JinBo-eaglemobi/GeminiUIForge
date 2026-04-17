package org.gemini.ui.forge.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}
