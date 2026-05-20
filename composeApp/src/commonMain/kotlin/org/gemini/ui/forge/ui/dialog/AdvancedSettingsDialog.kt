package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.rememberImagePicker
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel

/**
 * 全局高级设置对话框。
 * 负责管理风格参考图（图生图）以及全项目通用的风格提示词。
 */
@Composable
fun AdvancedSettingsDialog(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    onDismiss: () -> Unit
) {
    val projectName = state.projectName.replace(" ", "_")
    val projectAssetsBase = TemplateFile("templates/$projectName/assets")
    val imagePicker = projectAssetsBase.rememberImagePicker { uris ->
        uris.firstOrNull()?.let { viewModel.assetManager.setReferenceImageExternal(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全局风格与参考设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "风格参考图 (图生图引导)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 参考图预览与选择
                    Box(
                        Modifier.size(80.dp).clip(AppShapes.small).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { imagePicker() }.tip("点击选择本地图片作为 AI 生图的风格参考")
                    ) {
                        if (state.referenceImageUri != null) {
                            AsyncImage(
                                model = state.referenceImageUri.getAbsolutePath(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                null,
                                Modifier.align(Alignment.Center),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column {
                        Button(
                            onClick = { imagePicker() },
                            shape = AppShapes.small,
                            modifier = Modifier.height(32.dp).tip("更换当前的全局参考图"),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("更改参考", style = MaterialTheme.typography.labelSmall) }
                        if (state.referenceImageUri != null) {
                            TextButton(
                                onClick = { viewModel.assetManager.setReferenceImage(null) },
                                modifier = Modifier.tip("移除参考图，AI 将不再受其风格引导")
                            ) {
                                Text(
                                    "移除参考",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Text(
                    "全局风格关键词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                SelectAllOutlinedTextField(
                    value = state.globalStyle,
                    onValueChange = { viewModel.assetManager.setGlobalStyle(it) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = {
                        Text(
                            "例如: Cyberpunk, oil painting...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    })
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.assetManager.saveStyleSettings { onDismiss() } },
                modifier = Modifier.tip("应用当前风格并保存项目配置")
            ) { Text("保存设置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
