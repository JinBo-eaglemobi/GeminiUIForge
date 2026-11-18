package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.model.UIStudioRightTab
import org.jetbrains.compose.resources.stringResource

/**
 * UI Studio 右侧边栏顶部控制头组件 (Right Sidebar Header)。
 * 高度、间距、图标尺寸与字号 100% 动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun UIStudioRightSidebarHeader(
    selectedTab: UIStudioRightTab,
    onTabSelected: (UIStudioRightTab) -> Unit,
    onOpenFullDialog: () -> Unit,
    onCollapseSidebar: () -> Unit,
    modifier: Modifier = Modifier,
    isAiChatDialogVisible: Boolean = false
) {
    val spacing = LocalAppSpacing.current
    val colorScheme = MaterialTheme.colorScheme
    val dynamicTabStyle = if (spacing.isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge

    Surface(
        color = colorScheme.surface,
        modifier = modifier.fillMaxWidth().height(spacing.rightSidebarHeaderHeight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isAiChatDialogVisible) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = spacing.extraSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(spacing.inspectorInlineIconSize),
                            tint = colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(spacing.inspectorControlGap))
                        Text(
                            text = stringResource(Res.string.ui_studio_tab_property),
                            style = dynamicTabStyle.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.onSurface
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(AppShapes.small)
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // 属性检查器 Tab
                        val isPropActive = selectedTab == UIStudioRightTab.PROPERTY
                        val propBg by animateColorAsState(
                            if (isPropActive) colorScheme.primaryContainer else Color.Transparent,
                            animationSpec = tween(180)
                        )
                        val propContentColor by animateColorAsState(
                            if (isPropActive) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                            animationSpec = tween(180)
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(spacing.inspectorFieldCornerRadius))
                                .background(propBg)
                                .clickable { onTabSelected(UIStudioRightTab.PROPERTY) }
                                .padding(horizontal = spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(spacing.inspectorInlineIconSize),
                                tint = propContentColor
                            )
                            Spacer(modifier = Modifier.width(spacing.inspectorControlGap))
                            Text(
                                text = stringResource(Res.string.ui_studio_tab_property),
                                style = dynamicTabStyle.copy(
                                    fontWeight = if (isPropActive) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = propContentColor,
                                maxLines = 1
                            )
                        }

                        // AI 助手 Tab
                        val isAiActive = selectedTab == UIStudioRightTab.AI_CHAT
                        val aiBg by animateColorAsState(
                            if (isAiActive) colorScheme.primaryContainer else Color.Transparent,
                            animationSpec = tween(180)
                        )
                        val aiContentColor by animateColorAsState(
                            if (isAiActive) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                            animationSpec = tween(180)
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(spacing.inspectorFieldCornerRadius))
                                .background(aiBg)
                                .clickable { onTabSelected(UIStudioRightTab.AI_CHAT) }
                                .padding(horizontal = spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(spacing.inspectorInlineIconSize),
                                tint = aiContentColor
                            )
                            Spacer(modifier = Modifier.width(spacing.inspectorControlGap))
                            Text(
                                text = stringResource(Res.string.ui_studio_tab_ai_chat),
                                style = dynamicTabStyle.copy(
                                    fontWeight = if (isAiActive) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = aiContentColor,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(spacing.small))

                // 2. 右侧动作按钮区 (弹出大窗 + 收起侧栏)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!isAiChatDialogVisible) {
                        AppIconButton(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(Res.string.ui_studio_tab_open_dialog_tip),
                            onClick = onOpenFullDialog,
                            tier = AppIconButtonTier.COMPACT,
                            tooltip = stringResource(Res.string.ui_studio_tab_open_dialog_tip)
                        )
                    }

                    AppIconButton(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(Res.string.ui_studio_tab_collapse_tip),
                        onClick = onCollapseSidebar,
                        tier = AppIconButtonTier.COMPACT,
                        tooltip = stringResource(Res.string.ui_studio_tab_collapse_tip)
                    )
                }
            }

            // 底部 1dp 细分隔线
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}
