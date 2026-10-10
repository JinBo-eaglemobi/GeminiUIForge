package org.gemini.ui.forge.ui.feature.gameproject.create.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
 * 全景开发环境检测面板独立组件。
 * 遵循一文件一 Composable 铁律。
 * 按模块分组展示：
 * 1. Web 核心运行时 (Node.js / npm / pnpm)；
 * 2. 版本协作与脚本环境 (Git / Python / pip)；
 * 3. JVM 与工程构建环境 (Java / Gradle)。
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

    // 计算已就绪的环境总数
    val totalChecked = 7 // node, npm, pnpm, git, python, pip, java
    var readyCount = 0
    if (env.nodeInstalled) readyCount++
    if (env.npmInstalled) readyCount++
    if (env.pnpmInstalled) readyCount++
    if (env.gitInstalled) readyCount++
    if (env.pythonInstalled) readyCount++
    if (env.pipInstalled) readyCount++
    if (env.javaInstalled) readyCount++

    Column(
        modifier = modifier
            .width(360.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // 顶部标题与健康度统计条
        Card(
            shape = AppShapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(Res.string.gp_env_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "系统开发环境与工具链就绪度",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewModel.checkEnv() }, enabled = !viewModel.envChecking) {
                        if (viewModel.envChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.gp_env_refresh))
                        }
                    }
                }

                Spacer(Modifier.height(spacing.small))

                // 就绪度统计胶囊标签
                Surface(
                    shape = RoundedCornerShape(spacing.extraSmall),
                    color = if (readyCount >= 5) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "环境就绪: $readyCount / $totalChecked 项可用" + if (env.gradleInstalled) " (含系统 Gradle)" else "",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (readyCount >= 5) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = spacing.small, vertical = 4.dp)
                    )
                }
            }
        }

        // 分组一：Web 核心运行时 (Node.js / npm / pnpm)
        Card(
            shape = AppShapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Web 核心运行时 (JavaScript / Node.js)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(spacing.small))

                // node 未安装：阻断提示
                if (env.isHardBlocked) {
                    Card(
                        shape = AppShapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(spacing.small)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text(
                                    text = stringResource(Res.string.gp_env_node_blocked),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(spacing.extraSmall))
                            TextButton(
                                onClick = { getPlatform().openInBrowser(NODE_WEBSITE) },
                                shape = AppShapes.small,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(stringResource(Res.string.gp_env_website) + ": $NODE_WEBSITE", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(spacing.small))
                }

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

                // pnpm 缺失提示
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
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 分组二：版本协作与脚本环境 (Git & Python)
        Card(
            shape = AppShapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "版本协作与脚本 (Git & Python)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(spacing.small))

                EnvironmentItemRow("Git (版本控制)", env.gitVersion, env.gitInstalled)
                EnvironmentItemRow("Python (脚本/AI抠图)", env.pythonVersion, env.pythonInstalled)
                EnvironmentItemRow("pip (Python 包管理)", env.pipVersion, env.pipInstalled)
            }
        }

        // 分组三：JVM 与工程构建环境 (Java & Gradle)
        Card(
            shape = AppShapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "JVM 与构建环境 (Java / Gradle)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(spacing.small))

                EnvironmentItemRow("Java JDK (运行环境)", env.javaVersion, env.javaInstalled)
                EnvironmentItemRow("Gradle (自动化构建)", env.gradleVersion, env.gradleInstalled)
            }
        }
    }
}
