package org.gemini.ui.forge.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 环境安装/卸载命令行交互式请示协调器 (CLI Interactive Prompt Coordinator)
 *
 * 负责在后台环境安装/卸载流执行过程中，当遇到缺失前置依赖（如缺少 WinGet、缺少底层 VCLibs/WindowsAppRuntime 运行库）
 * 或检测到本地已缓存安装包需要用户决策时，挂起当前协程并在命令输出终端底部呈现传统 CLI 风格的 [Y/n] 交互确认栏，
 * 等待用户点击「同意并继续 (Y)」或「取消操作 (N)」后继续推进。
 */
object EnvCliPromptCoordinator {

    private val deferredMap = mutableMapOf<String, CompletableDeferred<Boolean>>()

    private val _pendingPrompts = MutableStateFlow<Map<String, String>>(emptyMap())

    /** 当前正在等待用户确认的请示映射表：Key 为环境项名称（如 "python"），Value 为请示问题文本 */
    val pendingPrompts: StateFlow<Map<String, String>> = _pendingPrompts.asStateFlow()

    /** 别名兼容 */
    val activePrompts: StateFlow<Map<String, String>> = pendingPrompts

    /**
     * 挂起当前安装/卸载协程，向指定环境项的命令终端发出请示并等待用户选择 [Y/n]
     *
     * @param itemName 环境依赖项标识（如 "python", "rembg" 等）
     * @param promptMessage 请示说明文本
     * @return 用户选择同意返回 true，选择取消返回 false
     */
    suspend fun awaitUserDecision(itemName: String, promptMessage: String): Boolean {
        val key = itemName.lowercase()
        // 若存在旧的未决请示先取消
        deferredMap.remove(key)?.cancel()

        val deferred = CompletableDeferred<Boolean>()
        deferredMap[key] = deferred
        _pendingPrompts.update { it + (key to promptMessage) }

        return try {
            deferred.await()
        } finally {
            deferredMap.remove(key)
            _pendingPrompts.update { it - key }
        }
    }

    /** 别名兼容 */
    suspend fun requestPrompt(itemName: String, promptMessage: String): Boolean =
        awaitUserDecision(itemName, promptMessage)

    /**
     * 由 UI 界面响应用户点击 [Y / 同意并继续] 或 [N / 取消操作] 后提交裁决结果
     */
    fun submitDecision(itemName: String, accepted: Boolean) {
        val key = itemName.lowercase()
        deferredMap.remove(key)?.complete(accepted)
        _pendingPrompts.update { it - key }
    }

    /** 别名兼容 */
    fun respond(itemName: String, accepted: Boolean) = submitDecision(itemName, accepted)

    /**
     * 取消并清理指定环境项的挂起请示
     */
    fun cancelPrompt(itemName: String) {
        val key = itemName.lowercase()
        deferredMap.remove(key)?.complete(false)
        _pendingPrompts.update { it - key }
    }
}
