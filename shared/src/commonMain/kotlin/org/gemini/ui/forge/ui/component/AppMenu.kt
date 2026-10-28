package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.PopupProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.app_menu_empty_default
import geminiuiforge.composeapp.generated.resources.app_menu_item_count
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * AppMenu 几何档位——浮层菜单几何规范的物化载体。
 * 全项目菜单宽度数值唯一来源；修改此处即全局生效，业务方永远不写宽度常量。
 *
 * @param minWidth 档位最小宽度下限
 * @param maxWidth 档位最大宽度上限；null 表示无固定上限，动态取视口宽（黄金比例 85%）
 * @param hardClamp 可选额外物理安全钳制；默认 null 不生效。生效时最终宽度恒 ≤ 该值（与 maxWidth、显式 maxMenuWidth 三者取小）
 */
enum class AppMenuTier(
    val minWidth: Dp,
    val maxWidth: Dp?,
    val hardClamp: Dp? = null
) {
    /** Tier 2 单列枚举/紧凑调节（思考等级、协议切换、比例、步数等）：min 280，max 动态视口黄金比例 */
    COMPACT(minWidth = 280.dp, maxWidth = null),

    /** Tier 1 复杂宽屏/多分组面板（模型选择、技能选择、素材面板等）：min 320，max 动态视口 85% */
    WIDE(minWidth = 320.dp, maxWidth = null)
}

/**
 * 槽位化全项目统一浮层菜单组件。【红线】业务代码严禁再裸用 DropdownMenu。
 *
 * 内置自动合规（业务方零感知，永不偏离）：
 * 1. 视口自适应：内部 BoxWithConstraints 感知父容器宽度，无固定上限档（maxWidth = null）的 Tier 上限 = 容器宽 * 0.85f；
 * 2. Tier 几何守卫：effectiveMax 与 tier.maxWidth、maxMenuWidth、tier.hardClamp 自动取小，并根据 anchorWidth 锚定；
 * 3. 标准视觉骨架：surface 底 + 1dp outlineVariant(0.6f) 边框 + AppShapes.medium 圆角 + 默认 offset 向上弹出(0, -8)；
 * 4. 标准结构骨架：标题栏(标题 + 计数徽章) → 分割线 → 头部插槽 → 内容滚动区 → 底部插槽。
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    tier: AppMenuTier = AppMenuTier.COMPACT,
    maxMenuWidth: Dp? = null,
    anchorWidth: Dp? = null,
    title: String? = null,
    totalCount: Int? = null,
    offset: DpOffset = DpOffset(0.dp, (-8).dp),
    maxMenuHeight: Dp? = null,
    isUpwardExplicit: Boolean? = null,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    footerContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = LocalAppSpacing.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    // 捕获触发锚点在窗口中的绝对物理几何坐标
    var anchorTopInWindowDp by remember { mutableStateOf<Dp?>(null) }
    var anchorBottomInWindowDp by remember { mutableStateOf<Dp?>(null) }

    val measureModifier = modifier.onGloballyPositioned { coords ->
        val pos = coords.positionInWindow()
        anchorTopInWindowDp = with(density) { pos.y.toDp() }
        anchorBottomInWindowDp = with(density) { (pos.y + coords.size.height).toDp() }
    }

    BoxWithConstraints(modifier = measureModifier) {
        val windowWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
        val windowHeightDp = with(density) { windowInfo.containerSize.height.toDp() }

        val safeViewportWidth = if (windowWidthDp > 0.dp) windowWidthDp else (if (maxWidth.isSpecified && maxWidth < 10000.dp) maxWidth else 800.dp)
        val safeViewportHeight = if (windowHeightDp > 0.dp) windowHeightDp else 720.dp

        // 1. 宽度几何守卫
        val tierMax = tier.maxWidth ?: (safeViewportWidth * 0.85f)
        val candidateMaxList = listOfNotNull(tierMax, maxMenuWidth, tier.hardClamp)
        val effectiveMax = minOf(
            if (candidateMaxList.isNotEmpty()) candidateMaxList.min() else safeViewportWidth,
            safeViewportWidth
        )
        val effectiveMin = maxOf(tier.minWidth, anchorWidth ?: 0.dp).coerceAtMost(effectiveMax)

        // 2. 高度几何算法：按弹出方向计算可用空间并保留安全呼吸边距 (防紧挨边缘)
        val safetyMargin = 20.dp
        val isUpward = isUpwardExplicit ?: (offset.y < 0.dp)

        val directionalAvailableHeight = if (isUpward) {
            // 向上显示：从目标显示开始位置到窗口最上方 (0dp) 的可用距离，减去呼吸间距
            val startY = anchorTopInWindowDp?.let { it + offset.y } ?: (safeViewportHeight - 64.dp)
            (startY - safetyMargin).coerceAtLeast(160.dp)
        } else {
            // 向下显示：从目标显示开始位置到窗口最下方位置的可用距离，减去呼吸间距
            val startY = anchorBottomInWindowDp?.let { it + offset.y } ?: 48.dp
            (safeViewportHeight - startY - safetyMargin).coerceAtLeast(160.dp)
        }

        val effectiveMaxHeightLimit = maxMenuHeight ?: 520.dp
        val effectiveHeight = minOf(effectiveMaxHeightLimit, directionalAvailableHeight).coerceIn(180.dp, 680.dp)

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
            offset = offset,
            modifier = Modifier
                .widthIn(min = effectiveMin, max = effectiveMax)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), AppShapes.medium)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.small),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. 标题栏
                if (!title.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (totalCount != null) {
                            Surface(
                                shape = AppShapes.extraSmall,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = stringResource(Res.string.app_menu_item_count, totalCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }

                // 2. 头部扩展插槽（如搜索框、操作按钮行）
                headerContent?.invoke(this)

                // 3. 候选条目列表区（带滚动）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = effectiveHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    content()
                }

                // 4. 底部扩展插槽（如新建等常驻操作）
                footerContent?.invoke(this)
            }
        }
    }
}

/**
 * 数据驱动型 AppMenu 重载版本：自动处理 List<T> 的平铺、分组、空态与选择。
 */
@Composable
fun <T> AppMenu(
    expanded: Boolean,
    items: List<T>,
    onDismissRequest: () -> Unit,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    tier: AppMenuTier = AppMenuTier.COMPACT,
    maxMenuWidth: Dp? = null,
    anchorWidth: Dp? = null,
    title: String? = null,
    offset: DpOffset = DpOffset(0.dp, (-8).dp),
    maxMenuHeight: Dp? = null,
    isUpwardExplicit: Boolean? = null,
    itemTitle: ((T) -> String)? = null,
    itemSubtitle: ((T) -> String)? = null,
    itemIcon: ((T) -> ImageVector)? = null,
    itemTrailing: ((T) -> String)? = null,
    isSelected: ((T) -> Boolean)? = null,
    itemEnabled: ((T) -> Boolean)? = null,
    grouped: ((T) -> Enum<*>)? = null,
    sectionTitle: ((Enum<*>) -> String)? = null,
    itemContent: (@Composable ColumnScope.(T) -> Unit)? = null,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    footerContent: (@Composable ColumnScope.() -> Unit)? = null,
    emptyContent: (@Composable ColumnScope.() -> Unit)? = null,
    emptyText: String? = null
) {
    val spacing = LocalAppSpacing.current

    AppMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        tier = tier,
        maxMenuWidth = maxMenuWidth,
        anchorWidth = anchorWidth,
        title = title,
        totalCount = items.size,
        offset = offset,
        maxMenuHeight = maxMenuHeight,
        isUpwardExplicit = isUpwardExplicit,
        headerContent = headerContent,
        footerContent = footerContent
    ) {
        if (items.isEmpty()) {
            if (emptyContent != null) {
                emptyContent()
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyText ?: stringResource(Res.string.app_menu_empty_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (grouped != null) {
            // 分组渲染
            val groupedItems = items.groupBy(grouped)
            groupedItems.forEach { (groupKey, groupItemList) ->
                val groupTitleText = sectionTitle?.invoke(groupKey) ?: groupKey.name
                AppMenuSectionHeader(title = groupTitleText)
                groupItemList.forEach { item ->
                    if (itemContent != null) {
                        itemContent(item)
                    } else {
                        val rowTitle = itemTitle?.invoke(item) ?: item.toString()
                        val rowSubtitle = itemSubtitle?.invoke(item)
                        val rowIcon = itemIcon?.invoke(item)
                        val rowTrailing = itemTrailing?.invoke(item)
                        val selected = isSelected?.invoke(item) ?: false
                        val enabled = itemEnabled?.invoke(item) ?: true
                        AppMenuItemRow(
                            title = rowTitle,
                            subtitle = rowSubtitle,
                            icon = rowIcon,
                            trailingBadge = rowTrailing,
                            isSelected = selected,
                            isEnabled = enabled,
                            onClick = {
                                onItemSelected(item)
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        } else {
            // 平铺渲染
            items.forEach { item ->
                if (itemContent != null) {
                    itemContent(item)
                } else {
                    val rowTitle = itemTitle?.invoke(item) ?: item.toString()
                    val rowSubtitle = itemSubtitle?.invoke(item)
                    val rowIcon = itemIcon?.invoke(item)
                    val rowTrailing = itemTrailing?.invoke(item)
                    val selected = isSelected?.invoke(item) ?: false
                    val enabled = itemEnabled?.invoke(item) ?: true
                    AppMenuItemRow(
                        title = rowTitle,
                        subtitle = rowSubtitle,
                        icon = rowIcon,
                        trailingBadge = rowTrailing,
                        isSelected = selected,
                        isEnabled = enabled,
                        onClick = {
                            onItemSelected(item)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}
