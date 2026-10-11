package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.extend.contextMenuClickable
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.utils.findBlockById

/**
 * UI Studio 中间视图工作区顶部多页面标签栏组件 (Page Tab Bar)。
 *
 * 遵循一文件一 Composable 铁律。
 * 支持展示当前打开的视图列表、标签页切换、常规关闭 (×) 以及快捷新建视图。
 *
 * @param viewModel UI Studio 核心 ViewModel
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioPageTabBar(
    viewModel: UIStudioViewModel,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    val allPages = state.project?.pages ?: emptyList()
    val openIds = state.openViewIds
    val currentActiveId = state.currentViewId
    val scrollState = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.extraSmall)
        ) {
            // 水平滚动的标签容器
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                openIds.forEach { viewId ->
                    val page = allPages.find { it.id == viewId } ?: return@forEach
                    val isActive = viewId == currentActiveId
                    var showContextMenu by remember { mutableStateOf(false) }

                    val tabBgColor = if (isActive) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    }
                    val contentColor = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(tabBgColor)
                                .contextMenuClickable(
                                    onClick = { viewModel.selectView(viewId) },
                                    onContextMenu = { showContextMenu = true }
                                )
                                .padding(horizontal = spacing.medium)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewQuilt,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = page.nameStr,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            // 关闭标签按钮
                            IconButton(
                                onClick = { viewModel.closeViewTab(viewId) },
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .tip("关闭标签页")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        // 标签页右键菜单 (符合浮层设计规范，纯中文文案)
                        DropdownMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false },
                            modifier = Modifier
                                .widthIn(min = 160.dp, max = 220.dp)
                                .shadow(elevation = 10.dp, shape = RoundedCornerShape(10.dp))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    RoundedCornerShape(10.dp)
                                )
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("关闭标签页", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    showContextMenu = false
                                    viewModel.closeViewTab(viewId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("关闭其他标签页", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                enabled = openIds.size > 1,
                                onClick = {
                                    showContextMenu = false
                                    viewModel.closeOtherViewTabs(viewId)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp))
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "关闭全部标签页",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    showContextMenu = false
                                    viewModel.closeAllViewTabs()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))
                }

                // 2. 容器独立编辑视口标签项
                state.openContainerTabs.forEach { containerId ->
                    val containerBlock = state.currentView?.blocks?.findBlockById(containerId)
                    val isContainerActive = state.activeContainerId == containerId
                    val containerTitle = containerBlock?.id ?: containerId

                    val tabBgColor = if (isContainerActive) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    }
                    val contentColor = if (isContainerActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(tabBgColor)
                            .clickable { viewModel.selectContainerTab(containerId) }
                            .padding(horizontal = spacing.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = containerTitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isContainerActive) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        // 关闭容器标签按钮
                        IconButton(
                            onClick = { viewModel.closeContainerTab(containerId) },
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .tip("关闭容器视图")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))
                }
            }

            // 右侧一键关闭全部标签入口
            if (openIds.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.closeAllViewTabs() },
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .tip("关闭全部标签页")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "关闭全部",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // 右侧轻量新建页面入口
            IconButton(
                onClick = { viewModel.openNewViewDialog() },
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .tip("新建视图界面")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建视图",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
