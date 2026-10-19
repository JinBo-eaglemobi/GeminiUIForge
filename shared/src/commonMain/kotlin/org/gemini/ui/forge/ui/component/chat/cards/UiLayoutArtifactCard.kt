package org.gemini.ui.forge.ui.component.chat.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 界面排版装配方案工坊卡片数据载荷
 */
data class LayoutArtifactData(
    val viewName: String,
    val resolution: String = "1280 × 720",
    val description: String = "",
    val modules: List<LayoutModuleSummary> = emptyList(),
    val rawJsonSnippet: String = ""
)

/**
 * 单个模块架构大纲
 */
data class LayoutModuleSummary(
    val title: String,
    val typeName: String,
    val assetFileName: String? = null,
    val detailCount: String? = null
)

/**
 * 主流 AI 工具标准：界面排版装配工坊卡片组件 (一文件一 Composable)
 *
 * 对标 Claude Artifacts 与 Cursor 工业设计规范：
 * 1. 结构化呈现生成的视图名称、设计构思与核心功能模块；
 * 2. 彻底消灭千行长串 JSON 倾倒缺陷；
 * 3. 36dp 舒适热区操作按键群（在画布中打开、复制排版 JSON、折叠查看原始数据）。
 */
@Composable
fun UiLayoutArtifactCard(
    artifact: LayoutArtifactData,
    onOpenViewRequested: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()
    var isRawJsonExpanded by remember { mutableStateOf(false) }

    val cardBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Surface(
        color = cardBgColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.small)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. 顶部 Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ViewQuilt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "页面排版装配方案",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "UI Studio Artifact · ${artifact.resolution}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "✓ 已就绪",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 2. 目标视图药丸
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🗖 目标视图:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = artifact.viewName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3. 设计概述说明
            if (artifact.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = artifact.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            // 4. 核心功能模块清单
            if (artifact.modules.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "❖ 核心模块排版架构",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    artifact.modules.forEach { mod ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val icon = when {
                                        mod.typeName.contains("BG") || mod.typeName.contains("BACK") -> "🖼️"
                                        mod.typeName.contains("REEL") || mod.typeName.contains("SLOT") -> "🎰"
                                        mod.typeName.contains("BTN") || mod.typeName.contains("BUTTON") -> "🔘"
                                        mod.typeName.contains("NAV") || mod.typeName.contains("TOP") -> "🔝"
                                        else -> "❖"
                                    }
                                    Text(text = icon, fontSize = 12.sp)
                                    Text(
                                        text = mod.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (mod.assetFileName != null) {
                                        Text(
                                            text = "(${mod.assetFileName})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                    }
                                }

                                if (mod.detailCount != null) {
                                    Text(
                                        text = mod.detailCount,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 5. 底部操作按键群
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenViewRequested?.invoke(artifact.viewName) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("在画布中打开视图", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val textToCopy = artifact.rawJsonSnippet.ifBlank { artifact.description }
                            copy(textToCopy, "已复制排版数据")
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制配置", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (artifact.rawJsonSnippet.isNotBlank()) {
                    TextButton(
                        onClick = { isRawJsonExpanded = !isRawJsonExpanded },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (isRawJsonExpanded) "收起数据 ▴" else "展开数据 ▾",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 6. 折叠的原始 JSON 数据区
            AnimatedVisibility(visible = isRawJsonExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E1E24),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    ) {
                        Text(
                            text = artifact.rawJsonSnippet,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE0E0E0),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
