package org.gemini.ui.forge.ui.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
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

    val rawTitle = if (module.nameRes != null) stringResource(module.nameRes) else module.nameStr ?: "Unknown"

    Card(
        modifier = Modifier
            .size(280.dp, 400.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable {
                if (isGenerating) {
                    Toast.show("当前模块正在由 AI 反向生成中，为保证数据完整性暂禁止进入工作区", ToastType.INFO)
                } else {
                    onOpenWorkspace()
                }
            }
            .pointerHoverIcon(if (isGenerating) PointerIcon.Default else PointerIcon.Hand),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 封面与预览区域（自适应充满上半部）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val firstPage = module.projectState?.pages?.firstOrNull()
                val pageCover = firstPage?.sourceImageUri
                val blockCover = firstPage?.blocks?.firstOrNull { it.currentImageUri != null }?.currentImageUri

                when {
                    pageCover != null -> {
                        AsyncImage(
                            model = pageCover.getAbsolutePath(),
                            contentDescription = "Cover for $rawTitle",
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.LightGray),
                            contentScale = ContentScale.Crop
                        )
                    }
                    blockCover != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = blockCover.getAbsolutePath(),
                                contentDescription = "Preview asset for $rawTitle",
                                modifier = Modifier
                                    .size(110.dp)
                                    .padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    else -> {
                        // Blueprint 蓝图科技风格封面
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                val resolutionText = if (firstPage != null && firstPage.width > 0f) {
                                    "${firstPage.width.toInt()} × ${firstPage.height.toInt()}"
                                } else {
                                    "UI Studio"
                                }
                                Surface(
                                    shape = RoundedCornerShape(9999.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = resolutionText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 左上角浮动类型徽章（彻底解耦，不占底部水平空间）
                if (module.projectState?.isUiStudioProject == true) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "UI Studio",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = spacing.small)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 底部信息区：标题整行舒展，元数据与操作图标分层排布
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. 标题独占第一行
                Text(
                    text = rawTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                // 2. 第二行：时间与操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val createdAt = module.projectState?.createdAt ?: 0L
                    if (createdAt > 0L) {
                        Text(
                            text = formatTimestamp(createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "本地工程",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onOpenFileDir,
                            modifier = Modifier
                                .size(32.dp)
                                .tip("打开项目目录")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open Folder",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                .size(32.dp)
                                .tip(if (isGenerating) "AI 生成中暂不可删除" else "删除项目")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Template",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}
