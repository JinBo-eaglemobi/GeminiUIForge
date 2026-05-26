package org.gemini.ui.forge.ui.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import org.gemini.ui.forge.model.app.UIModule
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.ui.feature.workspace.ModuleCard
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.state.ui.ProjectState

/**
 * 应用主界面（首页）。
 * 展示所有已保存的 UI 模块模板，并提供进入工作区、删除模板等入口。
 *
 * @param appViewModel 全局基础 ViewModel。
 * @param templateRepo 模板数据存储库。
 */
@Composable
fun HomeScreen(
    appViewModel: AppViewModel,
    templateRepo: TemplateRepository
) {
    var templatesList by remember { mutableStateOf(emptyList<Pair<String, ProjectState>>()) }
    val coroutineScope = rememberCoroutineScope()
    var moduleToDelete by remember { mutableStateOf<UIModule?>(null) }

    LaunchedEffect(templateRepo) {
        templatesList = templateRepo.getTemplates()
    }

    val modules = remember(templatesList) {
        templatesList.map { (name, projectState) ->
            UIModule(id = name, nameStr = name, projectState = projectState)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (modules.isEmpty()) {
            Text(
                text = stringResource(Res.string.no_modules),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                items(modules) { module ->
                    ModuleCard(
                        module = module,
                        onOpenWorkspace = {
                            appViewModel.loadProject(
                                module.nameStr ?: module.id,
                                module.projectState!!
                            )
                            appViewModel.navigateTo(AppScreen.PROJECT_WORKSPACE)
                        },
                        onOpenFileDir = {
                            coroutineScope.launch {
                                templateRepo.openFileDir(module)
                            }
                        },
                        onDelete = { moduleToDelete = module }
                    )
                }
            }
        }
    }

    moduleToDelete?.let { module ->
        val title = if (module.nameRes != null) stringResource(module.nameRes) else module.nameStr ?: "Unknown"
        AlertDialog(
            onDismissRequest = { moduleToDelete = null },
            title = { Text(stringResource(Res.string.dialog_delete_title)) },
            text = { Text("${stringResource(Res.string.dialog_delete_message)}\n($title)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            templateRepo.deleteTemplate(module.id)
                            templatesList = templateRepo.getTemplates()
                            moduleToDelete = null
                        }
                    },
                    shape = AppShapes.medium
                ) {
                    Text(stringResource(Res.string.dialog_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { moduleToDelete = null },
                    shape = AppShapes.medium
                ) {
                    Text(stringResource(Res.string.dialog_action_cancel))
                }
            }
        )
    }
}
