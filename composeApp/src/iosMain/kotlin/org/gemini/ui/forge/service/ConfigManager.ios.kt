package org.gemini.ui.forge.service

import platform.Foundation.NSUserDefaults

actual open class ConfigManager {
    /**
     * iOS 平台原生的轻量级键值对存储机制 (等同于 Android 的 SharedPreferences)。
     * standardUserDefaults 提供了一个与 App 绑定的持久化字典，
     * 非常适合用来存储 API Key、用户偏好等配置信息。
     */
    private val userDefaults = NSUserDefaults.standardUserDefaults

    actual suspend fun saveKey(keyName: String, keyValue: String) {
        // 将键值对同步保存到 iOS 的 UserDefaults 中
        userDefaults.setObject(keyValue, forKey = keyName)
    }

    actual suspend fun loadKey(keyName: String): String? {
        // 从 UserDefaults 中读取字符串值
        return userDefaults.stringForKey(keyName)
    }

    actual suspend fun loadGlobalGeminiKey(): String? {
        return null
    }
}
