package org.gemini.ui.forge.ui.dialog.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.rememberFilePicker
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel

/**
 * 全局高级设置对话框（800dp PC 宽屏超大视口重构版）。
 * 负责管理风格参考图（图生图引导）以及全项目通用的风格提示词。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSettingsDialog(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val projectName = state.projectName.replace(" ", "_")
    val projectAssetsBase = TemplateFile("templates/$projectName/assets")

    val imagePicker = rememberFilePicker(
        title = "选择风格参考图",
        isFolder = false,
        extensions = listOf("png", "jpg", "jpeg", "webp"),
        initialPath = projectAssetsBase.getAbsolutePath(),
        onResult = { uri ->
            uri?.let { viewModel.assetManager.setReferenceImageExternal(it) }
        }
    )

    var currentStyleText by remember(state.globalStyle) { mutableStateOf(state.globalStyle) }

    val popularStyleTags = listOf(
        "赛博朋克霓虹",
        "日式二次元动漫",
        "暗黑奇幻哥特",
        "写实3D极简渲染",
        "极简拟物扁平",
        "流光晶莹金属质感"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(800.dp)
                .wrapContentHeight(),
            shape = AppShapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(spacing.large)) {
                // 1. 顶栏 Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(spacing.small))
                        Column {
                            Text(
                                text = "全局风格与参考设置",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "配置项目统一的美术风格先验与全局提示词约束",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).tip("关闭")
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(spacing.small))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(spacing.medium))

                // 2. 板块一：风格参考图（280dp × 200dp 超大视口高清预览）
                Text(
                    text = "🎨 风格参考图 (图生图全局美术先验)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(spacing.small))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 超大视口参考图预览容器 (280dp × 200dp)
                    Surface(
                        modifier = Modifier
                            .width(280.dp)
                            .height(200.dp)
                            .clip(AppShapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), AppShapes.medium)
                            .clickable { imagePicker() }
                            .tip("点击选择本地图片作为全案 AI 生图的风格参考底图"),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                            if (state.referenceImageUri != null) {
                                AsyncImage(
                                    model = state.referenceImageUri.getAbsolutePath(),
                                    contentDescription = "Global Style Reference",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    Spacer(Modifier.height(spacing.small))
                                    Text(
                                        text = "点击上传全局风格参考底图",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 右侧说明与更换/移除按钮
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Text(
                            text = if (state.referenceImageUri != null) "✅ 已成功绑定全案风格参考底图。大模型在执行任何模块生成时，都将以此图的光影层次、材质纹理与主色调作为先验基准，保证所有组件美术风格统一。"
                            else "💡 建议上传一张最具代表性的主界面设计图或海报。AI 将自动提取其调色板、光影结构与材质风格，确保全案生成的按钮、转轴与界面元素保持高一致性。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                            Button(
                                onClick = { imagePicker() },
                                shape = AppShapes.small,
                                modifier = Modifier.height(38.dp).tip("从本地选择或更换风格参考图"),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("选择/更换参考底图", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            if (state.referenceImageUri != null) {
                                OutlinedButton(
                                    onClick = { viewModel.assetManager.setReferenceImage(null) },
                                    shape = AppShapes.small,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.height(38.dp).tip("移除参考图，后续生图不再强制对齐此风格"),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Text("移除参考", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(spacing.large))

                // 3. 板块二：全局风格关键词与快捷标签
                Text(
                    text = "📝 全局风格提示词 (自动附加于所有模块的生成指令)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(spacing.extraSmall))

                // 热门风格快捷胶囊
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    popularStyleTags.forEach { tag ->
                        SuggestionChip(
                            onClick = {
                                currentStyleText = if (currentStyleText.isBlank()) tag else "$currentStyleText, $tag"
                                viewModel.assetManager.setGlobalStyle(currentStyleText)
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp).tip("点击追加该风格关键词")
                        )
                    }
                }

                SelectAllOutlinedTextField(
                    value = currentStyleText,
                    onValueChange = {
                        currentStyleText = it
                        viewModel.assetManager.setGlobalStyle(it)
                    },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    shape = AppShapes.small,
                    placeholder = {
                        Text(
                            "例如: Cyberpunk style, vibrant glowing neon blue and purple lights, clean 3D render, masterpiece, octane render...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )

                Spacer(Modifier.height(spacing.large))

                // 4. 底部保存与取消操作
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = AppShapes.small) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(spacing.medium))
                    Button(
                        onClick = {
                            viewModel.assetManager.setGlobalStyle(currentStyleText)
                            viewModel.assetManager.saveStyleSettings { onDismiss() }
                        },
                        shape = AppShapes.small,
                        modifier = Modifier.height(40.dp).tip("保存全局风格配置并立即同步项目"),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        Text("保存全局设置", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
