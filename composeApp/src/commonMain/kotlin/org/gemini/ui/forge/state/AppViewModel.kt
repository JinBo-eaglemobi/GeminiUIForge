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
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.Toast

import org.gemini.ui.forge.ui.component.ToastType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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

    // --- 全局事件流 ---
    private val _saveEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveEvent: SharedFlow<Unit> = _saveEvent.asSharedFlow()

    private val _shortcutEvent = MutableSharedFlow<ShortcutAction>(extraBufferCapacity = 1)
    val shortcutEvent: SharedFlow<ShortcutAction> = _shortcutEvent.asSharedFlow()

    /**
     * 派发保存事件，通知当前活动的 Screen 执行保存逻辑
     */
    fun dispatchSaveEvent() {
        AppLogger.d("AppViewModel", "⌨️ 触发全局保存事件 (Ctrl+S 或 点击保存按钮)")
        viewModelScope.launch {
            _saveEvent.emit(Unit)
            _shortcutEvent.emit(ShortcutAction.SAVE)
        }
    }

    /**
     * 派发通用快捷键事件
     */
    fun dispatchShortcutEvent(action: ShortcutAction) {
        AppLogger.d("AppViewModel", "⌨️ 触发全局快捷键: [${action.label}]")
        viewModelScope.launch {
            if (action == ShortcutAction.SAVE) {
                _saveEvent.emit(Unit)
            }
            _shortcutEvent.emit(action)
        }
    }

    /**
     * 设置当前项目的“脏数据”状态
     */
    fun setDirty(dirty: Boolean) {
        _state.update { it.copy(isDirty = dirty) }
    }

    /**
     * 统一保存项目方法
     * @param projectName 项目名称
     * @param projectState 项目状态数据
     */
    fun saveProject(projectName: String, projectState: ProjectState) {
        viewModelScope.launch {
            try {
                templateRepo.saveTemplate(projectName, projectState)
                updateProject(projectState)
                setDirty(false)
                Toast.show("项目 [$projectName] 已保存", type = ToastType.SUCCESS)
                AppLogger.i("AppViewModel", "💾 项目 [$projectName] 已保存并同步")
            } catch (e: Exception) {
                AppLogger.e("AppViewModel", "❌ 保存项目 [$projectName] 失败", e)
                Toast.show("保存失败: ${e.message}", type = ToastType.ERROR)
            }
        }
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
            val promptLang = try {
                PromptLanguage.valueOf(promptLangStr)
            } catch (e: Exception) {
                PromptLanguage.AUTO
            }
            val storageDir = templateRepo.getDataDir()
            val retriesStr = configManager.loadKey("API_MAX_RETRIES") ?: "3"
            val imageGenCountStr = configManager.loadKey("IMAGE_GEN_COUNT") ?: "4"

            _state.update {
                it.copy(
                    globalState = it.globalState.copy(
                        apiKey = apiKey,
                        effectiveApiKey = apiKey.ifBlank { globalKey },
                        templateStorageDir = storageDir,
                        languageCode = languageCode,
                        promptLangPref = promptLang,
                        maxRetries = retriesStr.toIntOrNull() ?: 3,
                        imageGenCount = imageGenCountStr.toIntOrNull() ?: 4
                    )
                )
            }
        }
    }

    // --- 导航与显示控制 ---

    fun navigateTo(screen: AppScreen) =
        _state.update { it.copy(globalState = it.globalState.copy(currentScreen = screen)) }

    fun setThemeMode(mode: ThemeMode) = _state.update { it.copy(globalState = it.globalState.copy(themeMode = mode)) }
    fun setLanguage(code: String) = _state.update { it.copy(globalState = it.globalState.copy(languageCode = code)) }

    // --- 数据加载与共享 ---

    fun loadProject(projectName: String, projectState: ProjectState) {
        _state.update {
            it.copy(
                project = projectState,
                projectName = projectName
            )
        }
    }

    /** 更新项目数据 (由局部编辑器/生图页回流，仅更新内存状态) */
    fun updateProject(updatedProject: ProjectState) {
        _state.update { it.copy(project = updatedProject) }
    }

    // --- 设置同步接口 ---

    fun updateApiKey(newKey: String) =
        _state.update { it.copy(globalState = it.globalState.copy(apiKey = newKey, effectiveApiKey = newKey)) }

    fun updateStorageDirState(newPath: String) =
        _state.update { it.copy(globalState = it.globalState.copy(templateStorageDir = newPath)) }

    fun updateMaxRetriesState(count: Int) =
        _state.update { it.copy(globalState = it.globalState.copy(maxRetries = count)) }

    fun updateImageGenCountState(count: Int) =
        _state.update { it.copy(globalState = it.globalState.copy(imageGenCount = count)) }

    fun updateShortcutState(action: ShortcutAction, keyChord: String) {
        _state.update { s ->
            val newShortcuts = s.globalState.shortcuts.toMutableMap()
            newShortcuts[action] = keyChord
            s.copy(globalState = s.globalState.copy(shortcuts = newShortcuts))
        }
    }

    fun setPromptLanguagePref(pref: PromptLanguage) =
        _state.update { it.copy(globalState = it.globalState.copy(promptLangPref = pref)) }
}
