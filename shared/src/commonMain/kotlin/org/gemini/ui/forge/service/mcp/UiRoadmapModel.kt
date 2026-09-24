package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * 界面类型枚举
 */
@Serializable
enum class ScreenType {
    TEMPLATE_LIST,       // 模板列表界面 (主页)
    PROJECT_WORKSPACE,   // 模板编辑工作区界面
    VISUAL_CHAT_STUDIO,  // AI 视觉工作室界面
    MCP_CONSOLE          // MCP 控制台界面
}

/**
 * 弹窗定义模型
 */
@Serializable
data class DialogDescriptor(
    val id: String,
    val title: String,
    val description: String,
    val dismissActionNodeId: String,
    val confirmActionNodeId: String? = null,
    val hasUnsavedRisk: Boolean = false
)

/**
 * 界面节点描述符
 */
@Serializable
data class ScreenDescriptor(
    val type: ScreenType,
    val name: String,
    val description: String,
    val enterActions: List<String>,
    val exitActions: List<String>,
    val possibleDialogs: List<DialogDescriptor>
)

/**
 * 全局 UI 路线图数据模型
 */
@Serializable
data class UiRoadmapData(
    val currentScreen: ScreenType,
    val activeDialogs: List<DialogDescriptor>,
    val canDirectNavigate: Boolean,
    val currentContextSummary: String,
    val screens: List<ScreenDescriptor>,
    val isUiFollowEnabled: Boolean = false,
    val isAiExecuting: Boolean = false,
    val generatingProjects: Map<String, String> = emptyMap()
)

/**
 * UI 节点交互执行器回调接口
 */
interface UiNodeActionHandler {
    suspend fun clickNode(nodeId: String): Boolean
    suspend fun setInputText(nodeId: String, text: String): Boolean
}

/**
 * UI 路线图与当前状态单例注册中心
 * 跨平台纯 Kotlin 实现，用于 MCP 获取路线图与下发交互事件
 */
object UiRoadmapRegistry {
    private val mutex = Mutex()

    private val _currentScreen = MutableStateFlow(ScreenType.TEMPLATE_LIST)
    val currentScreen: StateFlow<ScreenType> = _currentScreen.asStateFlow()

    private val _activeDialogs = MutableStateFlow<List<DialogDescriptor>>(emptyList())
    val activeDialogs: StateFlow<List<DialogDescriptor>> = _activeDialogs.asStateFlow()

    // AI 视觉跟随模式开关 (true: 自动平滑进入工作区并模拟真实操作; false: 后台静默执行)
    private val _isUiFollowEnabled = MutableStateFlow(false)
    val isUiFollowEnabled: StateFlow<Boolean> = _isUiFollowEnabled.asStateFlow()

    fun setUiFollowEnabled(enabled: Boolean) {
        _isUiFollowEnabled.value = enabled
    }

    // AI 自动化任务执行状态 (true 时在前台跟随模式下手势阻断主内容区点击，防误触)
    private val _isAiExecuting = MutableStateFlow(false)
    val isAiExecuting: StateFlow<Boolean> = _isAiExecuting.asStateFlow()

    fun setAiExecuting(executing: Boolean) {
        _isAiExecuting.value = executing
    }

    // 当前正在由 AI 后台反向生成的模板工程集合 Map<projectName, statusDescription>
    private val _generatingProjects = MutableStateFlow<Map<String, String>>(emptyMap())
    val generatingProjects: StateFlow<Map<String, String>> = _generatingProjects.asStateFlow()

    fun markProjectGenerating(projectName: String, status: String = "正在由 AI 逆向生成图元工程...") {
        _generatingProjects.update { current ->
            current + (projectName to status)
        }
    }

    fun unmarkProjectGenerating(projectName: String) {
        val normalized = projectName.trim().replace(" ", "_")
        _generatingProjects.update { current ->
            current.filterKeys { k ->
                k != projectName && !k.trim().replace(" ", "_").equals(normalized, ignoreCase = true)
            }
        }
    }

    fun isProjectGenerating(projectName: String): Boolean {
        val normalized = projectName.trim().replace(" ", "_")
        return _generatingProjects.value.any { (k, _) ->
            k == projectName || k.trim().replace(" ", "_").equals(normalized, ignoreCase = true)
        }
    }

    private var currentTemplateId: String? = null
    private var hasUnsavedWorkspaceChanges: Boolean = false

    // 节点交互处理器
    private var actionHandler: UiNodeActionHandler? = null

    fun setActionHandler(handler: UiNodeActionHandler?) {
        actionHandler = handler
    }

    suspend fun clickNode(nodeId: String): Boolean {
        val handler = actionHandler ?: return false
        return handler.clickNode(nodeId)
    }

    suspend fun setInputText(nodeId: String, text: String): Boolean {
        val handler = actionHandler ?: return false
        return handler.setInputText(nodeId, text)
    }

    suspend fun updateCurrentScreen(screen: ScreenType, templateId: String? = null) {
        mutex.withLock {
            _currentScreen.value = screen
            currentTemplateId = templateId
        }
    }

    suspend fun setUnsavedWorkspaceChanges(hasChanges: Boolean) {
        mutex.withLock {
            hasUnsavedWorkspaceChanges = hasChanges
        }
    }

    suspend fun pushDialog(dialog: DialogDescriptor) {
        mutex.withLock {
            if (_activeDialogs.value.none { it.id == dialog.id }) {
                _activeDialogs.value = _activeDialogs.value + dialog
            }
        }
    }

    suspend fun removeDialog(dialogId: String) {
        mutex.withLock {
            _activeDialogs.value = _activeDialogs.value.filter { it.id != dialogId }
        }
    }

    suspend fun getFullRoadmap(): UiRoadmapData {
        return mutex.withLock {
            val screens = listOf(
                ScreenDescriptor(
                    type = ScreenType.TEMPLATE_LIST,
                    name = "模板列表界面 (主页)",
                    description = "展示全部游戏 UI 模板，支持新建、导入、搜索、删除及双击或点击进入编辑",
                    enterActions = listOf("从工作区点击顶部返回按钮，若有未保存修改需确认保存或放弃"),
                    exitActions = listOf("点击模板卡片进入工作区", "点击右下角 MCP 图标打开控制台"),
                    possibleDialogs = listOf(
                        DialogDescriptor(
                            id = "dialog_delete_confirm",
                            title = "删除确认弹窗",
                            description = "删除模板时的二次确认",
                            dismissActionNodeId = "btn_cancel_delete",
                            confirmActionNodeId = "btn_confirm_delete"
                        ),
                        DialogDescriptor(
                            id = "dialog_new_template",
                            title = "新建模板弹窗",
                            description = "输入名称、宽高及可选参考图创建模板",
                            dismissActionNodeId = "btn_cancel_create",
                            confirmActionNodeId = "btn_submit_create"
                        )
                    )
                ),
                ScreenDescriptor(
                    type = ScreenType.PROJECT_WORKSPACE,
                    name = "模板编辑工作区界面",
                    description = "核心画布编辑、左侧图层树、右侧属性面板、顶部工具栏与对齐吸附",
                    enterActions = listOf("从模板列表点击卡片进入", "从生图后自动载入进入"),
                    exitActions = listOf("点击顶部返回按钮回到模板列表 (可能触发未保存弹窗)", "点击工具栏打开 AI 视觉工作室"),
                    possibleDialogs = listOf(
                        DialogDescriptor(
                            id = "dialog_unsaved_changes",
                            title = "未保存修改警告弹窗",
                            description = "离开工作区前存在未保存脏数据时强制弹出，必须点击「保存」或「放弃」才能离开，直接强行跳转会丢数据！",
                            dismissActionNodeId = "btn_cancel_leave",
                            confirmActionNodeId = "btn_save_and_leave",
                            hasUnsavedRisk = true
                        ),
                        DialogDescriptor(
                            id = "dialog_batch_gen",
                            title = "批量生图弹窗",
                            description = "勾选需要生图的模块并批量提交",
                            dismissActionNodeId = "btn_close_batch_gen"
                        ),
                        DialogDescriptor(
                            id = "dialog_image_editor",
                            title = "参考图/资产编辑裁切弹窗",
                            description = "微调切片与参考区域",
                            dismissActionNodeId = "btn_close_editor"
                        )
                    )
                ),
                ScreenDescriptor(
                    type = ScreenType.VISUAL_CHAT_STUDIO,
                    name = "AI 视觉工作室界面",
                    description = "多轮会话式视觉重塑、生图与资产实时应用",
                    enterActions = listOf("在工作区点击「AI视觉工作室」按钮打开"),
                    exitActions = listOf("点击右上角关闭按钮或应用生成的图片"),
                    possibleDialogs = listOf(
                        DialogDescriptor(
                            id = "dialog_prompt_optimize",
                            title = "提示词优化弹窗",
                            description = "AI 优化提示词确认",
                            dismissActionNodeId = "btn_close_prompt_opt"
                        )
                    )
                ),
                ScreenDescriptor(
                    type = ScreenType.MCP_CONSOLE,
                    name = "MCP 服务中心与通信控制台",
                    description = "查看 MCP 工具字典、实时报文流动监控及配置",
                    enterActions = listOf("点击全局右下角 MCP 悬浮胶囊"),
                    exitActions = listOf("点击右上角关闭按钮"),
                    possibleDialogs = emptyList()
                )
            )

            val current = _currentScreen.value
            val active = _activeDialogs.value
            val canDirect = active.isEmpty() && (!hasUnsavedWorkspaceChanges || current != ScreenType.PROJECT_WORKSPACE)

            UiRoadmapData(
                currentScreen = current,
                activeDialogs = active,
                canDirectNavigate = canDirect,
                currentContextSummary = buildString {
                    append("当前所在界面: ").append(current.name)
                    if (currentTemplateId != null) {
                        append(" (项目: ").append(currentTemplateId).append(")")
                    }
                    if (hasUnsavedWorkspaceChanges) {
                        append(" [存在未保存的修改 - 离开前必须处理未保存确认弹窗]")
                    }
                    if (active.isNotEmpty()) {
                        append(" [当前前台激活弹窗: ").append(active.joinToString { it.title }).append("，注意必须先关闭或处理弹窗再执行后续页面流转]")
                    }
                },
                screens = screens,
                isUiFollowEnabled = _isUiFollowEnabled.value,
                isAiExecuting = _isAiExecuting.value,
                generatingProjects = _generatingProjects.value
            )
        }
    }
}
