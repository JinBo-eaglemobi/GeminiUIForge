package org.gemini.ui.forge.ui.dialog.ai.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 全局统一的标准双语 AI 提示词编辑器组件 (BilingualPromptEditor)
 *
 * 强制统一规范：全项目凡涉及生图提示词/提交文案的编辑，统一复用本组件，严禁手搓重复 UI。
 *
 * 核心规范落地：
 * 1. 中英文双语切换：SingleChoiceSegmentedButtonRow 单行不折行切换查看/编辑 userPromptZh 与 userPromptEn；
 * 2. 跨语言参考提示：当前语言为空时，自动提取另一语言前 40 字符作为占位参考；
 * 3. AI 提示词优化入口：配备 34dp FilledTonalButton 胶囊优化按钮，带加载指示与防重点击；
 * 4. 变动感知与显式确认同步：文案发生改动或经 AI 优化后，显式高亮"确认修改并同步"，点击后回调同步并持久化；
 * 5. 全量交互按钮 100% 挂载 Modifier.tip 悬浮提示，全案 Design Tokens 间距标准。
 *
 * @param promptZh 中文提示词文案
 * @param promptEn 英文提示词文案
 * @param onPromptConfirmed 用户确认修改时的回调（回传最新 zh 与 en）
 * @param onOptimizeRequested 请求 AI 优化当前提示词的回调（回传当前待优化文案与是否为中文）
 * @param isOptimizing 是否处于 AI 优化生成中
 * @param modifier 外部布局修饰符
 * @param title 标题文案
 * @param showExplicitConfirmButton 是否展示变动感知显式确认按钮
 * @param showChatContextOption 是否展示"携带历史上下文 (会话模式)"勾选项
 * @param useChatContext 当前是否勾选了会话上下文
 * @param onChatContextChanged 会话上下文勾选状态变更回调
 * @param minFieldHeight 输入框最小高度
 * @param maxLines 输入框最大行数
 * @param enabled 是否允许交互编辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilingualPromptEditor(
    promptZh: String,
    promptEn: String,
    initialLanguage: PromptLanguage = PromptLanguage.ZH,
    onLanguageChanged: (PromptLanguage) -> Unit = {},
    onPromptConfirmed: (zh: String, en: String) -> Unit,
    onOptimizeRequested: (currentPrompt: String, isZh: Boolean) -> Unit,
    isOptimizing: Boolean,
    modifier: Modifier = Modifier,
    title: String = "生图提示词 / 提交文案 (Prompt)",
    showExplicitConfirmButton: Boolean = true,
    showChatContextOption: Boolean = false,
    useChatContext: Boolean = false,
    onChatContextChanged: (Boolean) -> Unit = {},
    minFieldHeight: Dp = 100.dp,
    maxLines: Int = 5,
    enabled: Boolean = true
) {
    val spacing = LocalAppSpacing.current
    val resolvedInitialLang = remember(initialLanguage, promptZh, promptEn) {
        when {
            initialLanguage == PromptLanguage.ZH -> PromptLanguage.ZH
            initialLanguage == PromptLanguage.EN -> PromptLanguage.EN
            promptEn.isNotBlank() && promptZh.isBlank() -> PromptLanguage.EN
            else -> PromptLanguage.ZH // AUTO 默认优先中文
        }
    }
    var currentPromptLang by remember(resolvedInitialLang) { mutableStateOf(resolvedInitialLang) }

    LaunchedEffect(resolvedInitialLang) {
        onLanguageChanged(resolvedInitialLang)
    }

    var localPromptZh by remember(promptZh) { mutableStateOf(promptZh) }
    var localPromptEn by remember(promptEn) { mutableStateOf(promptEn) }

    val isModified = localPromptZh != promptZh || localPromptEn != promptEn
    val activePrompt = if (currentPromptLang == PromptLanguage.ZH) localPromptZh else localPromptEn
    val alternatePrompt = if (currentPromptLang == PromptLanguage.ZH) localPromptEn else localPromptZh

    Column(modifier = modifier) {
        // 1. 顶部控制行：标题 + 双语切换 + AI 优化胶囊按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(spacing.small))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.weight(1f))

            // 中英文双语切换 SegmentedButton（强制单行展开不折行，移除挤占空间的图标）
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = currentPromptLang == PromptLanguage.ZH,
                    onClick = {
                        currentPromptLang = PromptLanguage.ZH
                        onLanguageChanged(PromptLanguage.ZH)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    icon = {},
                    enabled = enabled,
                    label = {
                        Text(
                            text = stringResource(Res.string.prompt_lang_zh),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (currentPromptLang == PromptLanguage.ZH) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    modifier = Modifier.widthIn(min = 96.dp).tip(stringResource(Res.string.prompt_lang_zh))
                )
                SegmentedButton(
                    selected = currentPromptLang == PromptLanguage.EN,
                    onClick = {
                        currentPromptLang = PromptLanguage.EN
                        onLanguageChanged(PromptLanguage.EN)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    icon = {},
                    enabled = enabled,
                    label = {
                        Text(
                            text = stringResource(Res.string.prompt_lang_en),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (currentPromptLang == PromptLanguage.EN) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    modifier = Modifier.widthIn(min = 96.dp).tip(stringResource(Res.string.prompt_lang_en))
                )
            }

            Spacer(Modifier.width(spacing.medium))

            // AI 提示词优化辅助入口按钮
            FilledTonalButton(
                onClick = {
                    val source = activePrompt.ifBlank { alternatePrompt }
                    onOptimizeRequested(source, currentPromptLang == PromptLanguage.ZH)
                },
                enabled = enabled && !isOptimizing && (activePrompt.isNotBlank() || alternatePrompt.isNotBlank()),
                shape = AppShapes.small,
                contentPadding = PaddingValues(horizontal = spacing.small, vertical = 0.dp),
                modifier = Modifier.height(34.dp).tip(stringResource(Res.string.ai_optimize_prompt))
            ) {
                if (isOptimizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(spacing.extraSmall))
                    Text(
                        text = stringResource(Res.string.ai_optimize_prompt),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(Modifier.height(spacing.small))

        // 2. 提示词多行编辑框（双语联动与跨语言参考提示）
        SelectAllOutlinedTextField(
            value = activePrompt,
            onValueChange = { newText ->
                if (currentPromptLang == PromptLanguage.ZH) {
                    localPromptZh = newText
                } else {
                    localPromptEn = newText
                }
                if (!showExplicitConfirmButton) {
                    onPromptConfirmed(localPromptZh, localPromptEn)
                }
            },
            label = {
                Text(
                    if (currentPromptLang == PromptLanguage.ZH) "中文描述（影响生图内容）"
                    else "English Prompt (Detailed Material, Lighting & Texture)"
                )
            },
            placeholder = {
                if (alternatePrompt.isNotBlank()) {
                    Text(
                        text = "另一语言参考: ${alternatePrompt.take(40)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = minFieldHeight),
            shape = AppShapes.small,
            maxLines = maxLines,
            enabled = enabled
        )

        // 3. 可选：会话上下文选项与变动确认按钮
        if (showChatContextOption || (showExplicitConfirmButton && isModified)) {
            Spacer(Modifier.height(spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showChatContextOption) {
                    Checkbox(
                        checked = useChatContext,
                        onCheckedChange = onChatContextChanged,
                        enabled = enabled
                    )
                    Spacer(Modifier.width(spacing.extraSmall))
                    Text("携带历史上下文 (会话模式)", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.weight(1f))

                if (showExplicitConfirmButton && isModified) {
                    Button(
                        onClick = { onPromptConfirmed(localPromptZh, localPromptEn) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = AppShapes.small,
                        contentPadding = PaddingValues(horizontal = spacing.medium, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).tip(stringResource(Res.string.btn_confirm_sync))
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(spacing.extraSmall))
                        Text(stringResource(Res.string.btn_confirm_sync), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
