package org.gemini.ui.forge.ui.uistudio.verify.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.verify.model.MattingStepItem

/**
 * 原图 AI 去背 Task 步骤列式进度展示卡片 (一文件一 Composable)
 */
@Composable
fun MattingTaskProgressCard(
    steps: List<MattingStepItem>,
    totalElapsedSeconds: Float,
    overallProgressRatio: Float?,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 16.dp,
        modifier = modifier.width(600.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 顶部 Header 标题与总耗时药丸
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).padding(end = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI 原图智能去背流水线",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "正在执行多阶段高精度图元解耦与 Alpha 计算",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 总耗时统计胶囊 (保持纯粹单行不折行)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "总计 ⏱️ ${((totalElapsedSeconds * 10).toInt() / 10f)}s",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // 2. 中间 Task 步骤纵向列式清单 (4 个大步骤)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                steps.forEach { step ->
                    MattingTaskStepRow(step = step)
                }
            }

            // 3. 底部总进度条与提示
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (overallProgressRatio != null) {
                    LinearProgressIndicator(
                        progress = { overallProgressRatio },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                    )
                }

                Text(
                    text = "去背完成后切片将直接输出高品质透明 PNG 素材",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
                )
            }
        }
    }
}
