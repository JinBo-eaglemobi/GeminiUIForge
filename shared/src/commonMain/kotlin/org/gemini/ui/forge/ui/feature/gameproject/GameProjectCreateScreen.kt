package org.gemini.ui.forge.ui.feature.gameproject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.gemini.ui.forge.extend.copyOnClick
import org.gemini.ui.forge.extend.rememberClipboardAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.gameproject.CredentialStorageKind
import org.gemini.ui.forge.model.gameproject.GameLanguage
import org.gemini.ui.forge.model.gameproject.GitCredentialInfo
import org.gemini.ui.forge.model.gameproject.GitCredentialMode
import org.gemini.ui.forge.model.gameproject.GitCredentialSource
import org.gemini.ui.forge.model.gameproject.PackageManagerMode
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.feature.gameproject.create.component.*
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.service.CloneDirChoice
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.rememberFilePicker
import org.gemini.ui.forge.viewmodel.GameProjectViewModel

/** Node.js 推荐安装命令（Windows winget，可直接复制执行） */
private const val NODE_INSTALL_COMMAND = "winget install OpenJS.NodeJS.LTS"

/** Node.js 官网地址 */
private const val NODE_WEBSITE = "https://nodejs.org"

/** pnpm 推荐安装命令 */
private const val PNPM_INSTALL_COMMAND = "npm install -g pnpm"

/** pnpm 官网地址 */
private const val PNPM_WEBSITE = "https://pnpm.io"

/**
 * 游戏项目管理项目创建向导界面。
 * 左侧为项目信息表单（名字 / 仓库 / 凭据 / 保存地址 / 语言 / 包管理模式），
 * 右侧为实时环境检测面板（node / npm / pnpm 及配置信息）。
 * Node.js 未安装时为硬性阻断：创建按钮禁用，仅提供安装指引与重新检测。
 */
@Composable
fun GameProjectCreateScreen(
    viewModel: GameProjectViewModel,
    appViewModel: AppViewModel
) {
    val form = viewModel.form
    val spacing = LocalAppSpacing.current

    // 仓库地址与当前凭据模式的匹配校验（地址为空仅视为待填写，不算格式错误）
    val repoUrlInvalid = form.repoUrl.isNotBlank() && !viewModel.isRepoUrlValid()

    // 自动填名：上一次由仓库地址推导出的项目名（用于判断名字是否仍处于自动跟随态）
    var lastDerivedName by remember { mutableStateOf<String?>(null) }

    // 项目保存目录选择器：唤起系统目录选择框，选定后回填到保存地址输入框
    val saveDirPicker = rememberFilePicker(
        title = stringResource(Res.string.settings_storage_dir_title),
        isFolder = true,
        onResult = { path ->
            if (path != null) {
                viewModel.updateForm { it.copy(savePath = path) }
            }
        }
    )

    // 保存目录实时预览：项目名非空时解析最终完整路径（与克隆落盘目录严格同源）
    var savePathPreview by remember { mutableStateOf("") }
    LaunchedEffect(form.savePath, form.name) {
        savePathPreview = if (form.name.isNotBlank()) viewModel.resolveSaveDir() else ""
    }

    // 复制成功提示（预览路径一键复制用）
    val savePathCopiedTip = stringResource(Res.string.gp_save_path_copied)

    Row(
        modifier = Modifier.fillMaxSize().padding(spacing.large)
    ) {
        // 左侧：表单区
        Column(
            modifier = Modifier.weight(2f).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(Res.string.gp_form_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(spacing.medium))

            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> viewModel.updateForm { it.copy(name = value) } },
                label = { Text(stringResource(Res.string.gp_form_name)) },
                placeholder = { Text(stringResource(Res.string.gp_form_name_placeholder)) },
                singleLine = true,
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(spacing.medium))

            OutlinedTextField(
                value = form.repoUrl,
                onValueChange = { value ->
                    viewModel.updateForm { it.copy(repoUrl = value) }
                    // 智能填名：项目名字为空、或仍为上一次自动推导值（用户未手动修改）时，
                    // 跟随仓库地址末段实时更新；用户手动输入名字后即停止自动跟随
                    val derived = deriveProjectName(value)
                    if (derived != null && (form.name.isBlank() || form.name == lastDerivedName)) {
                        viewModel.updateForm { it.copy(name = derived) }
                        lastDerivedName = derived
                    }
                },
                label = { Text(stringResource(Res.string.gp_form_repo)) },
                placeholder = {
                    // 地址示例按凭据模式动态切换
                    Text(
                        stringResource(
                            if (form.credentialMode == GitCredentialMode.SSH_KEY) {
                                Res.string.gp_form_repo_placeholder_ssh
                            } else {
                                Res.string.gp_form_repo_placeholder
                            }
                        )
                    )
                },
                supportingText = {
                    // 非匹配时红字报错，否则展示当前模式推荐的地址格式
                    Text(
                        text = stringResource(
                            when {
                                repoUrlInvalid -> Res.string.gp_form_repo_invalid
                                form.credentialMode == GitCredentialMode.SSH_KEY -> Res.string.gp_form_repo_hint_ssh
                                else -> Res.string.gp_form_repo_hint_token
                            }
                        ),
                        color = if (repoUrlInvalid) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                isError = repoUrlInvalid,
                singleLine = true,
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(spacing.medium))

            // Git 凭据区
            GitCredentialSection(viewModel)
            Spacer(Modifier.height(spacing.medium))

            OutlinedTextField(
                value = form.savePath,
                onValueChange = { value -> viewModel.updateForm { it.copy(savePath = value) } },
                label = { Text(stringResource(Res.string.gp_form_save_path)) },
                // 目录选择按钮：唤起系统目录选择框直接选定保存地址
                trailingIcon = {
                    IconButton(
                        onClick = saveDirPicker,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = stringResource(Res.string.gp_form_save_path_pick),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                supportingText = {
                    // 实时预览最终保存路径（始终包含项目名字）；项目名未填时保留默认提示
                    if (savePathPreview.isBlank()) {
                        Text(stringResource(Res.string.gp_form_save_path_hint))
                    } else {
                        // 预览路径整体可点击：一键复制完整路径（复制的是完整值而非省略后的显示文本）并弹出提示
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .copyOnClick(savePathPreview, savePathCopiedTip)
                                .pointerHoverIcon(PointerIcon.Hand),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                        ) {
                            Text(
                                text = stringResource(Res.string.gp_form_save_path_preview, savePathPreview),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(Res.string.gp_form_save_path_copy),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(spacing.medium))

            // 游戏语言选择（当前仅 TypeScript，结构预留扩展）
            Text(
                text = stringResource(Res.string.gp_form_language),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                FilterChip(
                    selected = form.language == GameLanguage.TYPESCRIPT,
                    onClick = { viewModel.updateForm { it.copy(language = GameLanguage.TYPESCRIPT) } },
                    label = { Text(stringResource(Res.string.gp_lang_typescript)) }
                )
            }
            Spacer(Modifier.height(spacing.medium))

            // 包管理模式选择（变化后立即触发环境检测）
            Text(
                text = stringResource(Res.string.gp_form_pm),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                FilterChip(
                    selected = form.packageManager == PackageManagerMode.NPM,
                    onClick = { viewModel.updateForm { it.copy(packageManager = PackageManagerMode.NPM) } },
                    label = { Text(stringResource(Res.string.gp_pm_npm)) }
                )
                FilterChip(
                    selected = form.packageManager == PackageManagerMode.PNPM,
                    onClick = { viewModel.updateForm { it.copy(packageManager = PackageManagerMode.PNPM) } },
                    label = { Text(stringResource(Res.string.gp_pm_pnpm)) }
                )
            }
            Spacer(Modifier.height(spacing.large))

            // 创建按钮（node 未安装等硬阻断时禁用）
            Button(
                onClick = { viewModel.startCreate() },
                enabled = viewModel.canCreate(),
                shape = AppShapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.extraSmall))
                Text(stringResource(Res.string.gp_btn_create))
            }
            if (!viewModel.canCreate()) {
                Spacer(Modifier.height(spacing.extraSmall))
                Text(
                    text = stringResource(Res.string.gp_btn_create_disabled),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.width(spacing.large))

        // 右侧：环境检测面板
        EnvironmentCheckPanel(viewModel)
    }

    // 目录冲突确认弹窗：目标目录已存在时选择覆盖重建 / 继续使用 / 取消
    viewModel.pendingExistingDir?.let { dir ->
        ExistingDirConfirmDialog(
            dirPath = dir,
            onOverwrite = { viewModel.confirmExistingDir(CloneDirChoice.OVERWRITE) },
            onReuse = { viewModel.confirmExistingDir(CloneDirChoice.REUSE) },
            onCancel = { viewModel.cancelExistingDirChoice() }
        )
    }

    // 克隆流程弹窗：完成后跳转进入项目专属工作台
    GameProjectCloneDialog(
        viewModel = viewModel,
        onEnterWorkspace = { appViewModel.navigateTo(AppScreen.GAME_PROJECT_WORKSPACE) }
    )
}

/**
 * 从 git 仓库地址推导项目名：取地址末段路径并去除 .git 与尾部斜杠。
 * 兼容 https 与 SSH（含无子路径的 git@host:repo.git 形式）。
 * @return 可用作项目名的非空片段；地址为空或无法推导时返回 null
 */
private fun deriveProjectName(repoUrl: String): String? {
    if (repoUrl.isBlank()) return null
    val lastSegment = repoUrl.trim()
        .removeSuffix("/")
        .removeSuffix(".git")
        .substringAfterLast('/')
        .substringAfterLast(':')
    return lastSegment.takeIf { it.isNotBlank() }
}
