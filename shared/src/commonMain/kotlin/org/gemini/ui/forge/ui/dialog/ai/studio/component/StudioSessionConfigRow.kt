package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 视觉工作室会话级模型与并发生成数量配置行（宽屏呼吸感优化版）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioSessionConfigRow(
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    generationCount: Int,
    onCountSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    var isModelMenuExpanded by remember { mutableStateOf(false) }

    // 可选生图/视觉大模型列表（基于当前最新官方可用模型）
    val availableModels = remember {
        listOf(
            GeminiModel.GEMINI_2_5_FLASH_IMAGE,
            GeminiModel.GEMINI_3_PRO_IMAGE,
            GeminiModel.GEMINI_3_1_FLASH_IMAGE,
            GeminiModel.GEMINI_3_1_FLASH_LITE_IMAGE,
            GeminiModel.GEMINI_3_7_FLASH,
            GeminiModel.GEMINI_2_5_PRO,
            GeminiModel.GEMINI_2_5_FLASH
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium) // 拉开 16dp 间距
    ) {
        // 1. 模型切换下拉选择框
        Box {
            OutlinedButton(
                onClick = { isModelMenuExpanded = true },
                shape = AppShapes.small,
                modifier = Modifier
                    .height(32.dp)
                    .tip("切换当前会话专属的生图大模型（独立生效）"),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(spacing.extraSmall))
                Text(
                    text = selectedModel.displayName.replace("Preview", "").trim(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = isModelMenuExpanded,
                onDismissRequest = { isModelMenuExpanded = false },
                modifier = Modifier.width(280.dp)
            ) {
                Text(
                    text = "🤖 选择生图/多模态模型",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (model == selectedModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = getModelFeatureSummary(model),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onModelSelected(model)
                            isModelMenuExpanded = false
                        }
                    )
                }
            }
        }

        // 2. 单次生成数量分段单选胶囊 (1 / 2 / 4 张)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.tip("单次并发生成的图片数量（默认 1 张以节省资源）")
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.height(30.dp)
            ) {
                val counts = listOf(1, 2, 4)
                counts.forEachIndexed { index, count ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = counts.size),
                        onClick = { onCountSelected(count) },
                        selected = generationCount == count,
                        icon = {},
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text(
                            text = "${count}张",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (generationCount == count) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/** 获取模型特性一句话摘要 */
private fun getModelFeatureSummary(model: GeminiModel): String {
    return when (model) {
        GeminiModel.GEMINI_2_5_FLASH_IMAGE -> "Nano Banana 快速视觉图像生成"
        GeminiModel.GEMINI_3_PRO_IMAGE -> "Nano Banana Pro 高质感商业级生图"
        GeminiModel.GEMINI_3_1_FLASH_IMAGE -> "Nano Banana 2 最新一代智能生图"
        GeminiModel.GEMINI_3_1_FLASH_LITE_IMAGE -> "Nano Banana 2 Lite 超低延迟生图"
        GeminiModel.GEMINI_3_7_FLASH -> "Gemini 3.7 混合推理多模态"
        GeminiModel.GEMINI_2_5_PRO -> "复杂语义与高难度多轮构图推理"
        GeminiModel.GEMINI_2_5_FLASH -> "极速响应与低延迟视觉多模态"
        else -> "通用图像生成模型"
    }
}
