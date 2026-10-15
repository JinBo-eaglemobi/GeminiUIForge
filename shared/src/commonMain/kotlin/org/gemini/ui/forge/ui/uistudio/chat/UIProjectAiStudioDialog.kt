package org.gemini.ui.forge.ui.uistudio.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.ui.component.chat.CommonChatStudioConfig
import org.gemini.ui.forge.ui.component.chat.CommonChatStudioContent
import org.gemini.ui.forge.ui.component.chat.CommonSystemInstructionModalOverlay
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioSessionLogDialog
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel

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
    val currentSession = state.currentChatSession
    var showTrafficLogDialog by remember { mutableStateOf(false) }

    val currentModel = GeminiModel.entries.find {
        it.modelName == state.projectAgentConfig.modelName
    } ?: GeminiModel.GEMINI_3_8_FLASH

    val studioConfig = CommonChatStudioConfig(
        title = "UI Studio 智能设计助理",
        emptyTipText = "我是当前工程专属 AI 架构师，向我提问页面布局、样式规划或组件生成建议...",
        defaultModel = currentModel,
        defaultApiFlavor = state.projectAgentConfig.apiFlavor
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                    selectedApiFlavor = state.projectAgentConfig.apiFlavor,
                    onApiFlavorSelected = { viewModel.updateProjectApiFlavor(it) },
                    onSendMessage = { text, attachments ->
                        viewModel.sendChatMessage(text, attachments)
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
                    mentionState = viewModel.mentionState,
                    mentionCandidates = viewModel.getMentionCandidates(viewModel.mentionState.searchQuery),
                    config = studioConfig,
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
                                Icons.Default.ReceiptLong,
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
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp).tip("关闭工作室")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                        }
                    }
                )
            }

            // 项目系统人设指令模态覆盖层（绝对居中置顶，零窗口竞争冲突）
            if (state.isSystemInstructionDialogVisible) {
                CommonSystemInstructionModalOverlay(
                    currentInstruction = state.projectAgentConfig.systemPrompt,
                    defaultInstruction = "你是一位精通游戏 UI/UX 布局设计的资深架构师，协助开发者在 UI Studio 中完成视图排版、图元尺寸规划与素材建议。",
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
        }
    }
}
