package org.gemini.ui.forge.ui.uistudio.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.jetbrains.compose.resources.stringResource

/**
 * AI 聊天生成图片按需保存至素材库弹窗
 *
 * 遵循一文件一 Composable 与设计系统规范，支持选择目标子目录或自定义相对路径，
 * 并提供实时的最终落盘路径预览与保存确认。
 */
/**
 * 递归解析文件夹的相对路径（例如：parent/child）
 */
private fun resolveFolderPath(folderId: String?, allFolders: List<UIAssetFolder>): String {
    if (folderId == null) return ""
    val segments = mutableListOf<String>()
    var current: UIAssetFolder? = allFolders.firstOrNull { it.id == folderId }
    val visited = mutableSetOf<String>()
    while (current != null && visited.add(current.id)) {
        segments.add(0, current.name)
        val parentId = current.parentFolderId
        current = if (parentId != null) allFolders.firstOrNull { it.id == parentId } else null
    }
    return segments.joinToString("/")
}

@Composable
fun SaveChatImageToAssetDialog(
    isOpen: Boolean,
    initialFileName: String,
    folders: List<UIAssetFolder>,
    onDismissRequest: () -> Unit,
    onConfirmSave: (fileName: String, subPath: String) -> Unit = { _, _ -> },
    onConfirmSaveWithOptions: ((fileName: String, subPath: String, removeBgAfterSlice: Boolean) -> Unit)? = null,
    showFileNameField: Boolean = true,
    titleText: String? = null,
    showRemoveBgOption: Boolean = false,
    initialRemoveBgChecked: Boolean = true
) {
    if (!isOpen) return

    val spacing = LocalAppSpacing.current
    var fileName by remember(initialFileName) { mutableStateOf(initialFileName) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var isCustomFolderMode by remember { mutableStateOf(false) }
    var customSubPath by remember { mutableStateOf("") }
    var removeBgAfterSlice by remember(initialRemoveBgChecked) { mutableStateOf(initialRemoveBgChecked) }

    val activeSubPath = remember(isCustomFolderMode, selectedFolderId, customSubPath, folders) {
        if (isCustomFolderMode) {
            customSubPath.trim().trim('/', '\\')
        } else {
            resolveFolderPath(selectedFolderId, folders)
        }
    }

    val previewPath = remember(fileName, activeSubPath, showFileNameField) {
        val safeSub = if (activeSubPath.isBlank()) "" else "$activeSubPath/"
        if (showFileNameField) {
            val cleanName = fileName.trim()
            "assets/image/$safeSub$cleanName"
        } else {
            "assets/image/${safeSub}*.png"
        }
    }

    val canSave = if (showFileNameField) fileName.isNotBlank() else true

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(spacing.dialogConfigWidth)
                .wrapContentHeight()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.large)
            ) {
                // 1. 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = titleText ?: stringResource(Res.string.chat_image_dialog_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                if (showFileNameField) {
                    // 2. 文件名输入
                    Text(
                        text = stringResource(Res.string.chat_image_dialog_filename),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    SelectAllOutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(spacing.medium))
                }

                // 3. 目标目录选择
                Text(
                    text = stringResource(Res.string.chat_image_dialog_folder),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(spacing.extraSmall))

                // 根目录选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (!isCustomFolderMode && selectedFolderId == null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                        )
                        .clickable {
                            isCustomFolderMode = false
                            selectedFolderId = null
                        }
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    RadioButton(
                        selected = !isCustomFolderMode && selectedFolderId == null,
                        onClick = {
                            isCustomFolderMode = false
                            selectedFolderId = null
                        }
                    )
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(Res.string.chat_image_dialog_root_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 已有子文件夹列表（如有）
                folders.forEach { folder ->
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (!isCustomFolderMode && selectedFolderId == folder.id) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                            .clickable {
                                isCustomFolderMode = false
                                selectedFolderId = folder.id
                            }
                            .padding(horizontal = spacing.medium, vertical = spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        RadioButton(
                            selected = !isCustomFolderMode && selectedFolderId == folder.id,
                            onClick = {
                                isCustomFolderMode = false
                                selectedFolderId = folder.id
                            }
                        )
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.extraSmall))

                // 自定义输入子目录
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isCustomFolderMode) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                        )
                        .clickable { isCustomFolderMode = true }
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    RadioButton(
                        selected = isCustomFolderMode,
                        onClick = { isCustomFolderMode = true }
                    )
                    Text(
                        text = stringResource(Res.string.chat_image_dialog_custom_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isCustomFolderMode) {
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    SelectAllOutlinedTextField(
                        value = customSubPath,
                        onValueChange = { customSubPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：icons/shop", style = MaterialTheme.typography.bodySmall) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                // 4. 实时落盘路径预览
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(spacing.small)) {
                        Text(
                            text = stringResource(Res.string.chat_image_dialog_preview_path),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = previewPath,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                if (showRemoveBgOption) {
                    Spacer(modifier = Modifier.height(spacing.medium))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { removeBgAfterSlice = !removeBgAfterSlice }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "切图后执行去除背景",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "切片生成后自动调用本地 AI 抠图算法批量去除背景",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Switch(
                                checked = removeBgAfterSlice,
                                onCheckedChange = { removeBgAfterSlice = it },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))

                // 5. 底部操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(spacing.small))
                    Button(
                        onClick = {
                            if (canSave) {
                                val cleanFileName = if (showFileNameField) fileName.trim() else ""
                                if (onConfirmSaveWithOptions != null) {
                                    onConfirmSaveWithOptions(cleanFileName, activeSubPath, removeBgAfterSlice)
                                } else {
                                    onConfirmSave(cleanFileName, activeSubPath)
                                }
                            }
                        },
                        enabled = canSave,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(Res.string.chat_image_dialog_confirm))
                    }
                }
            }
        }
    }
}
