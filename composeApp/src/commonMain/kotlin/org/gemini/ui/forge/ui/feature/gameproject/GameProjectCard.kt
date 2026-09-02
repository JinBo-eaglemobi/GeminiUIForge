package org.gemini.ui.forge.ui.feature.gameproject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_card_badge
import geminiuiforge.composeapp.generated.resources.gp_card_delete
import geminiuiforge.composeapp.generated.resources.gp_card_last_opened
import geminiuiforge.composeapp.generated.resources.gp_ws_pm
import geminiuiforge.composeapp.generated.resources.gp_ws_selected_game
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.formatTimestamp
import org.gemini.ui.forge.model.gameproject.GameProjectInfo
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 首页展示的已纳管游戏项目卡片。
 * 与模板卡片（ModuleCard）同尺寸（280x400dp）混排在同一条卡片带中，
 * 点击整张卡片直接进入项目工作台，右下角提供打开目录与删除入口。
 *
 * @param info 已纳管项目信息
 * @param onOpen 打开项目（进入专属工作台）回调
 * @param onOpenFileDir 打开项目本地磁盘目录回调
 * @param onDelete 移除管理记录回调
 */
@Composable
fun GameProjectCard(
    info: GameProjectInfo,
    onOpen: () -> Unit,
    onOpenFileDir: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Card(
        modifier = modifier
            .size(280.dp, 400.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onOpen() }
            .pointerHoverIcon(PointerIcon.Hand),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部类型角标：胶囊 + 游戏手柄图标
            Surface(
                shape = AppShapes.small,
                color = MaterialTheme.colorScheme.secondary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.gp_card_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            Spacer(Modifier.height(spacing.small))

            // 中部：游戏手柄大图标占位区
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.height(spacing.small))

            // 底部信息区与操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(Res.string.gp_ws_selected_game, info.selectedGame.ifBlank { "-" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(Res.string.gp_ws_pm, info.packageManager.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(Res.string.gp_card_last_opened, formatTimestamp(info.lastOpenedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }

                Row {
                    IconButton(
                        onClick = onOpenFileDir,
                        modifier = Modifier
                            .size(36.dp)
                            .tip("打开项目目录")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open Folder",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .tip("移除管理记录")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.gp_card_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
