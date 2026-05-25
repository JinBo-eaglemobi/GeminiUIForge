package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.app.CompileConfig
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.rememberFilePicker
import org.jetbrains.compose.resources.stringResource

/**
 * 编译环境配置对话框。
 * 允许用户设置运行环境根目录、编译脚本路径以及资源保存目录。
 */
@Composable
fun CompileConfigDialog(
    initialConfig: CompileConfig,
    onDismiss: () -> Unit,
    onConfirm: (CompileConfig) -> Unit
) {
    var rootDir by remember { mutableStateOf(initialConfig.rootDir) }
    var envDir by remember { mutableStateOf(initialConfig.envDir) }
    var scriptPath by remember { mutableStateOf(initialConfig.scriptPath) }
    var outputDir by remember { mutableStateOf(initialConfig.outputDir) }

    val pickRootDir = rememberFilePicker(stringResource(Res.string.compile_pick_dir), isFolder = true) {
        if (it != null) rootDir = it
    }
    val pickEnvDir = rememberFilePicker(stringResource(Res.string.compile_pick_dir), isFolder = true) {
        if (it != null) envDir = it
    }
    val pickScript = rememberFilePicker(stringResource(Res.string.compile_pick_file), isFolder = false, extensions = listOf("js")) {
        if (it != null) scriptPath = it
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.compile_config_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 运行环境根目录
                SelectAllOutlinedTextField(
                    value = rootDir,
                    onValueChange = { rootDir = it },
                    label = { Text(stringResource(Res.string.compile_env_root)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium,
                    trailingIcon = {
                        IconButton(
                            onClick = pickRootDir,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                        }
                    }
                )

                // 运行环境目录
                SelectAllOutlinedTextField(
                    value = envDir,
                    onValueChange = { envDir = it },
                    label = { Text(stringResource(Res.string.compile_env_dir)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium,
                    trailingIcon = {
                        IconButton(
                            onClick = pickEnvDir,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                        }
                    }
                )

                // 编译脚本路径
                SelectAllOutlinedTextField(
                    value = scriptPath,
                    onValueChange = { scriptPath = it },
                    label = { Text(stringResource(Res.string.compile_script_path)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium,
                    trailingIcon = {
                        IconButton(
                            onClick = pickScript,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
                        }
                    }
                )

                // 资源保存目录
                SelectAllOutlinedTextField(
                    value = outputDir,
                    onValueChange = { outputDir = it },
                    label = { Text(stringResource(Res.string.compile_output_dir)) },
                    placeholder = { Text(stringResource(Res.string.compile_output_dir_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(CompileConfig(rootDir, scriptPath, outputDir, envDir))
                },
                shape = AppShapes.medium
            ) {
                Text(stringResource(Res.string.compile_action_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = AppShapes.medium) {
                Text(stringResource(Res.string.dialog_action_cancel))
            }
        }
    )
}
