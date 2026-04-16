package org.gemini.ui.forge.model.app

/**
 * 描述单个环境依赖项的状态
 */
data class EnvironmentItemStatus(
    val name: String,
    val labelRes: org.jetbrains.compose.resources.StringResource,
    val isInstalled: Boolean = false,
    val version: String? = null,
    val isInstalling: Boolean = false,
    val installLogs: List<String> = emptyList()
)

/**
 * 整个 Python 环境的检查结果汇总
 */
data class FullEnvironmentStatus(
    val items: List<EnvironmentItemStatus> = emptyList(),
    val isChecking: Boolean = false
) {
    val isAllReady: Boolean
        get() = items.isNotEmpty() && items.all { it.isInstalled }
}
