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
    val installLogs: List<String> = emptyList(),
    val latestVersion: String? = null,
    val lastError: String? = null,
    val isSuccess: Boolean? = null,
    val isUninstallAction: Boolean = false,
    val officialUrl: String = ""
) {
    val isOutdated: Boolean
        get() = isInstalled && latestVersion != null && version != latestVersion && latestVersion.isNotBlank()

    val hasLogs: Boolean
        get() = installLogs.isNotEmpty()
}

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
