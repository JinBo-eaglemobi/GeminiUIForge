package org.gemini.ui.forge.model.api

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String, // "user" 或 "model"
    val text: String
)