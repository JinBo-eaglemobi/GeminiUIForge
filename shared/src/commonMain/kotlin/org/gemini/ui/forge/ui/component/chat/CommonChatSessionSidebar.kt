package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast

/**
 * 全场景通用多会话历史侧栏组件
 *
 * @param sessions 全部会话列表
 * @param currentSessionId 当前选中的会话 ID
 * @param onSessionSelected 会话选择回调
 * @param onNewSession 新建会话回调
 * @param onDeleteSession 删除会话回调
 * @param config 通用配置参数
 * @param headerActions 侧栏顶部标题栏自定义动作插槽（如收起侧栏等）
 * @param modifier 修饰符
 */
@Composable
fun CommonChatSessionSidebar(
    sessions: List<VisualChatSession>,
    currentSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    config: CommonChatStudioConfig = CommonChatStudioConfig(),
    headerActions: @Composable RowScope.() -> Unit = {}
) {
    val spacing = LocalAppSpacing.current
    var searchQuery by remember { mutableStateOf("") }
    var sessionToDelete by remember { mutableStateOf<VisualChatSession?>(null) }

    val filteredSessions = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) sessions
        else sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(spacing.small)) {
            // 1. 顶部标题栏 + 新建会话按钮 + 自定义动作插槽
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "会话历史",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${sessions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    headerActions()

                    IconButton(
                        onClick = onNewSession,
                        modifier = Modifier.size(28.dp).tip("新建对话")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "新建对话",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. 搜索过滤输入框（若配置开启）
            if (config.showSessionSearch && sessions.size > 3) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "搜索历史会话...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清空搜索",
                                modifier = Modifier.size(16.dp).clickable { searchQuery = "" }
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Spacer(Modifier.height(spacing.small))
            }

            // 3. 会话卡片列表
            if (filteredSessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) "暂无会话" else "未搜到匹配会话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredSessions.forEach { session ->
                        val isSelected = session.id == currentSessionId
                        val interactionSource = remember { MutableInteractionSource() }
                        val isHovered by interactionSource.collectIsHoveredAsState()

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isSelected -> Color(0xFF282046)
                                isHovered -> Color.White.copy(alpha = 0.04f)
                                else -> Color.Transparent
                            },
                            border = when {
                                isSelected -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f))
                                isHovered -> androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                else -> null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onSessionSelected(session.id) }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 选中状态指示器小胶囊
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0xFFA855F7))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${session.messages.size} 条",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = if (isSelected) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                        )

                                        // 显式呈现当前会话的通信协议/模式徽章（精细化防变形胶囊）
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.25f)
                                            else Color.White.copy(alpha = 0.06f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                0.5.dp,
                                                if (isSelected) Color(0xFF818CF8).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
                                            )
                                        ) {
                                            Text(
                                                text = when (config.defaultApiFlavor) {
                                                    org.gemini.ui.forge.model.app.ApiFlavor.INTERACTIONS -> "⚡ Interactions"
                                                    org.gemini.ui.forge.model.app.ApiFlavor.GENERATE_CONTENT -> "⚡ generateContent"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    lineHeight = 12.sp
                                                ),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSelected) Color(0xFFC7D2FE) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // 单项删除按钮（二次确认保护，悬停或选中时更清晰）
                                IconButton(
                                    onClick = { sessionToDelete = session },
                                    modifier = Modifier.size(24.dp).tip("删除会话")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isHovered || isSelected) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除会话二次确认弹窗
    sessionToDelete?.let { session ->
        AppConfirmDialog(
            title = "删除对话确认",
            message = "确定要删除对话 \"${session.title}\" 吗？此操作无法撤销。",
            confirmText = "确认删除",
            isDestructive = true,
            onConfirm = {
                onDeleteSession(session.id)
                sessionToDelete = null
                Toast.show("会话已安全删除", ToastType.INFO)
            },
            onDismiss = {
                sessionToDelete = null
            }
        )
    }
}
