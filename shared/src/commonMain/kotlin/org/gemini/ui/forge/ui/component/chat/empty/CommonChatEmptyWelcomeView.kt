package org.gemini.ui.forge.ui.component.chat.empty

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 纯前端居中欢迎与排版意图引导卡片（零持久化、零历史污染）
 *
 * 严格遵循 ui-designer.md：
 * 1. 视错觉垂直重心上提 (Optical Gravity Bias: -0.2f)；
 * 2. 8pt 网格与模块化字阶；
 * 3. 快捷建议卡片 36dp 桌面端标准光标热区。
 */
@Composable
fun CommonChatEmptyWelcomeView(
    viewName: String = "",
    viewSizeText: String = "",
    readyAssetCount: Int = 0,
    onSuggestionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = spacing.medium),
        contentAlignment = BiasAlignment(0f, -0.2f) // 视错觉重力上提，拒绝下坠感
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 发光立体图标容器 (48x48dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(spacing.medium))

            // 2. 主标题
            Text(
                text = "UI Studio 智能排版设计助理",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(spacing.extraSmall))

            // 3. 上下文状态胶囊徽章
            if (viewName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(vertical = spacing.extraSmall)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.small, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Dashboard,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "当前视图: $viewName${if (viewSizeText.isNotBlank()) " ($viewSizeText)" else ""} · 素材库就绪: $readyAssetCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.small))

            // 4. 辅助引导文案
            Text(
                text = "你可以向我描述界面排版设想、或者点击下方快捷预设，让 AI 自动规划槽位骨架并智能装配素材切片。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(spacing.large))

            // 5. 快捷建议胶囊列表（触控热区 >= 36dp）
            val suggestions = listOf(
                "构建标准 5x3 经典老虎机主滚轴与控制栏",
                "根据当前素材切片自动排版 HUD 状态栏",
                "规划底部 Spin 大按钮与下注控制区域"
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                suggestions.forEach { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSuggestionClick(suggestion) }
                            .tip("点击快速填入此意图")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
