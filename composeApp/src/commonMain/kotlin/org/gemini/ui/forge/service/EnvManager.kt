package org.gemini.ui.forge.service

expect class EnvManager() {
    fun saveKey(keyName: String, keyValue: String)
    fun loadKey(keyName: String): String?
}
