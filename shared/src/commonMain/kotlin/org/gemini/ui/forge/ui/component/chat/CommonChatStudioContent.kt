package org.gemini.ui.forge.ui.component.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.ChatMentionTag
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.state.MentionState
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionPayload
import org.gemini.ui.forge.ui.component.chat.drop.onFileDrop
import org.gemini.ui.forge.ui.component.VerticalSplitter
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.doc.DocumentPreviewDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 附件文档与详情预览数据载荷
 */
private data class DocumentPreviewData(
    val title: String,
    val filePath: String? = null,
    val content: String? = null
)

/**
 * 全场景通用 AI 对话工作室响应式纯净内容容器
 *
 * 核心架构特性：
 * 1. 彻底解耦 Dialog，纯 Composable 容器，可在全屏页面、侧边栏、局部卡片或弹窗中自由嵌入；
 * 2. 响应式自适应布局：根据宿主可用宽度 [BoxWithConstraints] 智能分流：
 *    - 宽屏模式 (>= 640dp)：左右双栏并列（左侧会话侧栏 + 右侧主对话区）；
 *    - 窄屏/侧栏模式 (< 640dp)：会话侧栏自动折叠为覆盖层切换，主对话区独占 100% 宽度，杜绝侧栏被挤压；
 * 3. 完整插槽可定制化：开放 [topBarActions]、[messageActions]、[inputLeadingActions]、[inputTrailingActions] 插槽。
 *
 * @param sessions 全部会话列表
 * @param currentSessionId 当前选中的会话 ID
 * @param onSessionSelected 会话选择回调
 * @param onNewSession 新建会话回调
 * @param onDeleteSession 删除会话回调
 * @param messages 当前活跃会话的消息列表
 * @param isGenerating 是否正在生成中
 * @param streamingText 实时流式打字文本
 * @param selectedModel 当前选中的 Gemini 模型
 * @param onModelSelected 模型选择回调
 * @param selectedApiFlavor 当前选中的通信协议
 * @param onApiFlavorSelected 协议选择回调
 * @param onSendMessage 触发发送消息回调 (text, attachments)
 * @param config 通用配置参数
 * @param onImageClick 点击图片放大回调
 * @param topBarActions 顶部导航栏右侧自定义动作插槽（如人设指令、状态指示等）
 * @param messageActions 每条 AI 消息气泡底部的自定义动作插槽（如应用到图元、保存至素材库等）
 * @param inputLeadingActions 输入控制栏前置自定义动作插槽
 * @param inputTrailingActions 输入控制栏后置自定义动作插槽
 * @param modifier 修饰符
 */
@Composable
fun CommonChatStudioContent(
    sessions: List<VisualChatSession>,
    currentSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    messages: List<VisualChatMessage>,
    isGenerating: Boolean,
    streamingText: String = "",
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    selectedApiFlavor: ApiFlavor,
    onApiFlavorSelected: (ApiFlavor) -> Unit,
    onSendMessage: (text: String, attachments: List<CommonChatAttachment>) -> Unit,
    modifier: Modifier = Modifier,
    config: CommonChatStudioConfig = CommonChatStudioConfig(),
    onImageClick: ((String) -> Unit)? = null,
    onSwitchVariant: ((messageId: String, variantIndex: Int) -> Unit)? = null,
    onRegenerate: ((messageId: String) -> Unit)? = null,
    onDeleteMessage: ((messageId: String) -> Unit)? = null,
    onOpenViewRequested: ((String) -> Unit)? = null,
    mentionState: MentionState? = null,
    mentionCandidates: List<MentionItem> = emptyList(),
    onFilesDropped: ((List<String>) -> Unit)? = null,
    onDeleteExternalDoc: ((filePath: String) -> Unit)? = null,
    activeSkills: List<UiStudioSkill> = emptyList(),
    allSkills: List<UiStudioSkill> = emptyList(),
    enabledSkillIds: Set<String> = emptySet(),
    onToggleSkill: (String, Boolean) -> Unit = { _, _ -> },
    skillMode: SkillActivationMode = SkillActivationMode.AUTO,
    onSkillModeChanged: (SkillActivationMode) -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    messageActions: @Composable RowScope.(message: VisualChatMessage) -> Unit = {},
    inputLeadingActions: @Composable RowScope.() -> Unit = {},
    inputTrailingActions: @Composable RowScope.() -> Unit = {}
) {
    val spacing = LocalAppSpacing.current
    val density = LocalDensity.current
    val currentSession = remember(sessions, currentSessionId) {
        sessions.find { it.id == currentSessionId }
    }

    // 窄屏抽屉式会话覆盖层控制
    var isNarrowSessionDrawerOpen by remember { mutableStateOf(false) }

    // 沉浸式图片灯箱激活状态（内置开箱即用）
    var lightboxImageUri by remember { mutableStateOf<String?>(null) }

    // 附件文档/详情预览弹窗数据载荷
    var previewDocumentData by remember { mutableStateOf<DocumentPreviewData?>(null) }

    // 宽屏模式下会话侧栏拖拽宽度（自由拖拽，安全范围 180dp ~ 420dp，默认 240dp）
    var sidebarWidthDp by remember { mutableStateOf(240.dp) }

    // 重新编辑消息回填至输入框的中间状态
    var inputTextToInject by remember { mutableStateOf<String?>(null) }

    // 全局拖拽文件透传至底部输入框附件的中间状态
    var filesToInject by remember { mutableStateOf<List<String>?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onFileDrop(
                onDragStateChanged = {},
                onFilesDropped = { paths ->
                    filesToInject = paths
                }
            )
    ) {
        val studioMaxHeight = maxHeight
        val isNarrowMode = config.enableNarrowModeAutoFold && maxWidth < config.narrowBreakpointDp.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // 宽屏模式：左侧可拖拽会话侧栏
            if (!isNarrowMode) {
                CommonChatSessionSidebar(
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    onSessionSelected = onSessionSelected,
                    onNewSession = onNewSession,
                    onDeleteSession = onDeleteSession,
                    config = config,
                    modifier = Modifier.width(sidebarWidthDp)
                )

                // 纵向拖拽分割线（遵循 column-resizer 规范）
                VerticalSplitter(
                    onDrag = { deltaPx ->
                        val deltaDp = with(density) { deltaPx.toDp() }
                        sidebarWidthDp = (sidebarWidthDp + deltaDp).coerceIn(180.dp, 420.dp)
                    }
                )
            }

            // 主对话区（独占整宽或自适应右半区）
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 顶部标题栏
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = spacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                // 窄屏模式下的会话历史抽屉切换入口
                                if (isNarrowMode) {
                                    IconButton(
                                        onClick = { isNarrowSessionDrawerOpen = !isNarrowSessionDrawerOpen },
                                        modifier = Modifier.size(32.dp).tip("切换历史会话")
                                    ) {
                                        Icon(
                                            Icons.Default.Menu,
                                            contentDescription = "会话菜单",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = config.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                currentSession?.let { sess ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = sess.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // 顶部自定义动作插槽
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                topBarActions()
                            }
                        }
                    }

                    // 2. 中间消息气泡流
                    CommonChatMessageList(
                        messages = messages,
                        isGenerating = isGenerating,
                        sessionId = currentSessionId,
                        streamingText = streamingText,
                        emptyTipText = config.emptyTipText,
                        onImageClick = { clickedUri ->
                            if (onImageClick != null) {
                                onImageClick(clickedUri)
                            } else {
                                lightboxImageUri = clickedUri
                            }
                        },
                        onVariantIndexChanged = { msg, newIdx ->
                            onSwitchVariant?.invoke(msg.id, newIdx)
                        },
                        onRegenerateRequested = { msg ->
                            onRegenerate?.invoke(msg.id)
                        },
                        onDeleteRequested = { msg ->
                            onDeleteMessage?.invoke(msg.id)
                        },
                        onEditRequested = { textToEdit ->
                            inputTextToInject = textToEdit
                        },
                        onOpenViewRequested = onOpenViewRequested,
                        onMentionClick = { tag ->
                            val rawPath = tag.path.orEmpty()
                            val rawTitle = tag.title.trim()

                            // 1. 判断是否是素材库全量清单 (无论是新发送的标记还是历史消息的标题)
                            val isAllAssetsManifest = rawTitle.contains("全部素材库清单", ignoreCase = true) ||
                                                      rawTitle.contains("All Assets", ignoreCase = true) ||
                                                      rawPath.equals("all_assets_manifest", ignoreCase = true) ||
                                                      rawPath.equals("ALL", ignoreCase = true)

                            if (isAllAssetsManifest) {
                                val assetItems = mentionCandidates.filter { it.category == MentionCategory.ASSET && it.id != "all_assets_manifest" }
                                val sb = StringBuilder()
                                sb.appendLine("# 📦 项目素材库切片全量清单\n")
                                sb.appendLine("> 共计 **${assetItems.size}** 个已切片资产，可直接拼装到游戏画布各图元。\n")
                                sb.appendLine("| 序号 | 切片文件名 | 尺寸 (WxH) | 智能推断角色 | 磁盘相对路径 |")
                                sb.appendLine("|:---:|:---|:---:|:---|:---|")
                                assetItems.forEachIndexed { index, item ->
                                    val p = item.payload as? MentionPayload.AssetPayload
                                    val dims = if (p != null && p.width > 0 && p.height > 0) "${p.width}x${p.height}" else "未知"
                                    val role = item.subtitle?.substringAfterLast("·")?.trim().orEmpty().ifBlank { "普通切片" }
                                    val path = p?.filePath.orEmpty().ifBlank { item.subtitle.orEmpty() }
                                    sb.appendLine("| ${index + 1} | `${item.title.removePrefix("📦").trim()}` | $dims | $role | `$path` |")
                                }
                                previewDocumentData = DocumentPreviewData(
                                    title = "项目素材库清单 (共 ${assetItems.size} 项)",
                                    content = sb.toString()
                                )
                                return@CommonChatMessageList
                            }

                            // 2. 匹配候选列表尝试自愈真实实体 (兼容历史消息丢失路径的自愈)
                            val matchedCandidate = mentionCandidates.find {
                                it.title.equals(rawTitle, ignoreCase = true) ||
                                it.title.contains(rawTitle, ignoreCase = true) ||
                                rawTitle.contains(it.title, ignoreCase = true) ||
                                (it.payload as? MentionPayload.DocPayload)?.fileName.equals(rawTitle, ignoreCase = true)
                            }

                            // 3. 判断是否是切片图片素材
                            val isExplicitImage = rawPath.endsWith(".png", ignoreCase = true) ||
                                                  rawPath.endsWith(".webp", ignoreCase = true) ||
                                                  rawPath.endsWith(".jpg", ignoreCase = true) ||
                                                  rawPath.endsWith(".jpeg", ignoreCase = true)
                            val candidateAssetPath = (matchedCandidate?.payload as? MentionPayload.AssetPayload)?.filePath

                            val finalImagePath = if (isExplicitImage && rawPath.isNotBlank()) {
                                rawPath
                            } else if (!candidateAssetPath.isNullOrBlank() && (candidateAssetPath.endsWith(".png", ignoreCase = true) || candidateAssetPath.endsWith(".webp", ignoreCase = true))) {
                                candidateAssetPath
                            } else null

                            if (!finalImagePath.isNullOrBlank()) {
                                if (onImageClick != null) {
                                    onImageClick(finalImagePath)
                                } else {
                                    lightboxImageUri = finalImagePath
                                }
                                return@CommonChatMessageList
                            }

                            // 4. 判断是否是视图跳转
                            val viewId = (matchedCandidate?.payload as? MentionPayload.ViewPayload)?.viewId ?: if (tag.categoryName == "VIEW") rawPath else null
                            if (!viewId.isNullOrBlank() && onOpenViewRequested != null) {
                                onOpenViewRequested(viewId)
                                return@CommonChatMessageList
                            }

                            // 5. 普通外部规范文档查看 (如 UX_LAYOUT_SPEC.md)
                            val resolvedDocPath = (matchedCandidate?.payload as? MentionPayload.DocPayload)?.absolutePath
                                ?.ifBlank { null }
                                ?: rawPath.ifBlank { null }

                            previewDocumentData = DocumentPreviewData(
                                title = rawTitle.removePrefix("📄").removePrefix("📦").trim(),
                                filePath = resolvedDocPath,
                                content = if (resolvedDocPath == null && matchedCandidate == null) "附件信息：\n- 名称：$rawTitle\n- 类型：${tag.categoryName}\n（未能解析到本地磁盘路径）" else null
                            )
                        },
                        messageActions = messageActions,
                        modifier = Modifier.weight(1f)
                    )

                    // 3. 底部多模态输入控制栏
                    // 根据宿主剩余可视范围动态计算联想浮层最大高度：取 55% 黄金视口比例，最低 240dp，最高 640dp
                    val dynamicMentionMaxHeight = remember(studioMaxHeight) {
                        (studioMaxHeight * 0.55f).coerceIn(240.dp, 640.dp)
                    }

                    val handleFileOrImagePreview: (String) -> Unit = { uriOrPath ->
                        val ext = uriOrPath.substringAfterLast('.', "").lowercase()
                        val isImage = ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg")
                        if (isImage) {
                            if (onImageClick != null) {
                                onImageClick(uriOrPath)
                            } else {
                                lightboxImageUri = uriOrPath
                            }
                        } else {
                            val fileName = uriOrPath.replace('\\', '/').substringAfterLast('/')
                            previewDocumentData = DocumentPreviewData(
                                title = fileName.ifBlank { "文档预览" },
                                filePath = uriOrPath
                            )
                        }
                    }

                    CommonChatInputBar(
                        isGenerating = isGenerating,
                        selectedModel = selectedModel,
                        onModelSelected = onModelSelected,
                        selectedApiFlavor = selectedApiFlavor,
                        onApiFlavorSelected = onApiFlavorSelected,
                        onSendMessage = onSendMessage,
                        config = config,
                        inputTextOverride = inputTextToInject,
                        onInputTextConsumed = { inputTextToInject = null },
                        onImagePreviewRequested = handleFileOrImagePreview,
                        onDeleteMentionItem = { item ->
                            val docPayload = item.payload as? MentionPayload.DocPayload
                            if (docPayload != null && docPayload.absolutePath.isNotBlank()) {
                                onDeleteExternalDoc?.invoke(docPayload.absolutePath)
                            }
                        },
                        mentionState = mentionState,
                        mentionCandidates = mentionCandidates,
                        mentionMaxHeight = dynamicMentionMaxHeight,
                        onFilesDropped = onFilesDropped,
                        externalDroppedFiles = filesToInject,
                        onExternalDroppedFilesConsumed = { filesToInject = null },
                        activeSkills = activeSkills,
                        allSkills = allSkills,
                        enabledSkillIds = enabledSkillIds,
                        onToggleSkill = onToggleSkill,
                        skillMode = skillMode,
                        onSkillModeChanged = onSkillModeChanged,
                        leadingActions = inputLeadingActions,
                        trailingActions = inputTrailingActions
                    )
                }

                // 窄屏模式下弹出的会话覆盖层抽屉
                if (isNarrowMode && isNarrowSessionDrawerOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { isNarrowSessionDrawerOpen = false }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(min = 260.dp, max = 320.dp)
                                .clickable(enabled = false) {}
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(spacing.small),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = { isNarrowSessionDrawerOpen = false },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "关闭抽屉", modifier = Modifier.size(16.dp))
                                    }
                                }
                                CommonChatSessionSidebar(
                                    sessions = sessions,
                                    currentSessionId = currentSessionId,
                                    onSessionSelected = {
                                        onSessionSelected(it)
                                        isNarrowSessionDrawerOpen = false
                                    },
                                    onNewSession = {
                                        onNewSession()
                                        isNarrowSessionDrawerOpen = false
                                    },
                                    onDeleteSession = onDeleteSession,
                                    config = config,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 内置通用原寸沉浸式图片灯箱（全屏大图、滚轮缩放、复制与资源管理器定位）
        lightboxImageUri?.let { uri ->
            CommonImageLightbox(
                imageUri = uri,
                onDismiss = { lightboxImageUri = null }
            )
        }

        // 附件文档与详情预览弹窗
        previewDocumentData?.let { data ->
            DocumentPreviewDialog(
                title = data.title,
                filePath = data.filePath,
                content = data.content,
                onDismissRequest = { previewDocumentData = null }
            )
        }
    }
}
