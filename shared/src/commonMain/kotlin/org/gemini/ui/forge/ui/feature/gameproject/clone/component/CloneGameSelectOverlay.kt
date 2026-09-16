package org.gemini.ui.forge.ui.feature.gameproject.clone.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_clone_confirm
import geminiuiforge.composeapp.generated.resources.gp_clone_select_hint
import org.gemini.ui.forge.ui.theme.AppShapes
import org.jetbrains.compose.resources.stringResource

/**
 * 游戏选择覆盖层组件：单选列表 + 确认按钮。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun CloneGameSelectOverlay(
    games: List<String>,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember(games) { mutableStateOf<String?>(null) }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = AppShapes.small
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.gp_clone_select_hint),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                games.forEach { game ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = game }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        RadioButton(
                            selected = selected == game,
                            onClick = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = game,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected == game) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
                shape = AppShapes.medium
            ) {
                Text(stringResource(Res.string.gp_clone_confirm))
            }
        }
    }
}
