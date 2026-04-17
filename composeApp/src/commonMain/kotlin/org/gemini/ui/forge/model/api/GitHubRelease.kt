package org.gemini.ui.forge.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub API Release 响应模型 (DTO)
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)
