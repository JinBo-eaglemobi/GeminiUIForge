package org.gemini.ui.forge.ui.uistudio.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.component.dialog.draggableDialogHandle
import org.gemini.ui.forge.ui.component.chat.CommonChatStudioConfig
import org.gemini.ui.forge.ui.component.chat.CommonChatStudioContent
import org.gemini.ui.forge.ui.component.chat.CommonSystemInstructionModalOverlay
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioSessionLogDialog
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.dialog.ApplyLayoutConfirmDialog
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.ui.component.ToastType

/**
 * UI Studio 专属项目级 AI 智能工作台全景弹窗
 *
 * 彻底解耦私有业务，100% 装配全新的通用对话组件库 [CommonChatStudioContent]。
 * 继承响应式自适应布局（支持宽屏双列与窄屏抽屉）与全部高级自定义插槽，
 * 聊天数据与专属配置完全持久化至项目本地目录。
 */
@Composable
fun UIProjectAiStudioDialog(
    viewModel: UIStudioViewModel,
    onDismiss: () -> Unit
) {
    val state = viewModel.state
    val spacing = LocalAppSpacing.current
    val currentSession = state.currentChatSession
    var showTrafficLogDialog by remember { mutableStateOf(false) }

    val currentModel = GeminiModel.entries.find {
        it.modelName == state.projectAgentConfig.modelName
    } ?: GeminiModel.GEMINI_3_8_FLASH

    val studioConfig = CommonChatStudioConfig(
        title = "UI Studio 智能设计助理",
        emptyTipText = "我是当前工程专属 AI 架构师，向我提问页面布局、样式规划或组件生成建议...",
        defaultModel = currentModel,
        defaultApiFlavor = state.projectAgentConfig.apiFlavor,
        showCanvasSnapshotAction = true
    )

    // LIFO 模态栈智能分流器：按视觉层级自顶向下逐层消费 Esc 键关闭请求
    val handleModalDismiss: () -> Unit = {
        when {
            state.isSystemInstructionDialogVisible -> {
                viewModel.closeSystemInstructionDialog()
            }
            state.isClearingCurrentSession -> {
                viewModel.cancelClearCurrentSession()
            }
            showTrafficLogDialog -> {
                showTrafficLogDialog = false
            }
            state.pendingLayoutResult != null -> {
                viewModel.cancelApplyLayout()
            }
            else -> {
                onDismiss()
            }
        }
    }

    AppDraggableDialog(
        onDismissRequest = handleModalDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) { draggableState ->
        LaunchedEffect(Unit) {
            viewModel.loadExternalDocs(state.project?.projectId ?: "default")
            viewModel.loadChatSessions()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 6.dp,
                shadowElevation = 24.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                CommonChatStudioContent(
                    sessions = state.chatSessions,
                    currentSessionId = state.currentChatSessionId,
                    onSessionSelected = { viewModel.selectChatSession(it) },
                    onNewSession = { viewModel.createNewChatSession() },
                    onDeleteSession = { viewModel.deleteChatSession(it) },
                    messages = currentSession?.messages ?: emptyList(),
                    isGenerating = state.isAiChatSending,
                    streamingText = state.streamingAiText,
                    selectedModel = currentModel,
                    onModelSelected = { viewModel.updateProjectModel(it) },
                    currentThinkingLevel = state.projectAgentConfig.thinkingLevel,
                    onThinkingLevelSelected = { viewModel.updateProjectThinkingLevel(it) },
                    selectedApiFlavor = state.projectAgentConfig.apiFlavor,
                    onApiFlavorSelected = { viewModel.updateProjectApiFlavor(it) },
                    onSendMessage = { text, attachments ->
                        viewModel.sendChatMessage(text, attachments)
                    },
                    onStopGeneration = {
                        viewModel.cancelCurrentChatGeneration()
                    },
                    designMemoryContext = currentSession?.designMemoryContext,
                    onCompressRequested = {
                        viewModel.compressCurrentSession()
                    },
                    onSwitchVariant = { messageId, variantIndex ->
                        viewModel.switchChatMessageVariant(messageId, variantIndex)
                    },
                    onRegenerate = { messageId ->
                        viewModel.regenerateChatMessage(messageId)
                    },
                    onDeleteMessage = { messageId ->
                        viewModel.deleteChatMessage(messageId)
                    },
                    onOpenViewRequested = { viewName ->
                        val matchedPage = state.project?.pages?.find { it.nameStr.equals(viewName, ignoreCase = true) }
                        if (matchedPage != null) {
                            viewModel.selectView(matchedPage.id)
                            onDismiss()
                            Toast.show("已切换至视图 [${matchedPage.nameStr}]", ToastType.SUCCESS)
                        } else {
                            Toast.show("未找到视图: $viewName", ToastType.INFO)
                        }
                    },
                    mentionState = viewModel.mentionState,
                    mentionCandidates = viewModel.getMentionCandidates(viewModel.mentionState.searchQuery),
                    onFilesDropped = { paths ->
                        paths.forEach { viewModel.handleFileDropped(it) }
                    },
                    onDeleteExternalDoc = { viewModel.deleteExternalDoc(it) },
                    activeSkills = viewModel.activeSkills,
                    allSkills = viewModel.allSkills,
                    enabledSkillIds = viewModel.enabledSkillIds,
                    onToggleSkill = { skillId, isEnabled -> viewModel.toggleSkill(skillId, isEnabled) },
                    onRefreshSkills = { viewModel.refreshSkills(userInitiated = true) },
                    onOpenSkillsDirectory = { viewModel.openSkillsDirectory() },
                    skillMode = viewModel.skillMode,
                    onSkillModeChanged = { viewModel.skillMode = it },
                    onFallbackFlashModelRequested = { msg -> viewModel.fallbackToFlashAndRetry(msg) },
                    onSwitchProtocolRequested = { msg -> viewModel.fallbackProtocolAndRetry(msg) },
                    onCaptureCanvasSnapshotRequested = { mode, onAttachmentReady ->
                        viewModel.captureAndAttachCanvas(mode) { attachment ->
                            onAttachmentReady(attachment)
                        }
                    },
                    isCapturingCanvasSnapshot = viewModel.isCapturingCanvasSnapshot,
                    viewName = state.currentView?.nameStr ?: "",
                    viewSizeText = if (state.currentView != null) "${state.currentView?.width?.toInt() ?: 0} × ${state.currentView?.height?.toInt() ?: 0}" else "",
                    readyAssetCount = state.assetLibrary.items.size,
                    config = studioConfig,
                    chatNoticeContent = {
                        state.lastLayoutUndoSnapshot?.let { snapshot ->
                            org.gemini.ui.forge.ui.component.chat.LayoutDiffSummaryCard(
                                diffResult = snapshot.diff,
                                onUndo = { viewModel.undoLastLayoutChanges() },
                                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.extraSmall)
                            )
                        }
                    },
                    topBarActions = {
                        // 1. 当前视图标签胶囊
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "🖼️ 视图: ${state.currentView?.nameStr ?: "未选视图"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // 2. 项目系统人设指令弹窗入口
                        IconButton(
                            onClick = { viewModel.openSystemInstructionDialog() },
                            modifier = Modifier.size(32.dp).tip("配置当前项目 AI 系统人设指令")
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = "系统指令",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 3. 真实网络通信日志入口
                        IconButton(
                            onClick = { showTrafficLogDialog = true },
                            modifier = Modifier.size(32.dp).tip("查看网络通信报文与 API 真实日志")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = "通信报文",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 4. 清空当前会话消息按钮（二次确认保护）
                        if (currentSession != null && currentSession.messages.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.requestClearCurrentSession() },
                                modifier = Modifier.size(32.dp).tip("清空当前对话记录")
                            ) {
                                Icon(
                                    Icons.Default.CleaningServices,
                                    contentDescription = "清空对话",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // 5. 关闭工作室按钮
                        IconButton(
                            onClick = {
                                if (state.isSystemInstructionDialogVisible) {
                                    viewModel.closeSystemInstructionDialog()
                                }
                                onDismiss()
                            },
                            modifier = Modifier.size(32.dp).tip("关闭工作室")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                        }
                    },
                    draggableModifier = Modifier.draggableDialogHandle(draggableState)
                )
            }

            // 项目系统人设指令模态覆盖层（绝对居中置顶，零窗口竞争冲突）
            if (state.isSystemInstructionDialogVisible) {
                var defaultInstruction by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    defaultInstruction = PromptManager.getUiStudioAgentPrompt()
                }

                CommonSystemInstructionModalOverlay(
                    currentInstruction = state.projectAgentConfig.systemPrompt.ifBlank { defaultInstruction },
                    defaultInstruction = defaultInstruction,
                    onConfirm = { newPrompt ->
                        viewModel.updateProjectSystemPrompt(newPrompt)
                    },
                    onDismiss = {
                        viewModel.closeSystemInstructionDialog()
                    }
                )
            }

            // 清空会话消息二次确认弹窗
            if (state.isClearingCurrentSession) {
                AppConfirmDialog(
                    title = "清空对话记录确认",
                    message = "确定要清空当前会话的全部聊天记录吗？此操作将立即持久化覆盖磁盘文件，不可恢复。",
                    confirmText = "确认清空",
                    isDestructive = true,
                    onConfirm = { viewModel.confirmClearCurrentSession() },
                    onDismiss = { viewModel.cancelClearCurrentSession() }
                )
            }

            // 真实网络通信报文与 API 日志查看弹窗
            if (showTrafficLogDialog) {
                StudioSessionLogDialog(
                    scopeId = viewModel.trafficScopeId,
                    sessionId = currentSession?.id ?: "default_sess",
                    trafficStore = viewModel.trafficStore,
                    onDismiss = { showTrafficLogDialog = false }
                )
            }

            // AI 界面排版方案应用确认弹窗
            val pendingLayout = state.pendingLayoutResult
            if (pendingLayout != null) {
                ApplyLayoutConfirmDialog(
                    layoutResult = pendingLayout,
                    currentViewName = state.currentView?.nameStr ?: "当前视图",
                    onDismiss = { viewModel.cancelApplyLayout() },
                    onConfirm = { replaceCurrent, customName ->
                        viewModel.confirmApplyLayout(replaceCurrent, customName)
                    }
                )
            }
        }
    }
}
