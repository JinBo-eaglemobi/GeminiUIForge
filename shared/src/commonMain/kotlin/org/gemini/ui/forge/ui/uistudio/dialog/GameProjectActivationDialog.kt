package org.gemini.ui.forge.ui.uistudio.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.model.gameproject.GameProjectInspection
import org.gemini.ui.forge.model.gameproject.GameProjectKind
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.component.dialog.AppDraggableAlertDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.utils.rememberFilePicker
import org.jetbrains.compose.resources.stringResource

/**
 * UI Studio 首次运行游戏预览激活向导弹窗组件。
 * 遵循一文件一 Composable 铁律，提供项目名称设定、独立外部物理保存路径校验（拦截私有缓存目录）、
 * 实时环境检测/静默安装、源码与打包流水线调度进度条与终端流式日志输出。
 * 支持文本自由划词框选复制、一键复制全部日志、以及 960dp 巨幕全屏查看模式。
 */
@Composable
fun GameProjectActivationDialog(
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    val currentProject = state.project
    val initialProjectName = currentProject?.projectId ?: "NewGameProject"

    var inputProjectName: String by remember { mutableStateOf(initialProjectName) }
    var inputParentPath: String by remember {
        val existingPath = currentProject?.gameProjectPath?.replace('\\', '/')
        val defaultParent = if (!existingPath.isNullOrBlank()) {
            val parent = existingPath.substringBeforeLast('/', "")
            if (parent.isNotBlank()) parent else existingPath
        } else {
            ""
        }
        mutableStateOf(defaultParent)
    }

    var isConsoleFullscreen by remember { mutableStateOf(false) }
    val copyToClipboard = rememberClipboardAction()

    val finalProjectDirectory = remember(inputParentPath, inputProjectName) {
        val cleanParent = inputParentPath.trim().replace('\\', '/').trimEnd('/')
        val cleanName = inputProjectName.trim()
        if (cleanParent.isNotBlank() && cleanName.isNotBlank()) {
            "$cleanParent/$cleanName"
        } else if (cleanParent.isNotBlank()) {
            cleanParent
        } else {
            ""
        }
    }

    val pathErrorMsg = remember(inputParentPath, inputProjectName, finalProjectDirectory) {
        if (inputParentPath.isBlank()) {
            "请选择或填写游戏保存父目录"
        } else if (inputProjectName.isBlank()) {
            "游戏项目名称不能为空"
        } else {
            viewModel.scaffoldBridge.validateGamePath(finalProjectDirectory)
        }
    }
    val isPathValid = pathErrorMsg == null

    // 智能探测当前路径下是否已有成熟工程或源码
    val inspection = remember(finalProjectDirectory, isPathValid) {
        if (isPathValid && finalProjectDirectory.isNotBlank()) {
            viewModel.scaffoldBridge.inspectGameProject(finalProjectDirectory)
        } else {
            null
        }
    }

    // 若检测到已有成熟工程的 package.json 中有工程名，且用户未修改过初始名称，智能同步填入
    LaunchedEffect(inspection?.detectedName) {
        val detected = inspection?.detectedName
        if (!detected.isNullOrBlank() && inputProjectName == initialProjectName) {
            inputProjectName = detected
        }
    }

    val isActivating = state.isGameActivating
    val canConfirm = isPathValid && inputProjectName.isNotBlank() && !isActivating

    val logListState = rememberLazyListState()

    // 系统目录选择器：唤起原生目录选择对话框
    val dirPicker = rememberFilePicker(
        title = stringResource(Res.string.gp_form_save_path_pick),
        isFolder = true,
        onResult = { selectedPath ->
            if (!selectedPath.isNullOrBlank()) {
                inputParentPath = selectedPath.replace('\\', '/').trimEnd('/')
            }
        }
    )

    LaunchedEffect(state.gameActivationLogs.size) {
        if (state.gameActivationLogs.isNotEmpty()) {
            logListState.animateScrollToItem(state.gameActivationLogs.size - 1)
        }
    }

    val dialogWidth = if (isConsoleFullscreen) spacing.dialogLargeWidth else spacing.dialogMediumWidth

    AppDraggableAlertDialog(
        onDismissRequest = {
            if (!isActivating) {
                viewModel.dismissGameActivationDialog()
            }
        },
        modifier = modifier.width(dialogWidth),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(Res.string.game_activation_dialog_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 全屏/退出全屏快捷手柄
                AppIconButton(
                    imageVector = if (isConsoleFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    onClick = { isConsoleFullscreen = !isConsoleFullscreen },
                    tier = AppIconButtonTier.STANDARD,
                    tooltip = if (isConsoleFullscreen) stringResource(Res.string.game_activation_console_exit_fullscreen)
                              else stringResource(Res.string.game_activation_console_fullscreen)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                // 非全屏模式下展示完整配置表单
                if (!isConsoleFullscreen) {
                    // 顶部说明提示条
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(Res.string.game_activation_dialog_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // 项目名称输入
                    OutlinedTextField(
                        value = inputProjectName,
                        onValueChange = { inputProjectName = it },
                        label = { Text(stringResource(Res.string.game_activation_field_project_name)) },
                        singleLine = true,
                        enabled = !isActivating,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 保存父目录输入与警告
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        OutlinedTextField(
                            value = inputParentPath,
                            onValueChange = { inputParentPath = it },
                            label = { Text(stringResource(Res.string.game_activation_field_project_path)) },
                            placeholder = { Text("请选择或输入项目保存父目录") },
                            singleLine = true,
                            isError = !isPathValid,
                            enabled = !isActivating,
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                                    modifier = Modifier.padding(end = spacing.extraSmall)
                                ) {
                                    if (inputParentPath.isNotEmpty() && !isActivating) {
                                        AppIconButton(
                                            imageVector = Icons.Default.Clear,
                                            onClick = { inputParentPath = "" },
                                            tier = AppIconButtonTier.STANDARD,
                                            tooltip = "清空路径"
                                        )
                                    }
                                    AppIconButton(
                                        imageVector = Icons.Default.FolderOpen,
                                        onClick = dirPicker,
                                        tier = AppIconButtonTier.STANDARD,
                                        tooltip = stringResource(Res.string.gp_form_save_path_pick),
                                        enabled = !isActivating
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 最终存放目录高亮展示卡片
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (!isPathValid) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.medium, vertical = spacing.small),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (!isPathValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(Res.string.game_activation_final_directory),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (finalProjectDirectory.isNotBlank()) finalProjectDirectory else "—",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = if (!isPathValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 智能工程特征检测结果卡片展示
                        if (inspection != null && !isConsoleFullscreen) {
                            when (inspection.kind) {
                                GameProjectKind.COMPLETE_PROJECT -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1B3B2B),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(spacing.small),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VerifiedUser,
                                                contentDescription = null,
                                                tint = Color(0xFF81C784),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(Res.string.game_activation_kind_complete),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFFE8F5E9)
                                                )
                                                Text(
                                                    text = inspection.summaryText,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                                    color = Color(0xFFA5D6A7)
                                                )
                                            }
                                        }
                                    }
                                }
                                GameProjectKind.PARTIAL_SOURCE_PROJECT -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1B2E4A),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(spacing.small),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Inventory2,
                                                contentDescription = null,
                                                tint = Color(0xFF64B5F6),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(Res.string.game_activation_kind_source),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFFE3F2FD)
                                                )
                                                Text(
                                                    text = inspection.summaryText,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                                    color = Color(0xFF90CAF9)
                                                )
                                            }
                                        }
                                    }
                                }
                                GameProjectKind.EMPTY_OR_NEW -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(spacing.small),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(Res.string.game_activation_kind_new),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = inspection.summaryText,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                GameProjectKind.ALREADY_ACTIVATED -> {}
                            }
                        }

                        if (!isPathValid && !pathErrorMsg.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = pathErrorMsg,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                } else {
                    // 全屏模式下展示紧凑单行状态摘要
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium, vertical = spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "项目: $inputProjectName  |  目录: $finalProjectDirectory",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { isConsoleFullscreen = false },
                                contentPadding = PaddingValues(horizontal = spacing.small, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text(
                                    text = stringResource(Res.string.game_activation_console_exit_fullscreen),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // 激活流水线进度与日志展示区域
                AnimatedVisibility(visible = isActivating || state.gameActivationLogs.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        // 步骤指示、当前消息与右上角日志操作栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isActivating) "进度 [${state.gameActivationStep}/${state.gameActivationTotalSteps}]" else "构建完成",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = state.gameActivationMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 控制台操作工具栏（复制全部日志 + 全屏切换）
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                            ) {
                                if (state.gameActivationLogs.isNotEmpty()) {
                                    val copySuccessMsg = stringResource(Res.string.game_activation_console_copy_success)
                                    AppIconButton(
                                        imageVector = Icons.Default.ContentCopy,
                                        onClick = {
                                            val allLogs = state.gameActivationLogs.joinToString("\n")
                                            copyToClipboard(
                                                allLogs,
                                                copySuccessMsg
                                            )
                                        },
                                        tier = AppIconButtonTier.COMPACT,
                                        tooltip = stringResource(Res.string.game_activation_console_copy_all)
                                    )
                                }
                                AppIconButton(
                                    imageVector = if (isConsoleFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    onClick = { isConsoleFullscreen = !isConsoleFullscreen },
                                    tier = AppIconButtonTier.COMPACT,
                                    tooltip = if (isConsoleFullscreen) stringResource(Res.string.game_activation_console_exit_fullscreen)
                                              else stringResource(Res.string.game_activation_console_fullscreen)
                                )
                            }
                        }

                        if (isActivating) {
                            LinearProgressIndicator(
                                progress = { (state.gameActivationStep.toFloat() / state.gameActivationTotalSteps.toFloat()).coerceIn(0.05f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // 终端日志控制台（常规 240dp，全屏模式 480dp）
                        val consoleHeight = if (isConsoleFullscreen) 480.dp else 240.dp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(consoleHeight)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E1E))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        ) {
                            // 自由划词选择容器，支持鼠标框选与 Ctrl+C 复制
                            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = logListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = spacing.small, vertical = 6.dp)
                                        .padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(state.gameActivationLogs) { logLine ->
                                        Text(
                                            text = logLine,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            ),
                                            color = if (logLine.contains("ERR") || logLine.contains("失败")) Color(0xFFFF5252)
                                            else if (logLine.contains("WARN")) Color(0xFFFFD54F)
                                            else Color(0xFFCCCCCC)
                                        )
                                    }
                                }
                            }

                            VerticalScrollbarAdapter(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                                scrollState = logListState
                            )
                        }
                    }
                }

                // 错误信息展示
                if (!state.gameActivationError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = state.gameActivationError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isCompleteProject = inspection?.kind == GameProjectKind.COMPLETE_PROJECT
            val isSourceProject = inspection?.kind == GameProjectKind.PARTIAL_SOURCE_PROJECT

            Button(
                onClick = {
                    if (isCompleteProject) {
                        viewModel.bindExistingGameProject(
                            repo = templateRepo,
                            gameProjectName = inputProjectName.trim(),
                            targetDir = finalProjectDirectory
                        )
                    } else {
                        viewModel.activateGameProject(
                            repo = templateRepo,
                            gameProjectName = inputProjectName.trim(),
                            targetDir = finalProjectDirectory
                        )
                    }
                },
                enabled = canConfirm,
                colors = if (isCompleteProject) {
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                if (isActivating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(spacing.small))
                    Text(stringResource(Res.string.game_activation_step_installing_deps))
                } else if (isCompleteProject) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(spacing.extraSmall))
                    Text(stringResource(Res.string.game_activation_btn_bind_existing))
                } else if (isSourceProject) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(spacing.extraSmall))
                    Text(stringResource(Res.string.game_activation_btn_build_existing))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(spacing.extraSmall))
                    Text(stringResource(Res.string.game_activation_btn_confirm))
                }
            }
        },
        dismissButton = {
            if (!isActivating) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!currentProject?.gameProjectPath.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { viewModel.clearGameProjectPath(templateRepo) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("清除配置")
                        }
                    }
                    OutlinedButton(onClick = { viewModel.dismissGameActivationDialog() }) {
                        Text(stringResource(Res.string.game_activation_btn_cancel))
                    }
                }
            }
        }
    )
}
