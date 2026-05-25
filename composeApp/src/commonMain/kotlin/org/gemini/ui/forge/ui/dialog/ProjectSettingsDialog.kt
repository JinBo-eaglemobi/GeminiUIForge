package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.readBytesInternal
import org.gemini.ui.forge.model.ui.ResourceItem
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.rememberFilePicker
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.looseJson
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*


/**
 * 项目设置弹窗，目前主要用于配置并验证导出资源的命名规范 JSON 配置表。
 *
 * @param initialPath 初始配置的资源路径。
 * @param onDismiss 弹窗关闭的回调。
 * @param onConfirm 确认并验证成功后的回调。
 */
@Composable
fun ProjectSettingsDialog(
    initialPath: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pathInput by remember { mutableStateOf(initialPath ?: "") }
    var isValidating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // 预加载多语言文案，避免在 onClick/协程 非 Composable 作用域内调用 stringResource 导致编译错误
    val titleText = stringResource(Res.string.proj_settings_title)
    val pathLabel = stringResource(Res.string.proj_settings_path_label)
    val pathPlaceholder = stringResource(Res.string.proj_settings_path_placeholder)
    val hintText = stringResource(Res.string.proj_settings_path_hint)
    val btnSaveLabel = stringResource(Res.string.proj_settings_btn_save)
    val btnCancelLabel = stringResource(Res.string.dialog_action_cancel)
    val validatingLabel = stringResource(Res.string.proj_settings_validating)

    val errEmpty = stringResource(Res.string.proj_settings_path_empty_err)
    val errRead = stringResource(Res.string.proj_settings_path_read_err)
    
    // 带参数的错误格式模板，在 onClick 中动态格式化替换占位符
    val errParseTemplate = stringResource(Res.string.proj_settings_path_parse_err)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            val filePicker = rememberFilePicker(
                title = "选择资源命名规范 JSON 配置文件",
                extensions = listOf("json")
            ) { selectedPath ->
                if (selectedPath != null) {
                    pathInput = selectedPath
                    errorMessage = null
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = pathLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pathInput,
                    onValueChange = {
                        pathInput = it
                        errorMessage = null
                    },
                    placeholder = { Text(pathPlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium,
                    singleLine = true,
                    isError = errorMessage != null,
                    trailingIcon = {
                        IconButton(
                            onClick = filePicker,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "选择配置文件",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )

                if (errorMessage != null) {
                    SelectionContainer {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        text = hintText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pathInput.isBlank()) {
                        errorMessage = errEmpty
                        return@Button
                    }
                    isValidating = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            // 调用底层跨平台文件读取方法
                            val bytes = readBytesInternal(pathInput)
                            if (bytes == null) {
                                errorMessage = errRead
                                isValidating = false
                                return@launch
                            }
                            val content = bytes.decodeToString()
                            // 校验 JSON 格式（使用支持尾部逗号的宽容解析器）
                            looseJson.decodeFromString<Map<String, Map<String, ResourceItem>>>(content)
                            
                            // 校验通过
                            onConfirm(pathInput)
                        } catch (e: Exception) {
                            val detail = e.stackTraceToString()
                            errorMessage = errParseTemplate.replace($$"%1$s", detail).replace("%s", detail)
                        } finally {
                            isValidating = false
                        }
                    }
                },
                enabled = !isValidating,
                shape = AppShapes.medium
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(validatingLabel)
                } else {
                    Text(btnSaveLabel)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isValidating,
                shape = AppShapes.medium
            ) {
                Text(btnCancelLabel)
            }
        }
    )
}
