package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.chat.CommonChatStudioConfig
import org.gemini.ui.forge.ui.component.chat.CommonChatStudioContent
import org.gemini.ui.forge.ui.component.chat.CommonConfirmModalOverlay
import org.gemini.ui.forge.ui.component.chat.CommonSystemInstructionDialog
import org.gemini.ui.forge.ui.component.chat.LayoutDiffSummaryCard
import org.gemini.ui.forge.ui.component.chat.mention.MentionHoverPreviewConfig
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.ai.studio.component.StudioSessionLogDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.findBlockById

import org.gemini.ui.forge.ui.uistudio.component.ChatImageActionRow
import org.gemini.ui.forge.ui.uistudio.dialog.SaveChatImageToAssetDialog
import org.gemini.ui.forge.utils.readLocalFileBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.launch
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.getCurrentTimeMillis

/**
 * UI Studio 右侧边栏嵌入式 AI 对话面板 (Embedded AI Chat Panel)。
 *
 * 遵循一文件一 Composable 铁律与架构规范：
 * - 纯净复用 [CommonChatStudioContent]，与独立大弹窗 [UIProjectAiStudioDialog] 保持 100% 状态与能力对称；
 * - 嵌入右侧边栏，支持随边栏自由拉伸宽度；
 * - 具备外部文档、@ 跨页面引用、悬停预览、工具执行反哺及快照捕获完整能力；
 * - 顶部紧凑操作栏支持人设配置、清空会话与通信抓包。
 *
 * @param viewModel UI Studio 核心业务视图模型
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioEmbeddedChatPanel(
    viewModel: UIStudioViewModel,
    modifier: Modifier = Modifier
) {
    val state = viewModel.state
    val spacing = LocalAppSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val currentSession = state.currentChatSession
    var showTrafficLogDialog by remember { mutableStateOf(false) }
    var pendingSaveImageUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.project?.projectId) {
        viewModel.loadExternalDocs(state.project?.projectId ?: "default")
        viewModel.loadChatSessions()
    }

    val currentModel = GeminiModel.entries.find {
        it.modelName == state.projectAgentConfig.modelName
    } ?: GeminiModel.GEMINI_3_8_FLASH

    val studioConfig = remember(currentModel, state.projectAgentConfig.apiFlavor) {
        CommonChatStudioConfig(
            title = "AI 智能设计助理",
            emptyTipText = "我是当前工程专属 AI 架构师，向我提问页面布局、样式规划或组件生成建议...",
            defaultModel = currentModel,
            defaultApiFlavor = state.projectAgentConfig.apiFlavor,
            showCanvasSnapshotAction = true,
            isEmbeddedMode = true
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
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
                val lastLayoutMsg = currentSession?.messages?.lastOrNull {
                    it.effectiveText.contains("已完成界面排版规划方案") || it.effectiveText.contains("apply_view_layout")
                }
                if (lastLayoutMsg != null) {
                    viewModel.reapplyLayoutFromText(lastLayoutMsg.effectiveText)
                } else {
                    val matchedPage = state.project?.studioPages?.find {
                        it.nameStr.equals(viewName, ignoreCase = true) || it.id.equals(viewName, ignoreCase = true)
                    }
                    if (matchedPage != null) {
                        viewModel.pageManager.selectView(matchedPage.id)
                        Toast.show("已切换至视图 [${matchedPage.nameStr}]", ToastType.SUCCESS)
                    } else {
                        Toast.show("未找到视图: $viewName", ToastType.INFO)
                    }
                }
            },
            mentionState = viewModel.mentionState,
            mentionCandidates = viewModel.getMentionCandidates(viewModel.mentionState.searchQuery),
            hoverPreviewConfig = remember(state.project, state.currentView, state.assetLibrary) {
                MentionHoverPreviewConfig(
                    resolveAssetPathByHash = { hash -> viewModel.assetLibrary.findAssetByHash(hash)?.relativePath },
                    resolveViewBlocks = { viewId -> state.project?.studioPages?.find { it.id == viewId }?.blocks },
                    resolveBlockById = { id ->
                        (state.currentView?.blocks?.findBlockById(id) as? UIStudioBlock)
                            ?: state.project?.studioPages?.asSequence()
                                ?.mapNotNull { it.blocks.findBlockById(id) as? UIStudioBlock }
                                ?.firstOrNull()
                    }
                )
            },
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
            imageActions = { uri ->
                ChatImageActionRow(
                    imageUri = uri,
                    onSaveToAsset = { pendingSaveImageUri = uri },
                    onAddToPage = { targetUri ->
                        coroutineScope.launch {
                            @OptIn(ExperimentalEncodingApi::class)
                            val bytes = if (targetUri.startsWith("data:image")) {
                                val pureB64 = if (targetUri.contains(",")) targetUri.substringAfter(",") else targetUri
                                try { Base64.decode(pureB64) } catch (_: Throwable) { null }
                            } else {
                                readLocalFileBytes(targetUri)
                            } ?: return@launch

                            val defaultName = "ai_img_${getCurrentTimeMillis()}"
                            viewModel.assetLibrary.addChatImageToCurrentView(
                                imageBytes = bytes,
                                fileName = defaultName,
                                width = 200f,
                                height = 200f,
                                subPath = "",
                                isPng = targetUri.contains("png", ignoreCase = true)
                            )
                        }
                    }
                )
            },
            chatNoticeContent = {
                state.lastLayoutUndoSnapshot?.let { snapshot ->
                    LayoutDiffSummaryCard(
                        diffResult = snapshot.diff,
                        onUndo = { viewModel.undoLastLayoutChanges() },
                        modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.extraSmall)
                    )
                }
            },
            topBarActions = {
                // 1. 系统人设配置
                IconButton(
                    onClick = { viewModel.openSystemInstructionDialog() },
                    modifier = Modifier.size(32.dp).tip("配置当前项目 AI 系统人设指令")
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "系统指令",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 2. 清空当前会话
                IconButton(
                    onClick = { viewModel.requestClearCurrentSession() },
                    enabled = (currentSession?.messages?.isNotEmpty() == true) && !state.isAiChatSending,
                    modifier = Modifier.size(32.dp).tip("清空当前会话历史")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清空会话",
                        modifier = Modifier.size(19.dp),
                        tint = if (currentSession?.messages?.isNotEmpty() == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }

                // 3. 通信抓包与报文日志
                IconButton(
                    onClick = { showTrafficLogDialog = true },
                    modifier = Modifier.size(32.dp).tip("查看网络通信报文与 API 真实日志")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "报文档案",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 独立通信报文与 API 真实日志查看器
        if (showTrafficLogDialog) {
            StudioSessionLogDialog(
                scopeId = viewModel.trafficScopeId,
                sessionId = currentSession?.id ?: "default_sess",
                trafficStore = viewModel.trafficStore,
                onDismiss = { showTrafficLogDialog = false }
            )
        }

        // 项目系统人设指令弹窗（独立全景居中大弹窗，彻底脱离右侧栏物理宽度限制）
        if (state.isSystemInstructionDialogVisible) {
            var defaultInstruction by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                defaultInstruction = PromptManager.getUiStudioAgentPrompt()
            }

            CommonSystemInstructionDialog(
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

        // 清空会话消息二次确认模态覆层（零系统窗口竞争）
        if (state.isClearingCurrentSession) {
            CommonConfirmModalOverlay(
                title = "清空对话记录确认",
                message = "确定要清空当前会话的全部聊天记录吗？此操作将立即覆盖磁盘文件，不可恢复。",
                confirmText = "确认清空",
                isDestructive = true,
                onConfirm = { viewModel.confirmClearCurrentSession() },
                onDismiss = { viewModel.cancelClearCurrentSession() }
            )
        }

        // 保存图片至项目素材库对话框
        val saveUri = pendingSaveImageUri
        if (saveUri != null) {
            val autoName = remember(saveUri) { "ai_img_${getCurrentTimeMillis()}" }
            SaveChatImageToAssetDialog(
                isOpen = true,
                initialFileName = autoName,
                folders = state.assetLibrary.folders,
                onDismissRequest = { pendingSaveImageUri = null },
                onConfirmSave = { fileName, subPath ->
                    coroutineScope.launch {
                        @OptIn(ExperimentalEncodingApi::class)
                        val bytes = if (saveUri.startsWith("data:image")) {
                            val pureB64 = if (saveUri.contains(",")) saveUri.substringAfter(",") else saveUri
                            try { Base64.decode(pureB64) } catch (_: Throwable) { null }
                        } else {
                            readLocalFileBytes(saveUri)
                        }
                        if (bytes != null) {
                            val isPng = saveUri.contains("png", ignoreCase = true)
                            viewModel.assetLibrary.saveChatImageToAssetLibrary(
                                imageBytes = bytes,
                                fileName = fileName,
                                subPath = subPath,
                                isPng = isPng
                            )
                        }
                        pendingSaveImageUri = null
                    }
                }
            )
        }
    }
}
