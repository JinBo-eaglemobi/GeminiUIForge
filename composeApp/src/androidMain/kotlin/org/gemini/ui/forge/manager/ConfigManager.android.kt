package org.gemini.ui.forge.manager

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual open class ConfigManager {

    /**
     * Android 平台的本地轻量级键值对存储 (SharedPreferences)。
     *
     * 解决 KMP 架构中获取 Context 的痛点：
     * 由于跨平台公共层 (`commonMain`) 实例化 `ConfigManager` 时无法传递 Android 特有的 `Context`，
     * 此处使用 `by lazy` 延迟加载，并利用 Java 反射 (`ActivityThread.currentApplication()`) 
     * 隐式获取系统的全局 Application Context。
     * 获取到上下文后，创建或打开名为 "gemini_ui_forge_config" 的私有配置文件。
     */
    private val prefs: SharedPreferences? by lazy {
        try {
            // 通过反射获取 Android 运行时的全局 Application 实例
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val application = activityThreadClass.getMethod("currentApplication").invoke(null) as? android.app.Application
            
            // 初始化并返回 SharedPreferences
            application?.getSharedPreferences("gemini_ui_forge_config", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun saveKey(keyName: String, keyValue: String) {
        withContext(Dispatchers.IO) {
            prefs?.edit()?.putString(keyName, keyValue)?.apply()
        }
    }

    actual suspend fun loadKey(keyName: String): String? {
        return withContext(Dispatchers.IO) {
            prefs?.getString(keyName, null)
        }
    }

    actual suspend fun loadGlobalGeminiKey(): String? {
        return null
    }
}
