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
            CredentialSection(viewModel)
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
        EnvironmentPanel(viewModel)
    }

    // 目录冲突确认弹窗：目标目录已存在时选择覆盖重建 / 继续使用 / 取消
    viewModel.pendingExistingDir?.let { dir ->
        ExistingDirDialog(
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
 * Git 凭据区（私有组件）。
 * 输入框与下拉框一体化组合：输入框最右侧为下拉按钮（检测到本机凭据时显示），
 * 下拉选中本机凭据后自动填入并锁定为只读；输入框有内容时提供一键清除按钮。
 * 不再单独提供 Git 账户名输入（令牌模式下用户名由服务层默认 oauth2）。
 */
@Composable
private fun CredentialSection(viewModel: GameProjectViewModel) {
    val form = viewModel.form
    val credentials = viewModel.localCredentials
    val spacing = LocalAppSpacing.current

    // 预先解析每条本机凭据的可读描述：来源文案 + 可选的用户与主机（多凭据时用于区分条目）
    val credentialLabels = credentials.associateWith { cred ->
        val sourceLabel = when (cred.source) {
            GitCredentialSource.GLOBAL_CONFIG -> stringResource(Res.string.gp_cred_source_global)
            GitCredentialSource.GIT_CREDENTIALS_FILE -> stringResource(Res.string.gp_cred_source_file)
            GitCredentialSource.SSH_KEY -> stringResource(Res.string.gp_cred_source_ssh)
            GitCredentialSource.MANUAL -> stringResource(Res.string.gp_cred_source_manual)
        }
        val user = cred.userName
        val host = cred.repoHostHint
        when {
            !user.isNullOrBlank() && !host.isNullOrBlank() -> stringResource(
                Res.string.gp_cred_entry, sourceLabel,
                stringResource(Res.string.gp_cred_entry_host_user, user, host)
            )
            !user.isNullOrBlank() -> stringResource(Res.string.gp_cred_entry, sourceLabel, user)
            !host.isNullOrBlank() -> stringResource(Res.string.gp_cred_entry, sourceLabel, host)
            else -> sourceLabel
        }
    }

    // 当前生效的本机凭据：优先用户选中项，未选中时回退列表首项
    val effectiveCredential = viewModel.selectedCredential ?: credentials.firstOrNull()

    // 显示值按模式分流：SSH 模式展示私钥文件路径；令牌模式展示完整 Key（无令牌来源回退描述文案）
    val displayValue = when (form.credentialMode) {
        GitCredentialMode.SSH_KEY -> form.sshKeyPath
        GitCredentialMode.GIT_TOKEN -> when {
            !form.useGlobalCredential -> form.gitToken
            effectiveCredential == null -> ""
            !effectiveCredential.token.isNullOrBlank() -> effectiveCredential.token
            else -> credentialLabels[effectiveCredential] ?: ""
        }
    }

    // SSH 模式下的本机私钥候选列表（下拉选择用）
    val sshCredentials = remember(credentials) { credentials.filter { it.source == GitCredentialSource.SSH_KEY } }

    // SSH 私钥文件选择器：唤起系统文件选择框，选定后自动切换到 SSH 模式并回填完整路径
    val sshKeyPicker = rememberFilePicker(
        title = stringResource(Res.string.gp_cred_ssh_pick),
        isFolder = false,
        extensions = emptyList(),
        onResult = { path ->
            if (path != null) {
                viewModel.updateForm { it.copy(credentialMode = GitCredentialMode.SSH_KEY, sshKeyPath = path) }
            }
        }
    )

    Card(shape = AppShapes.medium) {
        Column(modifier = Modifier.padding(spacing.medium).fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.gp_form_cred_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(spacing.small))

            // 双模式切换：SSH 密钥（默认优先）/ 令牌
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = form.credentialMode == GitCredentialMode.SSH_KEY,
                    onClick = { viewModel.updateForm { it.copy(credentialMode = GitCredentialMode.SSH_KEY) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(Res.string.gp_cred_mode_ssh))
                }
                SegmentedButton(
                    selected = form.credentialMode == GitCredentialMode.GIT_TOKEN,
                    onClick = { viewModel.updateForm { it.copy(credentialMode = GitCredentialMode.GIT_TOKEN) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(Res.string.gp_cred_mode_token))
                }
            }
            Spacer(Modifier.height(spacing.small))

            when (form.credentialMode) {
                GitCredentialMode.GIT_TOKEN -> {
                    // 令牌模式：手动输入或从本机凭据列表选择，均可留空（公开仓库 / 交由 git 助手认证）
                    DropdownInputField(
                        value = displayValue,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(gitToken = value, useGlobalCredential = false) }
                        },
                        items = credentials,
                        onItemSelected = { cred ->
                            viewModel.selectCredential(cred)
                            viewModel.updateForm { it.copy(useGlobalCredential = true) }
                        },
                        itemLabel = { credentialLabels[it] ?: "" },
                        isSelected = { form.useGlobalCredential && it == effectiveCredential },
                        label = { Text(stringResource(Res.string.gp_form_git_token)) },
                        readOnly = form.useGlobalCredential,
                        visualTransformation = if (form.useGlobalCredential) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        shape = AppShapes.small,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                GitCredentialMode.SSH_KEY -> {
                    // SSH 模式：多行输入框展示私钥完整路径，可经系统文件选择器选定、下拉切换或手动粘贴
                    DropdownInputField(
                        value = displayValue,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(sshKeyPath = value.trim()) }
                        },
                        items = sshCredentials,
                        onItemSelected = { cred ->
                            viewModel.selectCredential(cred)
                        },
                        itemLabel = { credentialLabels[it] ?: "" },
                        isSelected = { it.keyFilePath == form.sshKeyPath },
                        label = { Text(stringResource(Res.string.gp_cred_ssh_label)) },
                        shape = AppShapes.small,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(spacing.extraSmall))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { sshKeyPicker() },
                            shape = AppShapes.small
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text(stringResource(Res.string.gp_cred_ssh_pick))
                        }
                        Spacer(Modifier.width(spacing.small))
                        Text(
                            text = stringResource(Res.string.gp_cred_ssh_empty_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(spacing.small))

            // 连接测试（本机凭据或已输入令牌即可测试）；
            // 选中的本机凭据带有可定位存储位置时，按钮旁提供一键定位入口并展示位置描述
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.testConnection() },
                    enabled = viewModel.isRepoUrlValid() && !viewModel.testing,
                    shape = AppShapes.medium
                ) {
                    if (viewModel.testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(spacing.extraSmall))
                    }
                    Text(stringResource(Res.string.gp_btn_test))
                }
                Spacer(Modifier.width(spacing.small))
                // 定位入口条件：SSH 模式下已指定私钥文件，或令牌模式选中带存储位置的本机凭据
                val locatedCredential = effectiveCredential?.takeIf {
                    (form.credentialMode == GitCredentialMode.SSH_KEY && !it.keyFilePath.isNullOrBlank()) ||
                            (form.useGlobalCredential && it.storageKind != CredentialStorageKind.NONE)
                }
                if (locatedCredential != null) {
                    // 回调闭包无法继承外层 smart cast，此处固化一份非空局部引用供 onClick 使用
                    val locateTarget = locatedCredential
                    IconButton(
                        onClick = {
                            when (locateTarget.storageKind) {
                                // 文件型凭据：资源管理器高亮定位到凭据文件
                                CredentialStorageKind.FILE -> getPlatform().openInFileExplorer(locateTarget.storageLocation.orEmpty())
                                // 系统凭据库型凭据：打开系统的凭据管理界面
                                CredentialStorageKind.SYSTEM_STORE -> getPlatform().openCredentialStore()
                                CredentialStorageKind.NONE -> Unit
                            }
                        },
                        modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = stringResource(Res.string.gp_cred_open_location),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(spacing.small))
                Column(modifier = Modifier.weight(1f)) {
                    // 展示凭据数据的实际存放位置（文件路径或系统凭据库条目标识）
                    if (locatedCredential != null) {
                        val locationText = when (locatedCredential.storageKind) {
                            CredentialStorageKind.SYSTEM_STORE -> stringResource(
                                Res.string.gp_cred_storage_system,
                                locatedCredential.storageLocation.orEmpty()
                            )
                            else -> locatedCredential.storageLocation.orEmpty()
                        }
                        Text(
                            text = locationText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    when (viewModel.testSuccess) {
                        true -> Text(
                            text = stringResource(Res.string.gp_test_success),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        false -> Text(
                            text = viewModel.testMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        null -> Unit
                    }
                    // 测试按钮禁用时显式告知具体缺少哪一项前置条件
                    if (viewModel.testSuccess == null && !viewModel.testing) {
                        when {
                            form.repoUrl.isBlank() -> Text(
                                text = stringResource(Res.string.gp_test_need_repo),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            !form.useGlobalCredential && form.gitToken.isBlank() -> Text(
                                text = stringResource(Res.string.gp_test_need_token),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 环境检测面板（私有组件）。
 * node 未安装时显示硬性阻断警示与安装指引（命令可复制 / 官网跳转）；
 * pnpm 缺失时提示安装方法；提供手动重新检测按钮。
 */
@Composable
private fun EnvironmentPanel(viewModel: GameProjectViewModel) {
    val env = viewModel.envStatus
    val spacing = LocalAppSpacing.current
    val copyAction = rememberClipboardAction()
    // 预先在 Composable 上下文中解析文案，供非 Composable 的复制回调使用
    val copiedTip = stringResource(Res.string.gp_env_copied)

    /** 复制命令到剪贴板并弹出提示 */
    fun copyCommand(command: String) {
        copyAction(command, copiedTip)
    }

    Column(
        modifier = Modifier.width(340.dp).fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.gp_env_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.checkEnv() }, enabled = !viewModel.envChecking) {
                if (viewModel.envChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.gp_env_refresh))
                }
            }
        }
        Spacer(Modifier.height(spacing.small))

        // node 未安装：硬性阻断警示
        if (env.isHardBlocked) {
            Card(shape = AppShapes.small) {
                Column(modifier = Modifier.padding(spacing.small)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(spacing.extraSmall))
                        Text(
                            text = stringResource(Res.string.gp_env_node_blocked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(spacing.small))
                    Text(
                        text = stringResource(Res.string.gp_env_install_cmd),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(spacing.extraSmall))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = NODE_INSTALL_COMMAND,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { copyCommand(NODE_INSTALL_COMMAND) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(Res.string.gp_env_copy), modifier = Modifier.size(16.dp))
                        }
                    }
                    TextButton(
                        onClick = { getPlatform().openInBrowser(NODE_WEBSITE) },
                        shape = AppShapes.small,
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Text(stringResource(Res.string.gp_env_website) + ": $NODE_WEBSITE")
                    }
                }
            }
            Spacer(Modifier.height(spacing.medium))
        }

        // 环境条目列表
        EnvItemRow(stringResource(Res.string.gp_env_node), env.nodeVersion, env.nodeInstalled)
        EnvItemRow(stringResource(Res.string.gp_env_npm), env.npmVersion, env.npmInstalled)
        env.npmRegistry?.let {
            Text(
                text = stringResource(Res.string.gp_env_registry, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        env.npmPrefix?.let {
            Text(
                text = stringResource(Res.string.gp_env_prefix, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        EnvItemRow(stringResource(Res.string.gp_env_pnpm), env.pnpmVersion, env.pnpmInstalled)

        // pnpm 缺失提示（必须存在 pnpm 环境）
        if (env.nodeInstalled && !env.pnpmInstalled) {
            Spacer(Modifier.height(spacing.small))
            Card(shape = AppShapes.small) {
                Column(modifier = Modifier.padding(spacing.small)) {
                    Text(
                        text = stringResource(Res.string.gp_env_pnpm_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(spacing.extraSmall))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = PNPM_INSTALL_COMMAND,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { copyCommand(PNPM_INSTALL_COMMAND) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(Res.string.gp_env_copy), modifier = Modifier.size(16.dp))
                        }
                    }
                    TextButton(
                        onClick = { getPlatform().openInBrowser(PNPM_WEBSITE) },
                        shape = AppShapes.small,
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Text(stringResource(Res.string.gp_env_website) + ": $PNPM_WEBSITE")
                    }
                }
            }
        }
    }
}

/**
 * 单条环境项（私有组件）：名称 + 版本/未安装状态。
 */
@Composable
private fun EnvItemRow(name: String, version: String?, installed: Boolean) {
    // 间距统一走主题槽位（紧凑模式下自动缩放）
    val spacing = LocalAppSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (installed) {
                stringResource(Res.string.gp_env_installed, version ?: "")
            } else {
                stringResource(Res.string.gp_env_missing)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
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

/**
 * 目录冲突确认弹窗（私有组件）。
 * 目标目录已存在时由用户选择处理方式：覆盖重建（删除后全新克隆）/ 继续使用（增量拉取）/ 取消创建。
 */
@Composable
private fun ExistingDirDialog(
    dirPath: String,
    onOverwrite: () -> Unit,
    onReuse: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.gp_clone_dir_exists_title)) },
        text = {
            Text(
                text = stringResource(Res.string.gp_clone_dir_exists_msg, dirPath),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text(stringResource(Res.string.gp_clone_dir_overwrite))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReuse) {
                    Text(stringResource(Res.string.gp_clone_dir_reuse))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(Res.string.gp_clone_cancel))
                }
            }
        }
    )
}
