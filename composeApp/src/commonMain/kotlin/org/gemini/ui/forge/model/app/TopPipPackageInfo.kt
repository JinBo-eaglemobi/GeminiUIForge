package org.gemini.ui.forge.model.app

import kotlinx.serialization.Serializable

@Serializable
data class TopPipPackageRow(
    val project: String,
    val download_count: Long
)

@Serializable
data class TopPipPackagesResponse(
    val rows: List<TopPipPackageRow>
)