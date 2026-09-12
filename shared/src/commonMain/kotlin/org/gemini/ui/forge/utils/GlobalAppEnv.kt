package org.gemini.ui.forge.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.userHomePath

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

    /**
     * 兼容兜底：确保全局根目录可用。
     *
     * 当 [currentRootPath] 尚未初始化（例如脱离主应用初始化链路单独使用 TemplateFile、
     * 单元测试或外部宿主先行调用）时，自动以项目跨平台属性 [userHomePath]（当前用户主目录，
     * 如 `C:/Users/xxx`）作为默认数据根目录并记录告警日志，
     * 从而保证相对路径始终能够解析出正确的绝对路径。
     *
     * @return 保证非空的当前数据根目录（绝对路径字符串）。
     */
    fun ensureInitialized(): String {
        if (_dataRoot.value.isNotBlank()) return _dataRoot.value

        // 兜底根目录：用户主目录（复用项目 Platform.kt 中的跨平台属性，如 C:/Users/xxx）
        val fallback = try {
            userHomePath.ifBlank { "/" }
                .replace("\\", "/")
                .trimEnd('/')
                .ifBlank { "/" }
        } catch (_: Throwable) {
            "/"
        }

        _dataRoot.value = fallback
        AppLogger.w("GlobalAppEnv", "⚠️ 全局数据根目录尚未初始化，已自动回退为用户主目录: $fallback")
        return fallback
    }
}