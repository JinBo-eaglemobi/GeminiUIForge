package org.gemini.ui.forge

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform