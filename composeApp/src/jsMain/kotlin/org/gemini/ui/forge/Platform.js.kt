package org.gemini.ui.forge

class JsPlatform: Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun getCurrentTimeMillis(): Long = kotlin.js.Date.now().toLong()