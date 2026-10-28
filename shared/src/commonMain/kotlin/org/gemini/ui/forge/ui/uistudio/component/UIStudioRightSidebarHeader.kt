package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.model.UIStudioRightTab
import org.jetbrains.compose.resources.stringResource

/**
 * UI Studio 右侧边栏顶部控制头组件 (Right Sidebar Header)。
 *
 * 遵循一文件一 Composable 铁律与设计系统 Tokens 规范：
 * - 紧凑高度 40dp，内置曲率守恒分段切换器 [UIStudioRightTab.PROPERTY] 与 [UIStudioRightTab.AI_CHAT]；
 * - 右侧快捷工具：一键逃逸至独立大弹窗 [onOpenFullDialog]、一键折叠收起边栏 [onCollapseSidebar]；
 * - 完备的 Hover/Active 微交互与 I18n 悬浮提示。
 *
 * @param selectedTab 当前激活的右侧边栏分页
 * @param onTabSelected 切换分页回调
 * @param onOpenFullDialog 弹出独立全景大弹窗回调
 * @param onCollapseSidebar 折叠收起右侧边栏回调
 * @param modifier 外部修饰符
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

    Surface(
        color = colorScheme.surface,
        modifier = modifier.fillMaxWidth().height(40.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.small, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. 左侧区域：大窗打开时锁定纯属性标题；大窗未打开时展示分段切换器 (Segmented Tab Bar)
                if (isAiChatDialogVisible) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(Res.string.ui_studio_tab_property),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.onSurface
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(propBg)
                                .clickable { onTabSelected(UIStudioRightTab.PROPERTY) }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = propContentColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(Res.string.ui_studio_tab_property),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(aiBg)
                                .clickable { onTabSelected(UIStudioRightTab.AI_CHAT) }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = aiContentColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(Res.string.ui_studio_tab_ai_chat),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
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
                    // 弹出独立大窗按钮（仅当大窗未打开时展示，杜绝重复弹出）
                    if (!isAiChatDialogVisible) {
                        IconButton(
                            onClick = onOpenFullDialog,
                            modifier = Modifier
                                .size(28.dp)
                                .tip(stringResource(Res.string.ui_studio_tab_open_dialog_tip))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(Res.string.ui_studio_tab_open_dialog_tip),
                                modifier = Modifier.size(14.dp),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 收起侧栏按钮
                    IconButton(
                        onClick = onCollapseSidebar,
                        modifier = Modifier
                            .size(28.dp)
                            .tip(stringResource(Res.string.ui_studio_tab_collapse_tip))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(Res.string.ui_studio_tab_collapse_tip),
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
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
