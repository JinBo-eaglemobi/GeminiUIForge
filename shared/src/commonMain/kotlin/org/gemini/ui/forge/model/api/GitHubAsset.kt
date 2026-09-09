package org.gemini.ui.forge.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub API 资源资产模型 (DTO)
 */
@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L
)
