package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.ui.component.tip

/**
 * 全场景通用 AI 对话工作室弹窗包装层
 *
 * 遵循 0.92f 沉浸式全屏大视口规范，内部 100% 委托 [CommonChatStudioContent]，支持全部插槽自定义。
 *
 * @param sessions 全部会话列表
 * @param currentSessionId 当前选中的会话 ID
 * @param onSessionSelected 会话选择回调
 * @param onNewSession 新建会话回调
 * @param onDeleteSession 删除会话回调
 * @param messages 当前活跃会话的消息列表
 * @param isGenerating 是否正在后台生成中
 * @param streamingText 实时流式打字文本
 * @param selectedModel 当前选中的 Gemini 模型
 * @param onModelSelected 模型选择回调
 * @param selectedApiFlavor 当前选中的通信协议
 * @param onApiFlavorSelected 协议选择回调
 * @param onSendMessage 触发发送消息回调 (text, attachments)
 * @param onDismiss 关闭弹窗回调
 * @param config 通用配置参数
 * @param onImageClick 点击图片放大回调
 * @param topBarActions 顶部自定义动作插槽（自动在最右侧追加通用关闭按钮）
 * @param messageActions 每条 AI 消息气泡底部的自定义动作插槽
 * @param inputLeadingActions 输入控制栏前置自定义动作插槽
 * @param inputTrailingActions 输入控制栏后置自定义动作插槽
 */
@Composable
fun CommonChatStudioDialog(
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
    onDismiss: () -> Unit,
    config: CommonChatStudioConfig = CommonChatStudioConfig(),
    onImageClick: ((String) -> Unit)? = null,
    onSwitchVariant: ((messageId: String, variantIndex: Int) -> Unit)? = null,
    onRegenerate: ((messageId: String) -> Unit)? = null,
    onDeleteMessage: ((messageId: String) -> Unit)? = null,
    onFallbackFlashModelRequested: ((VisualChatMessage) -> Unit)? = null,
    onSwitchProtocolRequested: ((VisualChatMessage) -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    messageActions: @Composable RowScope.(message: VisualChatMessage) -> Unit = {},
    inputLeadingActions: @Composable RowScope.() -> Unit = {},
    inputTrailingActions: @Composable RowScope.() -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            CommonChatStudioContent(
                sessions = sessions,
                currentSessionId = currentSessionId,
                onSessionSelected = onSessionSelected,
                onNewSession = onNewSession,
                onDeleteSession = onDeleteSession,
                messages = messages,
                isGenerating = isGenerating,
                streamingText = streamingText,
                selectedModel = selectedModel,
                onModelSelected = onModelSelected,
                selectedApiFlavor = selectedApiFlavor,
                onApiFlavorSelected = onApiFlavorSelected,
                onSendMessage = onSendMessage,
                config = config,
                onImageClick = onImageClick,
                onSwitchVariant = onSwitchVariant,
                onRegenerate = onRegenerate,
                onDeleteMessage = onDeleteMessage,
                onFallbackFlashModelRequested = onFallbackFlashModelRequested,
                onSwitchProtocolRequested = onSwitchProtocolRequested,
                topBarActions = {
                    topBarActions()
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).tip("关闭工作室")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                },
                messageActions = messageActions,
                inputLeadingActions = inputLeadingActions,
                inputTrailingActions = inputTrailingActions
            )
        }
    }
}
