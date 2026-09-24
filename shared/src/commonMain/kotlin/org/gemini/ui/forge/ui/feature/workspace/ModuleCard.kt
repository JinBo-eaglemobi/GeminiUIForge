package org.gemini.ui.forge.ui.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.ui.component.ToastType

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
    val generatingProjects by UiRoadmapRegistry.generatingProjects.collectAsState()
    val matchedGeneratingEntry = remember(generatingProjects, module.id, module.nameStr) {
        val targets = listOfNotNull(module.id, module.nameStr)
        generatingProjects.entries.firstOrNull { (key, _) ->
            val normKey = key.trim().replace(" ", "_")
            targets.any { t -> t == key || t.trim().replace(" ", "_").equals(normKey, ignoreCase = true) }
        }
    }
    val isGenerating = matchedGeneratingEntry != null
    val generatingStatus = matchedGeneratingEntry?.value ?: "AI 逆向生成中..."

    Card(
        modifier = Modifier
            .size(280.dp, 400.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                if (isGenerating) {
                    Toast.show("当前模块正在由 AI 反向生成中，为保证数据完整性暂禁止进入工作区", ToastType.INFO)
                } else {
                    onOpenWorkspace()
                }
            }
            .pointerHoverIcon(if (isGenerating) PointerIcon.Default else PointerIcon.Hand),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val title = if (module.nameRes != null) stringResource(module.nameRes) else module.nameStr ?: "Unknown"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                val coverUri = module.projectState?.pages?.firstOrNull()?.sourceImageUri
                if (coverUri != null) {
                    AsyncImage(
                        model = coverUri.getAbsolutePath(),
                        contentDescription = "Cover for $title",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.LightGray),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // AI 反向生成中动效与状态遮罩
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(spacing.small))
                            Text(
                                text = generatingStatus,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = spacing.small)
                            )
                        }
                    }
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
                        onClick = {
                            if (isGenerating) {
                                Toast.show("当前模块正在由 AI 逆向生成中，禁止删除", ToastType.INFO)
                            } else {
                                onDelete()
                            }
                        },
                        enabled = !isGenerating,
                        modifier = Modifier
                            .size(36.dp)
                            .tip(if (isGenerating) "AI 生成中暂不可删除" else "删除项目")
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
