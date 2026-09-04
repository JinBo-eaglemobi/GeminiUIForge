package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.looseJson
import org.jetbrains.compose.resources.stringResource

/**
 * 视觉工作室会话完整 JSON 交互日志查看器（支持 Base64 智能折叠与专属预览）
 */
@Composable
fun StudioSessionLogDialog(
    session: VisualChatSession?,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val clipboardManager = LocalClipboardManager.current

    var selectedBase64Payload by remember { mutableStateOf<String?>(null) }

    // 格式化完整 JSON
    val rawJsonString = remember(session) {
        if (session != null) {
            try {
                looseJson.encodeToString(VisualChatSession.serializer(), session)
            } catch (e: Exception) {
                "JSON 序列化失败: ${e.message}"
            }
        } else {
            "当前无活动会话"
        }
    }

    // 智能折叠超长 Base64 字符串
    val displayJsonString = remember(rawJsonString) {
        val b64Regex = Regex(""""data:image/[^;]+;base64,([A-Za-z0-9+/=]{100,})"""")
        b64Regex.replace(rawJsonString) { matchResult ->
            val totalLen = matchResult.value.length
            val sizeKb = (totalLen * 3 / 4) / 1024
            """"data:image/...;base64,[Base64 图像数据已自动折叠 (~${sizeKb}KB) - 可在会话气泡或下方点击预览]""""
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.88f),
            shape = AppShapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
                // 1. 顶栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DataObject,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(spacing.small))
                        Column {
                            Text(
                                text = "会话完整 JSON 交互日志",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "超长 Base64 数据已在视图中自动折叠，点击右上角复制获取全量数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(rawJsonString))
                                Toast.show("已成功复制完整全量会话 JSON 到剪贴板", ToastType.SUCCESS)
                            },
                            shape = AppShapes.small,
                            modifier = Modifier.height(32.dp).tip("复制全量未经折叠的完整 JSON 到系统剪贴板"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制全量 JSON", style = MaterialTheme.typography.labelSmall)
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp).tip(stringResource(Res.string.btn_close_dialog))
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(spacing.small))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(spacing.small))

                // 2. JSON 展示容器
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = AppShapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(spacing.medium).verticalScroll(rememberScrollState())) {
                        Text(
                            text = displayJsonString,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
