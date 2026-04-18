package org.gemini.ui.forge.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.CloudAssetManager
import org.gemini.ui.forge.service.ConfigManager

import org.gemini.ui.forge.ui.component.ToastData
import org.gemini.ui.forge.ui.component.ToastType

/**
 * 应用的主控制 ViewModel
 * 负责全局导航、配置同步以及跨模块的核心数据共享
 */
class AppViewModel(
    private val templateRepo: TemplateRepository = TemplateRepository(),
    val cloudAssetManager: CloudAssetManager,
    private val aiService: AIGenerationService
) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    // --- 全局 Toast 状态流 ---
    private val _toastData = MutableStateFlow<ToastData?>(null)
    val toastData: StateFlow<ToastData?> = _toastData.asStateFlow()

    /**
     * 显示全局 Toast
     */
    fun showToast(
        message: String, 
        type: ToastType = ToastType.INFO, 
        durationMillis: Long = 3000L,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        _toastData.value = ToastData(message, type, durationMillis, actionLabel, onAction)
    }

    /**
     * 清除 Toast（用于手动关闭或动画结束）
     */
    fun clearToast() {
        _toastData.value = null
    }

    /**
     * 初始化加载全局配置
     */
    fun syncInitialSettings(configManager: ConfigManager) {
        viewModelScope.launch {
            val apiKey = configManager.loadKey("GEMINI_API_KEY") ?: ""
            val globalKey = configManager.loadGlobalGeminiKey() ?: ""
            val languageCode = configManager.loadKey("APP_LANGUAGE") ?: "zh"
            val promptLangStr = configManager.loadKey("PROMPT_LANGUAGE_PREF") ?: "AUTO"
            val promptLang = try { PromptLanguage.valueOf(promptLangStr) } catch (e: Exception) { PromptLanguage.AUTO }
            val storageDir = templateRepo.getDataDir()
            val retriesStr = configManager.loadKey("API_MAX_RETRIES") ?: "3"
            
            _state.update { it.copy(
                globalState = it.globalState.copy(
                    apiKey = apiKey, 
                    effectiveApiKey = apiKey.ifBlank { globalKey }, 
                    templateStorageDir = storageDir, 
                    languageCode = languageCode, 
                    promptLangPref = promptLang, 
                    maxRetries = retriesStr.toIntOrNull() ?: 3
                ), 
                currentEditingPromptLang = if (promptLang == PromptLanguage.EN) PromptLanguage.EN else PromptLanguage.ZH
            ) }
        }
    }

    // --- 导航与显示控制 ---

    fun navigateTo(screen: AppScreen) = _state.update { it.copy(globalState = it.globalState.copy(currentScreen = screen)) }
    fun setThemeMode(mode: ThemeMode) = _state.update { it.copy(globalState = it.globalState.copy(themeMode = mode)) }
    fun setLanguage(code: String) = _state.update { it.copy(globalState = it.globalState.copy(languageCode = code)) }
    fun switchEditingLanguage(lang: PromptLanguage) = _state.update { it.copy(currentEditingPromptLang = lang) }

    // --- 数据加载与共享 ---

    fun loadProject(projectName: String, projectState: ProjectState) {
        _state.update { it.copy(
            project = projectState, 
            projectName = projectName, 
            selectedPageId = projectState.pages.firstOrNull()?.id
        ) }
    }

    /** 更新项目数据 (由局部编辑器/生图页回流，仅更新内存状态) */
    fun updateProject(updatedProject: ProjectState) {
        _state.update { it.copy(project = updatedProject) }
    }

    fun onPageSelected(pageId: String) = _state.update { it.copy(selectedPageId = pageId) }

    // --- 设置同步接口 ---

    fun updateApiKey(newKey: String) = _state.update { it.copy(globalState = it.globalState.copy(apiKey = newKey, effectiveApiKey = newKey)) }
    fun updateStorageDirState(newPath: String) = _state.update { it.copy(globalState = it.globalState.copy(templateStorageDir = newPath)) }
    fun updateMaxRetriesState(count: Int) = _state.update { it.copy(globalState = it.globalState.copy(maxRetries = count)) }
    fun updateShortcutState(action: ShortcutAction, keyChord: String) {
        _state.update { s -> 
            val newShortcuts = s.globalState.shortcuts.toMutableMap()
            newShortcuts[action] = keyChord
            s.copy(globalState = s.globalState.copy(shortcuts = newShortcuts))
        }
    }
    fun setPromptLanguagePref(pref: PromptLanguage) = _state.update { it.copy(globalState = it.globalState.copy(promptLangPref = pref)) }
}
