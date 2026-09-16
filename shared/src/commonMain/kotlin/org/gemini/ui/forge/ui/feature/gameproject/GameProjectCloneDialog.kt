package org.gemini.ui.forge.ui.feature.gameproject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.gameproject.CloneStep
import org.gemini.ui.forge.model.gameproject.InstallChoice
import org.gemini.ui.forge.service.CloneRefType
import org.gemini.ui.forge.service.CommitSummary
import org.gemini.ui.forge.service.GitRefCatalog
import org.gemini.ui.forge.service.RefSelection
import org.gemini.ui.forge.ui.feature.gameproject.clone.component.*
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.GameProjectViewModel

/**
 * 项目创建流程弹窗。
 * 左侧为步骤进度列表，右侧为执行日志 / 游戏选择覆盖层 / 依赖检查面板：
 * - 游戏扫描完成后在右侧覆盖弹出单选列表，选择后继续资源下载；
 * - 克隆完成后切换到 pnpm 依赖检查面板（缺失项红色显示，可选安装方式）；
 * - 全部处理完成后点击"进入项目"进入项目专属工作台。
 *
 * @param viewModel 游戏项目管理 ViewModel
 * @param onEnterWorkspace 进入工作台回调（由外部完成导航）
 */
@Composable
fun GameProjectCloneDialog(
    viewModel: GameProjectViewModel,
    onEnterWorkspace: () -> Unit = {}
) {
    if (!viewModel.cloneDialogVisible) return
    val step = viewModel.cloneProgress.step

    Dialog(
        onDismissRequest = {
            // 仅失败状态允许点外部关闭；执行中必须显式取消
            if (step == CloneStep.FAILED) viewModel.cancelClone()
        },
        // 关闭"平台默认宽度"限制：桌面端该选项默认开启时会强制按内容宽度换行测量，
        // 导致 fillMaxWidth 百分比尺寸失效（弹窗被挤成窄条）
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 尺寸按宿主窗口比例约束：相对占比不受全局紧凑缩放（Density 重映射）影响，避免弹窗视觉缩水
        Surface(shape = AppShapes.large, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(0.62f).fillMaxHeight(0.82f).padding(20.dp)
            ) {
                Text(
                    text = stringResource(Res.string.gp_clone_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                // 失败错误条
                viewModel.cloneErrorMessage?.let { error ->
                    Surface(
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(modifier = Modifier.weight(1f)) {
                    // 左侧：步骤列表
                    Column(
                        modifier = Modifier.width(220.dp).fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val stepItems = listOf(
                            CloneStep.CREATE_FOLDER to Res.string.gp_step_create_folder,
                            CloneStep.CONNECT_FETCH to Res.string.gp_step_connect_fetch,
                            CloneStep.SELECT_VERSION to Res.string.gp_step_select_version,
                            CloneStep.SCAN_GAMES to Res.string.gp_step_scan_games,
                            CloneStep.WAIT_SELECT_GAME to Res.string.gp_step_wait_select,
                            CloneStep.APPLY_FILTER to Res.string.gp_step_apply_filter,
                            CloneStep.DOWNLOAD_ASSETS to Res.string.gp_step_download,
                            CloneStep.CHECK_DEPENDENCIES to Res.string.gp_step_check_deps,
                            CloneStep.COMPLETE to Res.string.gp_step_complete
                        )
                        stepItems.forEach { (stepEnum, labelRes) ->
                            CloneStepRow(
                                label = stringResource(labelRes),
                                state = when {
                                    step == CloneStep.FAILED && stepEnum.ordinal >= CloneStep.CONNECT_FETCH.ordinal -> CloneStepState.FAILED
                                    stepEnum.ordinal < step.ordinal -> CloneStepState.DONE
                                    // 终态 COMPLETE 属于"全部完成"的完结标记，自身直接显示对勾而非转圈
                                    stepEnum == step -> if (stepEnum == CloneStep.COMPLETE) CloneStepState.DONE else CloneStepState.ACTIVE
                                    else -> CloneStepState.PENDING
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    // 右侧：内容区（日志 / 依赖面板 + 游戏选择覆盖层）
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (step == CloneStep.CHECK_DEPENDENCIES || step == CloneStep.COMPLETE) {
                            CloneDependencyPanel(viewModel)
                        } else {
                            CloneLogPanel(viewModel)
                        }
                        // 游戏选择覆盖层
                        viewModel.pendingGames?.let { games ->
                            CloneGameSelectOverlay(games = games, onConfirm = { viewModel.confirmGame(it) })
                        }
                        // 版本选择覆盖层（fetch 完成后挂起等待用户选择检出版本）
                        viewModel.pendingVersionCatalog?.let { catalog ->
                            CloneVersionSelectOverlay(
                                catalog = catalog,
                                onConfirm = { viewModel.confirmVersion(it) },
                                onCancel = { viewModel.cancelVersionChoice() }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                // 底部操作按钮
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    when (step) {
                        CloneStep.FAILED -> {
                            TextButton(onClick = { viewModel.cancelClone() }, shape = AppShapes.medium) {
                                Text(stringResource(Res.string.gp_clone_close))
                            }
                        }
                        CloneStep.COMPLETE -> {
                            Button(
                                onClick = {
                                    viewModel.finishProject()?.let { onEnterWorkspace() }
                                },
                                shape = AppShapes.medium
                            ) {
                                Text(stringResource(Res.string.gp_deps_enter))
                            }
                        }
                        else -> {
                            TextButton(onClick = { viewModel.cancelClone() }, shape = AppShapes.medium) {
                                Text(stringResource(Res.string.gp_clone_cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}
