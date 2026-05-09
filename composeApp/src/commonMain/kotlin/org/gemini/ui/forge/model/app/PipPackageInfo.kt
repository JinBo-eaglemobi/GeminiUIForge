package org.gemini.ui.forge.model.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PipPackageJson(
    val name: String,
    val version: String,
    @SerialName("latest_version") val latestVersion: String? = null
)

data class PipPackageInfo(
    val name: String,
    val version: String? = null,
    val latestVersion: String? = null,
    val isInstalled: Boolean = true,
    val isRecommended: Boolean = false,
    val description: String = "",
    val projectUrl: String? = null
) {
    val isOutdated: Boolean
        get() = isInstalled && latestVersion != null && version != latestVersion
}
