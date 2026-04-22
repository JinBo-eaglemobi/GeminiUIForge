package org.gemini.ui.forge.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.utils.AppLogger

/**
 * 全应用级别的运行环境配置单例。
 * 用于集中管理如数据根目录、当前选中的模板等全局状态。
 */
object GlobalAppEnv {
    private val _dataRoot = MutableStateFlow("")
    
    /** 当前动态的数据存储根目录路径 (绝对路径) */
    val dataRoot: StateFlow<String> = _dataRoot.asStateFlow()

    /** 
     * 获取当前的绝对根目录字符串。
     * 业务代码应优先使用 TemplateFile，而非直接操作此字符串。
     */
    val currentRootPath: String get() = _dataRoot.value

    /**
     * 更新全局数据根目录。
     * 当设置中更改了路径或应用初始化加载配置后调用。
     */
    fun updateDataRoot(newPath: String) {
        val normalized = newPath.replace("\\", "/").removeSuffix("/")
        _dataRoot.value = normalized
        AppLogger.i("GlobalAppEnv", "🌍 全局数据根目录已更新: $normalized")
    }
}
