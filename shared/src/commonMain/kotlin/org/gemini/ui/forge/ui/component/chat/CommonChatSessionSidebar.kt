package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
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
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "${sessions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    headerActions()

                    FilledTonalButton(
                        onClick = onNewSession,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp).tip("新建对话")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "新建对话",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("新建", style = MaterialTheme.typography.labelSmall)
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

            // 3. 会话卡片列表（调用独立的 CommonChatSessionItem 保持一文件一 Composable 铁律）
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
                        CommonChatSessionItem(
                            session = session,
                            isSelected = session.id == currentSessionId,
                            onSelect = { onSessionSelected(session.id) },
                            onDeleteRequest = { sessionToDelete = session }
                        )
                    }
                }
            }
        }
    }

    // 4. 删除会话二次确认弹窗
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
