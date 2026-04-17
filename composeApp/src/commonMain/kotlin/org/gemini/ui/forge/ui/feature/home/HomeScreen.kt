package org.gemini.ui.forge.ui.feature.home
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.gemini.ui.forge.model.app.UIModule
import org.gemini.ui.forge.ui.theme.AppShapes

@Composable
fun HomeScreen(
    modules: List<UIModule>,
    onEditLayout: (String) -> Unit,
    onGenerateUI: (String) -> Unit,
    onDeleteModule: (String) -> Unit = {}
) {
    var moduleToDelete by remember { mutableStateOf<UIModule?>(null) }

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
                        onEditLayout = { onEditLayout(module.id) },
                        onGenerateUI = { onGenerateUI(module.id) },
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
                        onDeleteModule(module.id)
                        moduleToDelete = null
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
