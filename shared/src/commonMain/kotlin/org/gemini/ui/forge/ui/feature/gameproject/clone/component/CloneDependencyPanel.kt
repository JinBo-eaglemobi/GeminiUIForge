package org.gemini.ui.forge.ui.feature.gameproject.clone.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.gameproject.InstallChoice
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 依赖检查面板组件。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun CloneDependencyPanel(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.gp_deps_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { viewModel.recheckDependencies() }, shape = AppShapes.medium) {
                Text(stringResource(Res.string.gp_deps_recheck))
            }
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = AppShapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)
            ) {
                items(viewModel.dependencies) { dep ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dep.name + if (dep.isDev) " (dev)" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = dep.versionRange,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (dep.installed) {
                                stringResource(Res.string.gp_deps_installed)
                            } else {
                                stringResource(Res.string.gp_deps_missing)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (dep.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (!dep.installed) {
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = dep.installChoice == InstallChoice.SKIP,
                                onClick = { viewModel.updateDepChoice(dep.name, InstallChoice.SKIP) },
                                label = { Text(stringResource(Res.string.gp_deps_choice_skip), style = MaterialTheme.typography.labelSmall) }
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = dep.installChoice == InstallChoice.PNPM,
                                onClick = { viewModel.updateDepChoice(dep.name, InstallChoice.PNPM) },
                                label = { Text(stringResource(Res.string.gp_deps_choice_pnpm), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }
        if (viewModel.depsInstalling || viewModel.installLogs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = AppShapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                    val installLogState = rememberLazyListState()
                    LaunchedEffect(viewModel.installLogs.size) {
                        if (viewModel.installLogs.isNotEmpty()) {
                            installLogState.animateScrollToItem(viewModel.installLogs.size - 1)
                        }
                    }
                    SelectionContainer {
                        LazyColumn(
                            state = installLogState,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            items(viewModel.installLogs) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val hasPnpmChoice = viewModel.dependencies.any { it.installChoice == InstallChoice.PNPM }
            if (hasPnpmChoice) {
                Button(
                    onClick = { viewModel.installSelected() },
                    enabled = !viewModel.depsInstalling,
                    shape = AppShapes.medium
                ) {
                    if (viewModel.depsInstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(stringResource(Res.string.gp_deps_install))
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
