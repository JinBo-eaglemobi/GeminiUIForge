package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.traffic_show_all_lines
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.formatTimestamp
import org.gemini.ui.forge.model.chat.TrafficDirection
import org.gemini.ui.forge.model.chat.TrafficRecord
import org.gemini.ui.forge.utils.TrafficSummaryExtractor
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

private val prettyJsonParser = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
    isLenient = true
}

private fun formatJsonPretty(raw: String): String {
    val trimmed = raw.trim()
    if (!((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]")))) {
        return raw
    }
    return try {
        val element = prettyJsonParser.parseToJsonElement(trimmed)
        prettyJsonParser.encodeToString(JsonElement.serializer(), element)
    } catch (_: Throwable) {
        raw
    }
}

/**
 * 单遍正则扫描提取 Base64 并替换生成预览折叠文本
 */
private fun buildBase64FoldedText(prettyBody: String): Pair<Map<String, String>, String> {
    val map = mutableMapOf<String, String>()
    val regex = Regex(""""(data|bytesBase64Encoded)"\s*:\s*"([A-Za-z0-9+/=]{128,})"""")
    val text = regex.replace(prettyBody) { match ->
        val field = match.groupValues[1]
        val b64 = match.groupValues[2]
        val token = "__BASE64_TOKEN_${map.size}__"
        map[token] = b64
        val sizeKb = (b64.length * 3 / 4) / 1024
        """"$field": "<Base64 数据已折叠 (~${sizeKb}KB) —— 点击下方按钮查看/预览图片>""""
    }
    return map to text
}

/**
 * 单条原始通信档案卡片组件
 *
 * 核心性能保障：折叠状态下零 JSON 解析与零正则扫描，展开后按需渐进渲染。
 */
@Composable
fun TrafficRecordCard(
    record: TrafficRecord,
    defaultExpanded: Boolean = false,
    expandToggleVersion: Int = 0,
    onCopyRaw: () -> Unit,
    onInspectPayload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()
    var isExpanded by remember { mutableStateOf(defaultExpanded) }

    LaunchedEffect(expandToggleVersion) {
        if (expandToggleVersion > 0) {
            isExpanded = defaultExpanded
        }
    }

    val isReq = record.direction == TrafficDirection.REQ
    val dirColor = if (isReq) Color(0xFF2196F3) else Color(0xFF4CAF50)
    val dirText = if (isReq) "请求" else "响应"

    // 报文体近似大小徽章（基于字符长度，零额外解析成本）
    val sizeLabel = remember(record.body) {
        val bytes = record.body.length
        if (bytes >= 1024 * 1024) {
            "~${bytes / 1024 / 1024}MB"
        } else {
            "~${(bytes / 1024).coerceAtLeast(1)}KB"
        }
    }

    Card(
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), AppShapes.medium)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.medium)) {
            // 卡片头部元数据行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.small)
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // 通信时间戳 (精确到毫秒)
                    val timeStr = remember(record.timestamp) {
                        formatTimestamp(record.timestamp, "HH:mm:ss.SSS")
                    }
                    Surface(
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = spacing.small)
                    ) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // 轮次胶囊（若 roundIndex > 0）
                    if (record.roundIndex > 0) {
                        Surface(
                            shape = AppShapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "第 ${record.roundIndex} 轮",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(spacing.extraSmall))
                    }

                    // 方向标识胶囊
                    Surface(
                        shape = AppShapes.small,
                        color = dirColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${record.seq.toString().padStart(4, '0')} $dirText",
                            style = MaterialTheme.typography.labelSmall,
                            color = dirColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // 大小徽章
                    Spacer(Modifier.width(spacing.extraSmall))
                    Surface(
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = sizeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    if (record.model.isNotBlank()) {
                        Spacer(Modifier.width(spacing.small))
                        Text(
                            text = record.model,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 复制单条报文完整原文按钮
                    IconButton(
                        onClick = onCopyRaw,
                        modifier = Modifier.size(28.dp).tip("复制本条报文完整原文（含未截断 Base64）")
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp).tip(if (isExpanded) "收起报文" else "展开报文")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 展开状态下第二排显示完整的请求地址行 (支持划选且不截断)
            if (isExpanded && record.url.isNotBlank()) {
                Spacer(Modifier.height(spacing.extraSmall))
                Surface(
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = "请求地址",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.url,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { copy(record.url, "已复制请求地址到剪贴板") },
                            modifier = Modifier.size(24.dp).tip("复制完整请求地址")
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 阶段业务标识胶囊（仅展示纯净胶囊，彻底去除杂乱的截断正文摘要）
            if (record.stageTag.isNotBlank()) {
                Spacer(Modifier.height(spacing.extraSmall))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                ) {
                    Surface(
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = record.stageTag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // 报文展开内容（仅在 isExpanded == true 时执行重计算与渲染）
            if (isExpanded) {
                Spacer(Modifier.height(spacing.small))

                // 1. JSON 格式化与 Base64 折叠处理（惰性执行）
                val (base64PayloadMap, displayBodyText) = remember(record.body) {
                    val pretty = formatJsonPretty(record.body)
                    buildBase64FoldedText(pretty)
                }

                // 2. 若包含折叠的 Base64 图像，提供快捷点击按钮
                if (base64PayloadMap.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        base64PayloadMap.values.forEachIndexed { idx, payload ->
                            val sizeKb = (payload.length * 3 / 4) / 1024
                            ElevatedButton(
                                onClick = { onInspectPayload(payload) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).tip("点击弹出解码大图预览与完整原文复制"),
                                shape = AppShapes.small
                            ) {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("预览 Base64 图片 #${idx + 1} (~${sizeKb}KB)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // 3. 渐进式超长正文渲染控制（首屏 400 行）
                var isShowAllLines by remember { mutableStateOf(false) }
                val allLines = remember(displayBodyText) { displayBodyText.lines() }
                val isOversized = allLines.size > 400
                val renderedText = remember(displayBodyText, isShowAllLines) {
                    if (isOversized && !isShowAllLines) {
                        allLines.take(400).joinToString("\n")
                    } else {
                        displayBodyText
                    }
                }

                // 报文等宽文本区
                Surface(
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(spacing.medium)) {
                        SelectionContainer(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        ) {
                            Text(
                                text = renderedText,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = true
                            )
                        }

                        if (isOversized && !isShowAllLines) {
                            Spacer(Modifier.height(spacing.small))
                            val remainingLines = allLines.size - 400
                            TextButton(
                                onClick = { isShowAllLines = true },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Icon(Icons.Default.UnfoldMore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text(
                                    text = stringResource(Res.string.traffic_show_all_lines, remainingLines),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
