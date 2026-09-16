package org.gemini.ui.forge.ui.feature.gameproject.clone.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_clone_log_title
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 执行日志面板组件：自动滚动到底部。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun CloneLogPanel(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.cloneLogs.size) {
        if (viewModel.cloneLogs.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.cloneLogs.size - 1)
        }
    }
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.gp_clone_log_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = AppShapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    items(viewModel.cloneLogs) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
