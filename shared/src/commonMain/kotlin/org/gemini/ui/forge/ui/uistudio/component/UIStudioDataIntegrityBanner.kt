package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.model.UIStudioDataError

/**
 * UI Studio 画布顶部常驻的数据健康与完整性诊断横幅。
 * 当工程中存在图元引用关系异常（如引用页面却含有冗余子图元、引用页面不存在）时长期常驻展示，
 * 详细指出错误所在页面、图元位置及异常原因，并提供一键定位直达入口。
 */
@Composable
fun UIStudioDataIntegrityBanner(
    errors: List<UIStudioDataError>,
    onNavigateToPage: (pageId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (errors.isEmpty()) return

    val spacing = LocalAppSpacing.current
    var isExpanded by remember { mutableStateOf(false) }

    val warningBg = Color(0xFFFEF2F2).copy(alpha = 0.95f)
    val warningBorder = Color(0xFFFCA5A5)
    val warningIconColor = Color(0xFFEF4444)
    val warningTextColor = Color(0xFF991B1B)
    val warningSubTextColor = Color(0xFFB91C1C)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium, vertical = spacing.extraSmall)
            .background(warningBg, RoundedCornerShape(8.dp))
            .border(1.dp, warningBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = spacing.medium, vertical = spacing.small)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "数据异常警告",
                        tint = warningIconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(
                        text = "数据完整性警示 (检测到 ${errors.size} 处数据异常)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = warningTextColor
                    )
                    Spacer(modifier = Modifier.width(spacing.medium))
                    if (!isExpanded) {
                        Text(
                            text = errors.first().message,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = warningSubTextColor,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isExpanded) "收起详情" else "展开详情 (${errors.size})",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = warningIconColor
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = warningIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.small)
                ) {
                    errors.forEachIndexed { index, error ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Text(
                                text = "${index + 1}. ${error.message}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = warningTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onNavigateToPage(error.pageId) }
                                    .padding(start = spacing.small)
                            ) {
                                Text(
                                    text = "前往该页面",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = warningIconColor
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "前往",
                                    tint = warningIconColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
