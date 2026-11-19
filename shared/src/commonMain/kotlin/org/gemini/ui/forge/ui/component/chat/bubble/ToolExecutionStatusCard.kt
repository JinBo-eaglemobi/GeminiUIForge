package org.gemini.ui.forge.ui.component.chat.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.model.chat.ChatToolCallRecord

/**
 * 视觉资产生成类工具名称白名单。
 * 仅此类工具产出的图片，才被认定为新生成的视觉图片资产，并在卡片下方展示「存入素材库/添加到页面」操作栏。
 * 审查、观测、截图类工具（如 capture_ui_snapshot, compose_page_screenshot 等）绝不展示资产操作栏。
 */
private val ASSET_GENERATION_TOOL_NAMES: Set<String> = setOf(
    "generate_image",
    "query_image_job",
    "chat_refine_image",
    "generate_block_asset",
    "generate_all_assets"
)

/**
 * AI 工具调用执行状态与结果展示卡片 (遵循 ui-designer.md 规范)
 *
 * 采用嵌套同心圆角 (R_outer 12dp, R_inner 6dp, padding 6dp)，支持展开查看详细出入参，
 * 并支持工具产出图像（imageUri）就地内联预览。
 */
@Composable
fun ToolExecutionStatusCard(
    record: ChatToolCallRecord,
    modifier: Modifier = Modifier,
    onImageClick: ((String) -> Unit)? = null,
    imageActions: (@Composable (imageUri: String) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    val statusColor = if (record.isSuccess) {
        Color(0xFF2E7D32) // 森林绿
    } else {
        MaterialTheme.colorScheme.error
    }

    val containerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val borderStroke = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderStroke, RoundedCornerShape(12.dp)),
        color = containerBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // 顶栏：工具图标、工具名称、耗时、状态图标与折叠箭头 (通过 DisableSelection 阻断外层划选光标侵入)
            DisableSelection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Handyman,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = record.toolName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (record.durationMs > 0) {
                        Text(
                            text = "${record.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    if (record.isSuccess) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "执行成功",
                            modifier = Modifier.size(15.dp),
                            tint = statusColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "执行失败",
                            modifier = Modifier.size(15.dp),
                            tint = statusColor
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "折叠" else "展开详情",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 若工具产出了图片（如 generate_image / query_image_job），在工具卡片正下方就地呈现图片
            record.imageUri?.takeIf { it.isNotBlank() }?.let { imgUri ->
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onImageClick?.invoke(imgUri) },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imgUri,
                        contentDescription = "工具产出图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                // 仅限新生成的视觉图片资产才挂载操作栏（存入素材库/添加到页面）
                // 截图与审查类工具（如 capture_ui_snapshot）仅供视觉自审，严禁展示资产操作栏
                val isAssetGenerationTool = record.toolName in ASSET_GENERATION_TOOL_NAMES
                if (isAssetGenerationTool && imageActions != null) {
                    Spacer(Modifier.height(4.dp))
                    imageActions(imgUri)
                }
            }

            // 展开区域：入参与完整返回结果
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (record.argumentsJson.isNotBlank() && record.argumentsJson != "{}") {
                        Text(
                            text = "入参:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectionContainer {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = record.argumentsJson,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (!record.resultSummary.isNullOrBlank()) {
                        Text(
                            text = "执行结果:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectionContainer {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = record.resultSummary,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
