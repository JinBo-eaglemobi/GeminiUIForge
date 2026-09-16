package org.gemini.ui.forge.ui.feature.gameproject.create.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.gameproject.CredentialStorageKind
import org.gemini.ui.forge.model.gameproject.GitCredentialMode
import org.gemini.ui.forge.model.gameproject.GitCredentialSource
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.utils.rememberFilePicker
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Git 凭据区独立组件。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun GitCredentialSection(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier
) {
    val form = viewModel.form
    val credentials = viewModel.localCredentials
    val spacing = LocalAppSpacing.current

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

    val effectiveCredential = viewModel.selectedCredential ?: credentials.firstOrNull()

    val displayValue = when (form.credentialMode) {
        GitCredentialMode.SSH_KEY -> form.sshKeyPath
        GitCredentialMode.GIT_TOKEN -> when {
            !form.useGlobalCredential -> form.gitToken
            effectiveCredential == null -> ""
            !effectiveCredential.token.isNullOrBlank() -> effectiveCredential.token
            else -> credentialLabels[effectiveCredential] ?: ""
        }
    }

    val sshCredentials = remember(credentials) { credentials.filter { it.source == GitCredentialSource.SSH_KEY } }

    val sshKeyPicker = rememberFilePicker(
        title = stringResource(Res.string.gp_cred_ssh_pick),
        isFolder = false,
        extensions = emptyList<String>(),
        onResult = { path: String? ->
            if (path != null) {
                viewModel.updateForm { it.copy(credentialMode = GitCredentialMode.SSH_KEY, sshKeyPath = path) }
            }
        }
    )

    Card(shape = AppShapes.medium, modifier = modifier) {
        Column(modifier = Modifier.padding(spacing.medium).fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.gp_form_cred_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(spacing.small))

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

                val locatedCredential = effectiveCredential?.takeIf {
                    (form.credentialMode == GitCredentialMode.SSH_KEY && !it.keyFilePath.isNullOrBlank()) ||
                            (form.useGlobalCredential && it.storageKind != CredentialStorageKind.NONE)
                }
                if (locatedCredential != null) {
                    val locateTarget = locatedCredential
                    IconButton(
                        onClick = {
                            when (locateTarget.storageKind) {
                                CredentialStorageKind.FILE -> getPlatform().openInFileExplorer(locateTarget.storageLocation.orEmpty())
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
