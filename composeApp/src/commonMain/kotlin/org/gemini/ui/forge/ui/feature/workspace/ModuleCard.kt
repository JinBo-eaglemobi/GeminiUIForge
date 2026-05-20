package org.gemini.ui.forge.ui.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import org.gemini.ui.forge.formatTimestamp
import org.gemini.ui.forge.model.app.UIModule
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.getPlatform

/**
 * 首页展示的模块卡片组件。
 * 展示项目封面、名称、创建时间，并提供进入工作区和删除项目的入口。
 *
 * @param module 模块元数据。
 * @param onOpenWorkspace 点击“打开工作区”的回调。
 * @param onDelete 点击删除图标的回调。
 */
@Composable
fun ModuleCard(
    module: UIModule,
    onOpenWorkspace: () -> Unit,
    onOpenFileDir: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.size(280.dp, 400.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
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

            Spacer(modifier = Modifier.height(12.dp))
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
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open Folder",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Template",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onOpenWorkspace,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium
            ) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_open_workspace), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
