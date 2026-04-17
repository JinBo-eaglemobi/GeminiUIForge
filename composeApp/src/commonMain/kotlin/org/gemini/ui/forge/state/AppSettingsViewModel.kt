package org.gemini.ui.forge.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.app.ShortcutAction
import org.gemini.ui.forge.service.ConfigManager

/**
 * 专门负责应用配置持久化管理的 ViewModel
 */
class AppSettingsViewModel(
    private val configManager: ConfigManager = ConfigManager(),
    private val templateRepo: TemplateRepository = TemplateRepository()
) : ViewModel() {

    /**
     * 保存 API 密钥
     */
    fun saveApiKey(newKey: String) {
        viewModelScope.launch {
            configManager.saveKey("GEMINI_API_KEY", newKey)
        }
    }

    /**
     * 设置界面语言持久化
     */
    fun saveLanguage(code: String) {
        viewModelScope.launch {
            configManager.saveKey("APP_LANGUAGE", code)
        }
    }

    /**
     * 更新存储目录
     */
    suspend fun updateStorageDir(newPath: String): Boolean {
        return templateRepo.updateStorageDir(newPath)
    }

    /**
     * 设置 API 最大重试次数
     */
    fun saveMaxRetries(count: Int) {
        viewModelScope.launch {
            configManager.saveKey("API_MAX_RETRIES", count.toString())
        }
    }

    /**
     * 保存快捷键配置
     */
    fun saveShortcut(action: ShortcutAction, keyChord: String) {
        viewModelScope.launch {
            configManager.saveKey("SHORTCUT_${action.name}", keyChord)
        }
    }

    /**
     * 保存提示词语言偏好
     */
    fun savePromptLanguagePref(pref: PromptLanguage) {
        viewModelScope.launch {
            configManager.saveKey("PROMPT_LANGUAGE_PREF", pref.name)
        }
    }
    
    /**
     * 获取 ConfigManager 实例，用于初始化加载
     */
    fun getConfigManager() = configManager
}
