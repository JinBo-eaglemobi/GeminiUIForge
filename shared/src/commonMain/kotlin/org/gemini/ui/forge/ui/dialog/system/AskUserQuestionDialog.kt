package org.gemini.ui.forge.ui.dialog.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.service.mcp.QuestionAnswer
import org.gemini.ui.forge.service.mcp.QuestionOption
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.system.component.QuestionOptionCard
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * OpenCode 风格结构化问询弹窗：header + question + 选项卡片列表 + 自定义答案 + 提交/取消
 * 单题渲染；多问题轮播由 McpInteractionBridge 逐题挂起实现
 */
@Composable
fun AskUserQuestionDialog(
    header: String,
    question: String,
    options: List<QuestionOption>,
    multiple: Boolean,
    allowCustomAnswer: Boolean,
    onAnswer: (QuestionAnswer) -> Unit,
    onCancel: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    // recommended 稳定置顶（原地稳定排序）
    val sortedOptions = remember(options) {
        options.sortedByDescending { it.recommended }
    }
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    var customMode by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    val hasValidAnswer = selectedIndices.isNotEmpty() || (customMode && customText.isNotBlank())

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(spacing.dialogConfigWidth),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.mcp_interaction_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(spacing.extraSmall))
                    Text(
                        text = header.ifBlank { stringResource(Res.string.question_title_default) },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.tip(stringResource(Res.string.question_cancel))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.question_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (question.isNotBlank()) {
                    Text(
                        text = question,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = spacing.small)
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    items(sortedOptions) { option ->
                        val originalIndex = options.indexOf(option)
                        val isSelected = !customMode && originalIndex in selectedIndices
                        QuestionOptionCard(
                            label = option.label,
                            description = option.description,
                            selected = isSelected,
                            multiple = multiple,
                            recommended = option.recommended,
                            onClick = {
                                if (customMode) {
                                    customMode = false
                                }
                                selectedIndices = if (multiple) {
                                    if (isSelected) selectedIndices - originalIndex
                                    else selectedIndices + originalIndex
                                } else {
                                    setOf(originalIndex)
                                }
                            }
                        )
                    }

                    if (allowCustomAnswer) {
                        item {
                            QuestionOptionCard(
                                label = stringResource(Res.string.question_custom_answer_label),
                                description = stringResource(Res.string.question_custom_answer_hint),
                                selected = customMode,
                                multiple = false,
                                recommended = false,
                                onClick = {
                                    selectedIndices = emptySet()
                                    customMode = true
                                }
                            )
                            if (customMode) {
                                Spacer(Modifier.height(spacing.small))
                                OutlinedTextField(
                                    value = customText,
                                    onValueChange = { customText = it },
                                    placeholder = {
                                        Text(stringResource(Res.string.question_custom_answer_placeholder))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 4
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = hasValidAnswer,
                onClick = {
                    if (customMode) {
                        onAnswer(
                            QuestionAnswer(
                                isCustom = true,
                                customText = customText.trim()
                            )
                        )
                    } else {
                        val sortedSelected = selectedIndices.sorted()
                        onAnswer(
                            QuestionAnswer(
                                isCustom = false,
                                selectedIndices = sortedSelected,
                                selectedLabels = sortedSelected.mapNotNull { options.getOrNull(it)?.label }
                            )
                        )
                    }
                }
            ) {
                Text(
                    if (multiple && selectedIndices.size > 1) {
                        stringResource(Res.string.question_submit_multi, selectedIndices.size)
                    } else {
                        stringResource(Res.string.question_submit_single)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.question_cancel))
            }
        }
    )
}
