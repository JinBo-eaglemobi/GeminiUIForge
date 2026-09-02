package org.gemini.ui.forge.ui.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import org.gemini.ui.forge.formatTimestamp
import org.gemini.ui.forge.model.app.UIModule
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 首页展示的模块卡片组件。
 * 展示项目封面、名称、创建时间，点击卡片直接进入工作区，右下角提供打开目录与删除入口。
 *
 * @param module 模块元数据
 * @param onOpenWorkspace 点击打开工作区的回调
 * @param onOpenFileDir 点击打开本地目录的回调
 * @param onDelete 点击删除图标的回调
 */
@Composable
fun ModuleCard(
    module: UIModule,
    onOpenWorkspace: () -> Unit,
    onOpenFileDir: () -> Unit,
    onDelete: () -> Unit
) {
    val spacing = LocalAppSpacing.current

    Card(
        modifier = Modifier
            .size(280.dp, 400.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onOpenWorkspace() }
            .pointerHoverIcon(PointerIcon.Hand),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val title = if (module.nameRes != null) stringResource(module.nameRes) else module.nameStr ?: "Unknown"

            val coverUri = module.projectState?.pages?.firstOrNull()?.sourceImageUri
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri.getAbsolutePath(),
                    contentDescription = "Cover for $title",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No Preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val rawTitle =
                    if (module.nameRes != null) stringResource(module.nameRes) else module.nameStr ?: "Unknown"
                val displayTitle = if (rawTitle.length > 15) rawTitle.take(15) + "..." else rawTitle

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )

                    val createdAt = module.projectState?.createdAt ?: 0L
                    if (createdAt > 0L) {
                        Text(
                            text = formatTimestamp(createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .tip("删除项目")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Template",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
