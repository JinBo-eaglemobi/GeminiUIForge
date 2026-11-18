package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.component.AppIconButtonVariant
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * UI Studio 素材多选批量操作条组件。
 *
 * 当在素材库中多选切片 (selectedAssetHashes.size > 1) 时浮现展示，
 * 提供已选项数提示、一键全选、清空选择以及批量删除二次确认触发。
 *
 * @param selectedCount 当前选中的素材数量
 * @param onSelectAll 全选回调
 * @param onClearSelection 清空选择回调
 * @param onDeleteRequested 触发批量删除请求回调
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioAssetBatchBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBadgeStyle = if (spacing.isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    AnimatedVisibility(
        visible = selectedCount > 1,
        enter = fadeIn(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(spacing.inspectorFieldCornerRadius),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.small, vertical = spacing.inspectorFieldPaddingVertical),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧已选数量徽章
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(spacing.inspectorInlineIconSize)
                    )
                    Text(
                        text = "已选 $selectedCount 项",
                        style = dynamicBadgeStyle.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // 右侧操作按钮组
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 全选
                    AppIconButton(
                        imageVector = Icons.Default.SelectAll,
                        onClick = onSelectAll,
                        tier = AppIconButtonTier.MICRO,
                        variant = AppIconButtonVariant.GHOST,
                        tooltip = "全选当前可见素材"
                    )

                    // 清空选择
                    AppIconButton(
                        imageVector = Icons.Default.Close,
                        onClick = onClearSelection,
                        tier = AppIconButtonTier.MICRO,
                        variant = AppIconButtonVariant.GHOST,
                        tooltip = "取消多选 (Esc)"
                    )

                    // 批量删除
                    AppIconButton(
                        imageVector = Icons.Default.Delete,
                        onClick = onDeleteRequested,
                        tier = AppIconButtonTier.MICRO,
                        variant = AppIconButtonVariant.FILLED_TONAL,
                        tint = MaterialTheme.colorScheme.error,
                        tooltip = "批量彻底删除所选 $selectedCount 个素材"
                    )
                }
            }
        }
    }
}
