package org.gemini.ui.forge.ui.dialog.mcp.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.gemini.ui.forge.extend.copyOnClick

import org.gemini.ui.forge.service.mcp.McpCommandDictionary
import org.gemini.ui.forge.service.mcp.McpTrafficDirection
import org.gemini.ui.forge.service.mcp.McpTrafficRecord
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.looseJson
import org.gemini.ui.forge.utils.unwrapJsonStringsForDisplay
import kotlin.time.Instant

/**
 * 单条通信报文项组件 (含【指令用途释义】与【Pretty Print 格式化美化 JSON】)
 * 遵循一文件一 Composable 规范。
 */
@OptIn(FormatStringsInDatetimeFormats::class)
@Composable
fun TrafficRecordItem(
    record: McpTrafficRecord,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInbound = record.direction == McpTrafficDirection.INBOUND
    val dirColor = if (isInbound) Color(0xFF00ACC1) else Color(0xFF8E24AA)

    val timeStr = remember(record.timestamp) {
        Instant.fromEpochMilliseconds(record.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .format(LocalDateTime.Format {
                byUnicodePattern("HH:mm:ss.SSS")
            })
    }

    val commandDesc = remember(record.methodOrTool) {
        McpCommandDictionary.getCommandDescription(record.methodOrTool)
    }

    // 格式化 Pretty Print JSON (2 空格缩进，递归展开嵌套的 JSON 字符串供 UI 呈现)
    val prettyJson = remember(record.payloadJson) {
        try {
            if (record.payloadJson.isBlank() || record.payloadJson == "{}") {
                "{}"
            } else {
                val parsed = looseJson.parseToJsonElement(record.payloadJson)
                val displayElement = unwrapJsonStringsForDisplay(parsed)
                val prettyPrinter = Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                }
                prettyPrinter.encodeToString(JsonElement.serializer(), displayElement)
            }
        } catch (_: Exception) {
            record.payloadJson
        }
    }

    val lines = remember(prettyJson) {
        prettyJson.lines()
    }

    val lineCount = lines.size
    val lineNumWidth = remember(lineCount) {
        val digits = lineCount.toString().length
        // 至少 2 位字符宽度，每增加一位多 7.dp
        maxOf(24.dp, (digits * 7 + 10).dp)
    }

    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    Card(
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (record.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = 0.4f
                ),
                shape = AppShapes.medium
            )
            .clip(AppShapes.medium)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶栏 Header 区域：仅此区域承担折叠与展开点击手势，与下方 JSON 区域彻底解耦
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.medium)
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        // 方向徽章
                        Surface(
                            shape = AppShapes.small,
                            color = dirColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isInbound) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = dirColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isInbound) "INBOUND" else "OUTBOUND",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = dirColor,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        Text(
                            text = record.methodOrTool,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 点击即复制指令名（复用项目剪贴板规范扩展；内层 clickable 优先消费点击，不冒泡触发 Header 折叠）
                            modifier = Modifier
                                .copyOnClick(record.methodOrTool, "已复制指令名: ${record.methodOrTool}")
                                .tip("点击复制指令名")
                        )

                        if (record.clientName != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = AppShapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = record.clientName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (record.durationMs != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = AppShapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "${record.durationMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = record.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (record.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) 10 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))

                    // 【指令用途解释卡片】
                    Surface(
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "【指令用途】: $commandDesc",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // JSON 代码块标题行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Payload (格式化 JSON · ${lineCount} 行)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedButton(
                            onClick = onCopy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp).tip("复制格式化后的完整 JSON 报文")
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制 JSON", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // 格式化后的代码块内容 (左侧行号条 + 右侧自由划词选择代码区，零手势冒泡)
                    Surface(
                        shape = AppShapes.small,
                        color = Color(0xFF1E1E1E), // 专用高对比深色代码底板
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            // 1. 左侧独立行号列 (Gutter)
                            Text(
                                text = lineNumbersText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFF6E7681), // 优雅暗灰行号
                                modifier = Modifier
                                    .width(lineNumWidth)
                                    .padding(end = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )

                            // 2. 行号与代码垂直分隔线
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF333333))
                            )

                            // 3. 右侧正文代码区 (纯净 SelectionContainer，单机/双击/拖拽绝不冒泡折叠)
                            SelectionContainer(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp, end = 12.dp)
                            ) {
                                Text(
                                    text = prettyJson,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFFD4D4D4)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
