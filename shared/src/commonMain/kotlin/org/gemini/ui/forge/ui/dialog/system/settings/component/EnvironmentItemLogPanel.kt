package org.gemini.ui.forge.ui.dialog.system.settings.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.app.EnvironmentItemStatus
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 针对环境依赖单项（如 Python, Rembg, ONNX Runtime 等）的安装/卸载执行日志与错误持久化留痕面板。
 * 支持在命令输出区直接识别并点击 URL 外链打开默认浏览器。
 * 遵循一文件一 Composable 铁律与统一滚动条规范。
 */
@Composable
fun EnvironmentItemLogPanel(
    item: EnvironmentItemStatus,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copyAction = rememberClipboardAction()
    val copySuccessTip = stringResource(Res.string.env_log_copied)
    val isUninstallFlow = item.isUninstallAction || item.installLogs.firstOrNull()?.startsWith("开始卸载") == true

    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
        shape = AppShapes.medium,
        color = Color(0xFF16181D),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.small)) {
            // 1. 顶部控制栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = spacing.small)
                ) {
                    when {
                        item.isInstalling -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(spacing.small))
                            Text(
                                text = stringResource(
                                    if (isUninstallFlow) Res.string.env_status_uninstalling else Res.string.env_status_installing
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        item.isSuccess == true -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(Modifier.width(spacing.small))
                            Text(
                                text = stringResource(
                                    if (isUninstallFlow) Res.string.env_status_uninstall_success else Res.string.env_status_install_success
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        item.isSuccess == false -> {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(spacing.small))
                            Text(
                                text = stringResource(
                                    if (isUninstallFlow) Res.string.env_status_uninstall_failed else Res.string.env_status_install_failed
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(Res.string.env_log_title),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 复制日志按钮
                    AppIconButton(
                        imageVector = Icons.Default.ContentCopy,
                        tooltip = stringResource(Res.string.env_log_copy),
                        tier = AppIconButtonTier.MICRO,
                        onClick = {
                            val allLogs = (item.lastError?.let { "Error: $it\n" } ?: "") + item.installLogs.joinToString("\n")
                            copyAction(allLogs, copySuccessTip)
                        }
                    )
                    Spacer(Modifier.width(spacing.extraSmall))
                    // 收起/清除日志按钮
                    AppIconButton(
                        imageVector = Icons.Default.Close,
                        tooltip = stringResource(Res.string.env_log_clear),
                        tier = AppIconButtonTier.MICRO,
                        onClick = onClearLogs
                    )
                }
            }

            // 2. 失败态错误横幅展示
            if (item.lastError != null || item.isSuccess == false) {
                Spacer(Modifier.height(spacing.extraSmall))
                Surface(
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(spacing.extraSmall))
                        Text(
                            text = item.lastError ?: stringResource(
                                if (isUninstallFlow) Res.string.env_status_uninstall_failed else Res.string.env_status_install_failed
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 3
                        )
                    }
                }
            }

            // 3. 详细命令行输出滚动区（内置 URL 外链高亮与点击打开默认浏览器支持）
            if (item.installLogs.isNotEmpty()) {
                Spacer(Modifier.height(spacing.extraSmall))
                val logScroll = rememberScrollState()
                val annotatedLogs = remember(item.installLogs) {
                    val urlRegex = Regex("""https?://[^\s)\]]+""")
                    val fullText = item.installLogs.joinToString("\n")
                    buildAnnotatedString {
                        var lastIndex = 0
                        for (match in urlRegex.findAll(fullText)) {
                            if (match.range.first > lastIndex) {
                                append(fullText.substring(lastIndex, match.range.first))
                            }
                            val url = match.value
                            pushLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = Color(0xFF8BE9FD),
                                            textDecoration = TextDecoration.Underline,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    ),
                                    linkInteractionListener = {
                                        getPlatform().openInBrowser(url)
                                    }
                                )
                            )
                            append("$url ↗")
                            pop()
                            lastIndex = match.range.last + 1
                        }
                        if (lastIndex < fullText.length) {
                            append(fullText.substring(lastIndex))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 150.dp)
                        .background(Color.Black, AppShapes.small)
                        .padding(spacing.small)
                ) {
                    Text(
                        text = annotatedLogs,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF50FA7B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = spacing.medium)
                            .verticalScroll(logScroll)
                    )

                    VerticalScrollbarAdapter(
                        scrollState = logScroll,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )

                    LaunchedEffect(item.installLogs.size) {
                        logScroll.animateScrollTo(logScroll.maxValue)
                    }
                }
            }
        }
    }
}
