package org.gemini.ui.forge.ui.dialog.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.service.mcp.QuestionAnswer
import org.gemini.ui.forge.service.mcp.QuestionOption
import org.gemini.ui.forge.ui.dialog.system.component.CustomAnswerOptionCard
import org.gemini.ui.forge.ui.dialog.system.component.QuestionOptionCard
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 结构化决策问询弹窗：
 * 1. 禁用右上角关闭、取消按钮与 Esc 快捷键，强制单向明确决断；
 * 2. 宽度与高度极致逃逸：弹窗宽度 1200dp，最大高度拉满视口 94%（几乎撑满纵向屏幕）；
 * 3. 纵向自适应解耦：正文区彻底取消写死高度上限，纯 weight(1f, fill=false) 撑满屏幕剩余空间并接入 SelectionContainer 自由划词复制；
 * 4. 自定义输入原位变形：点击直接在卡片内原位变成输入框，文案转为占位提示词，零多余外挂控件；
 * 5. 常驻内置「结束任务」红线安全退出通道。
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
    var isTerminatedMode by remember { mutableStateOf(false) }

    val hasValidAnswer = isTerminatedMode || selectedIndices.isNotEmpty() || (customMode && customText.isNotBlank())

    Dialog(
        onDismissRequest = { /* 强制禁用 ESC 与点击遮罩退出，避免意外关闭导致后台协程死锁 */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 弹性计算宽屏尺寸：宽度在 1200dp 与视口 96% 之间取适宜值；最大高度开放至视口 94%
            val dialogWidth = maxWidth.coerceAtMost(1240.dp) * 0.96f
            val maxDialogHeight = maxHeight * 0.94f

            Surface(
                modifier = Modifier
                    .width(dialogWidth.coerceIn(720.dp, 1200.dp))
                    .heightIn(max = maxDialogHeight),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.large)
                ) {
                    // Header 标题栏
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
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    Spacer(Modifier.height(spacing.medium))

                    // 【区域 A：正文描述与技术方案大纲区】
                    // 1. 外层挂载 SelectionContainer 支持光标自由划词与复制；
                    // 2. 彻底取消写死高度上限！采用纯 weight(1f, fill = false)：短文案紧凑贴合，长方案大纲自动自然拉伸撑满屏幕剩余的全部纵向空间。
                    if (question.isNotBlank()) {
                        val descScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(descScrollState)
                                        .padding(end = 14.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = question,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(spacing.large)
                                        )
                                    }
                                }
                            }

                            VerticalScrollbarAdapter(
                                scrollState = descScrollState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                            )
                        }

                        Spacer(Modifier.height(spacing.medium))
                    }

                    // 【区域 B：固定常驻选项区】
                    // 完全脱离滚动容器，永远完整平铺可见，绝不滚出视口。
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        sortedOptions.forEach { option ->
                            val originalIndex = options.indexOf(option)
                            val isSelected = !customMode && !isTerminatedMode && originalIndex in selectedIndices
                            QuestionOptionCard(
                                label = option.label,
                                description = option.description,
                                selected = isSelected,
                                multiple = multiple,
                                recommended = option.recommended,
                                onClick = {
                                    isTerminatedMode = false
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
                            CustomAnswerOptionCard(
                                value = customText,
                                onValueChange = { customText = it },
                                active = customMode,
                                onActivate = {
                                    isTerminatedMode = false
                                    selectedIndices = emptySet()
                                    customMode = true
                                },
                                placeholder = stringResource(Res.string.question_custom_answer_placeholder),
                                title = stringResource(Res.string.question_custom_answer_label),
                                description = stringResource(Res.string.question_custom_answer_hint)
                            )
                        }

                        // 常驻内置安全退出通道：结束任务
                        QuestionOptionCard(
                            label = "结束任务（放弃修改并终止对话）",
                            description = "明确拒绝当前方案并指示 AI 立即中止后续所有活动，不执行任何修改",
                            selected = isTerminatedMode,
                            multiple = false,
                            recommended = false,
                            onClick = {
                                isTerminatedMode = true
                                customMode = false
                                selectedIndices = emptySet()
                            }
                        )
                    }

                    Spacer(Modifier.height(spacing.large))

                    // Footer 底部确认操作按钮（靠右对齐）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            enabled = hasValidAnswer,
                            colors = if (isTerminatedMode) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            onClick = {
                                if (isTerminatedMode) {
                                    onAnswer(
                                        QuestionAnswer(
                                            isCustom = false,
                                            selectedIndices = emptyList(),
                                            selectedLabels = listOf("结束任务（放弃修改并终止对话）"),
                                            customText = "用户已明确拒绝当前方案并要求结束任务，请立即中止后续所有活动，不要继续执行任何修改或排版。",
                                            isTerminated = true
                                        )
                                    )
                                } else if (customMode) {
                                    onAnswer(
                                        QuestionAnswer(
                                            isCustom = true,
                                            customText = customText.trim()
                                        )
                                    )
                                } else {
                                    val sortedList = selectedIndices.sorted()
                                    val labels = sortedList.mapNotNull { idx -> options.getOrNull(idx)?.label }
                                    onAnswer(
                                        QuestionAnswer(
                                            isCustom = false,
                                            selectedIndices = sortedList,
                                            selectedLabels = labels
                                        )
                                    )
                                }
                            }
                        ) {
                            Text(
                                if (isTerminatedMode) "确认终止任务"
                                else stringResource(Res.string.question_submit_single)
                            )
                        }
                    }
                }
            }
        }
    }
}
