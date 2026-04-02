package org.gemini.ui.forge

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

actual fun getCurrentTimeMillis(): Long = kotlin.js.Date.now().toLong()

actual fun Long.formatTimestamp(format: String): String {
    val date = kotlin.js.Date(this.toDouble())
    val yyyy = date.getFullYear().toString()
    val MM = (date.getMonth() + 1).toString().padStart(2, '0')
    val dd = date.getDate().toString().padStart(2, '0')
    val HH = date.getHours().toString().padStart(2, '0')
    val mm = date.getMinutes().toString().padStart(2, '0')
    val ss = date.getSeconds().toString().padStart(2, '0')
    
    return format
        .replace("yyyy", yyyy)
        .replace("MM", MM)
        .replace("dd", dd)
        .replace("HH", HH)
        .replace("mm", mm)
        .replace("ss", ss)
}