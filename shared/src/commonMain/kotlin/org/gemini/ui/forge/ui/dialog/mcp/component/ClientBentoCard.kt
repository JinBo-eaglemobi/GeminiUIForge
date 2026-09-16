package org.gemini.ui.forge.ui.dialog.mcp.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.service.mcp.ClientAppConfigStatus
import org.gemini.ui.forge.service.mcp.McpClientType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.userHomePath

/**
 * 客户端生态 2-Column Bento 单卡组件
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun ClientBentoCard(
    status: ClientAppConfigStatus,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    // 图标与品牌配色映射
    val clientIcon = when (status.clientType) {
        McpClientType.OPEN_CODE -> Icons.Default.Terminal
        McpClientType.CLAUDE_CODE -> Icons.Default.SmartToy
        McpClientType.CLAUDE_DESKTOP -> Icons.Default.Computer
        McpClientType.GEMINI_CLI -> Icons.Default.AutoAwesome
        McpClientType.CURSOR -> Icons.Default.NearMe
    }

    val brandTint = when (status.clientType) {
        McpClientType.OPEN_CODE -> Color(0xFF00C853)
        McpClientType.CLAUDE_CODE, McpClientType.CLAUDE_DESKTOP -> Color(0xFFFF9800)
        McpClientType.GEMINI_CLI -> Color(0xFF2979FF)
        McpClientType.CURSOR -> Color(0xFF9C27B0)
    }

    // 将长绝对路径智能缩写为 ~ 开头友好形态展示
    val simplifiedPath = remember(status.configPath) {
        if (userHomePath.isNotBlank() && status.configPath.startsWith(userHomePath)) {
            "~" + status.configPath.substring(userHomePath.length)
        } else {
            status.configPath
        }
    }

    Card(
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (status.isConfigured) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            }
        ),
        modifier = modifier.border(
            width = 1.dp,
            color = if (status.isConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            shape = AppShapes.medium
        )
    ) {
        Column(modifier = Modifier.padding(spacing.medium).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = brandTint.copy(alpha = 0.15f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = clientIcon,
                                contentDescription = null,
                                tint = brandTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(spacing.small))
                    Text(
                        text = status.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 独立开关
                Switch(
                    checked = status.isConfigured,
                    onCheckedChange = onToggle,
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(Modifier.height(spacing.small))

            // 状态胶囊与路径
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = when {
                        status.isConfigured && status.isServiceDisabled -> Color(0xFFFF9800).copy(alpha = 0.15f)
                        status.isConfigured -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        status.isFileExists -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    },
                    shape = AppShapes.small
                ) {
                    Text(
                        text = when {
                            status.isConfigured && status.isServiceDisabled -> "已配置 (默认禁用)"
                            status.isConfigured -> "已接入并启用"
                            status.isFileExists -> "未配置"
                            else -> "未检测到配置"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            status.isConfigured && status.isServiceDisabled -> Color(0xFFE65100)
                            status.isConfigured -> Color(0xFF2E7D32)
                            status.isFileExists -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(spacing.small))

                Text(
                    text = simplifiedPath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).tip(status.configPath)
                )
            }
        }
    }
}
