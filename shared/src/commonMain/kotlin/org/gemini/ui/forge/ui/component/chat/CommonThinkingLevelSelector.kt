package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.chat_thinking_level_menu_title
import geminiuiforge.composeapp.generated.resources.chat_thinking_level_tip
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.chat.ThinkingLevel
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 通用思考等级选择器组件（底栏药丸 + 卡片化下拉菜单）
 *
 * 遵循一文件一 Composable 铁律，供 UI Studio 对话输入栏与 Slots 工作台配置行统一复用。
 * 严格遵循【响应式动态宽度计算规范】：
 * - 动态测量触发按钮宽度并实现最小宽度支撑锚定（effectiveMinWidth = maxOf(320.dp, minMenuWidth, anchorWidthDp)）；
 * - 默认向上偏移安全展开（offset = DpOffset(0.dp, (-8).dp)），杜绝冲出视口截断；
 * - 内容区支持垂直平滑滚动，并支持外部注入自适应响应式宽高夹具；
 * - 仅当 [selectedModel].supportsThinking 为 true 时渲染，专属生图模型自动隐藏。
 */
@Composable
fun CommonThinkingLevelSelector(
    selectedModel: GeminiModel,
    currentLevel: ThinkingLevel,
    onLevelSelected: (ThinkingLevel) -> Unit,
    modifier: Modifier = Modifier,
    useShortLabel: Boolean = true,
    minMenuWidth: Dp = 0.dp,
    maxMenuWidth: Dp = 440.dp,
    maxMenuHeight: Dp = 520.dp,
    offset: DpOffset = DpOffset(0.dp, (-8).dp)
) {
    if (!selectedModel.supportsThinking) return

    val spacing = LocalAppSpacing.current
    var isExpanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val anchorWidthDp = with(density) { anchorWidthPx.toDp() }
    val effectiveMinWidth = maxOf(320.dp, minMenuWidth, anchorWidthDp)

    Box(modifier = modifier) {
        val isNonDefault = currentLevel != ThinkingLevel.DEFAULT
        val containerColor = if (isNonDefault) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
        val borderColor = if (isNonDefault) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        }
        val contentColor = if (isNonDefault) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .height(32.dp)
                .onGloballyPositioned { coordinates ->
                    anchorWidthPx = coordinates.size.width
                }
                .clickable { isExpanded = true }
                .tip(stringResource(Res.string.chat_thinking_level_tip, currentLevel.displayName))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text(
                    text = if (useShortLabel) "🧠 ${currentLevel.shortLabel}" else "🧠 ${currentLevel.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor
                )
            }
        }

        val effectiveMaxWidth = minOf(380.dp, if (maxMenuWidth > 0.dp) maxMenuWidth else 380.dp)

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            offset = offset,
            properties = PopupProperties(focusable = true),
            modifier = Modifier
                .widthIn(min = effectiveMinWidth, max = maxOf(effectiveMinWidth, effectiveMaxWidth))
                .heightIn(max = maxMenuHeight)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.small)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.chat_thinking_level_menu_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "当前: ${currentLevel.shortLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = spacing.extraSmall),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                ThinkingLevel.entries.forEach { level ->
                    CommonThinkingLevelCard(
                        level = level,
                        isSelected = level == currentLevel,
                        onSelect = {
                            onLevelSelected(level)
                            isExpanded = false
                        }
                    )
                }
            }
        }
    }
}
