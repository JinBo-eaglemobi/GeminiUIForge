package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.code_block_collapse_tip
import geminiuiforge.composeapp.generated.resources.code_block_collapsed_lines_hint
import geminiuiforge.composeapp.generated.resources.code_block_copied_tip
import geminiuiforge.composeapp.generated.resources.code_block_copy_tip
import geminiuiforge.composeapp.generated.resources.code_block_expand_tip
import geminiuiforge.composeapp.generated.resources.code_block_fold_all_scopes_tip
import geminiuiforge.composeapp.generated.resources.code_block_unfold_all_scopes_tip
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * Markdown 代码块专用顶部控制操作栏组件 (一文件一 Composable 规范)。
 *
 * 核心特性：
 * 1. 左侧展示语言 Tag（大写 monospace 等宽字体）及折叠预览行数元信息；
 * 2. 右侧展示一键折叠/展开全部子作用域、一键复制代码按钮与代码块整体折叠/展开切换图标；
 * 3. 支持点击头部区域快速折叠/展开；
 * 4. 严格遵循嵌套圆角、四阶图标按钮守恒与 I18n 多语言规范。
 */
@Composable
fun AppMarkdownCodeHeader(
    language: String?,
    lineCount: Int,
    isCollapsed: Boolean,
    isCopied: Boolean,
    onToggleCollapse: () -> Unit,
    onCopyClick: () -> Unit,
    hasFoldableScopes: Boolean = false,
    areAllScopesFolded: Boolean = false,
    onToggleAllScopes: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copiedTip = stringResource(Res.string.code_block_copied_tip)
    val copyTip = stringResource(Res.string.code_block_copy_tip)
    val expandTip = stringResource(Res.string.code_block_expand_tip)
    val collapseTip = stringResource(Res.string.code_block_collapse_tip)
    val foldAllScopesTip = stringResource(Res.string.code_block_fold_all_scopes_tip)
    val unfoldAllScopesTip = stringResource(Res.string.code_block_unfold_all_scopes_tip)

    DisableSelection {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF181A1F))
                .clickable(onClick = onToggleCollapse)
                .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Text(
                    text = language?.uppercase()?.ifBlank { "TEXT" } ?: "TEXT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF9DA5B4)
                    )
                )

                if (isCollapsed && lineCount > 0) {
                    Text(
                        text = stringResource(Res.string.code_block_collapsed_lines_hint, lineCount),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = Color(0xFF5C6370)
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                // 一键折叠 / 展开全部内部方法或节点作用域按钮
                if (hasFoldableScopes && !isCollapsed && onToggleAllScopes != null) {
                    val scopeTip = if (areAllScopesFolded) unfoldAllScopesTip else foldAllScopesTip
                    AppIconButton(
                        onClick = onToggleAllScopes,
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = scopeTip,
                        tooltip = scopeTip,
                        tier = AppIconButtonTier.COMPACT,
                        tint = if (areAllScopesFolded) Color(0xFF61AFEF) else Color(0xFFABB2BF)
                    )
                }

                // 一键复制代码按钮
                AppIconButton(
                    onClick = onCopyClick,
                    tier = AppIconButtonTier.COMPACT,
                    tooltip = if (isCopied) copiedTip else copyTip
                ) {
                    Crossfade(targetState = isCopied) { copied ->
                        if (copied) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = copiedTip,
                                tint = Color(0xFF98C379),
                                modifier = Modifier.size(spacing.iconCompactSize)
                            )
                        } else {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = copyTip,
                                tint = Color(0xFFABB2BF),
                                modifier = Modifier.size(spacing.iconCompactSize)
                            )
                        }
                    }
                }

                // 整块代码折叠 / 展开状态切换按钮
                AppIconButton(
                    onClick = onToggleCollapse,
                    tier = AppIconButtonTier.COMPACT,
                    tooltip = if (isCollapsed) expandTip else collapseTip
                ) {
                    Crossfade(targetState = isCollapsed) { collapsed ->
                        if (collapsed) {
                            Icon(
                                Icons.Default.UnfoldMore,
                                contentDescription = expandTip,
                                tint = Color(0xFFABB2BF),
                                modifier = Modifier.size(spacing.iconCompactSize)
                            )
                        } else {
                            Icon(
                                Icons.Default.UnfoldLess,
                                contentDescription = collapseTip,
                                tint = Color(0xFFABB2BF),
                                modifier = Modifier.size(spacing.iconCompactSize)
                            )
                        }
                    }
                }
            }
        }
    }
}
