package org.gemini.ui.forge.service

actual class EnvManager {
    actual fun saveKey(keyName: String, keyValue: String) {}
    actual fun loadKey(keyName: String): String? = null
}
