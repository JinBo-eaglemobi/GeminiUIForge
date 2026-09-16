package org.gemini.ui.forge.ui.feature.gameproject.create.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.jetbrains.compose.resources.stringResource

private const val NODE_WEBSITE = "https://nodejs.org"
private const val PNPM_WEBSITE = "https://pnpm.io"
private const val NODE_INSTALL_COMMAND = "npm install -g pnpm"
private const val PNPM_INSTALL_COMMAND = "npm install -g pnpm"

/**
 * 环境检测面板独立组件。
 * node 未安装时显示硬性阻断警示与安装指引（命令可复制 / 官网跳转）；
 * pnpm 缺失时提示安装方法；提供手动重新检测按钮。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun EnvironmentCheckPanel(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier
) {
    val env = viewModel.envStatus
    val spacing = LocalAppSpacing.current
    val copyAction = rememberClipboardAction()
    val copiedTip = stringResource(Res.string.gp_env_copied)

    fun copyCommand(command: String) {
        copyAction(command, copiedTip)
    }

    Column(
        modifier = modifier
            .width(340.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
        EnvironmentItemRow(stringResource(Res.string.gp_env_node), env.nodeVersion, env.nodeInstalled)
        EnvironmentItemRow(stringResource(Res.string.gp_env_npm), env.npmVersion, env.npmInstalled)
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
        EnvironmentItemRow(stringResource(Res.string.gp_env_pnpm), env.pnpmVersion, env.pnpmInstalled)

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
