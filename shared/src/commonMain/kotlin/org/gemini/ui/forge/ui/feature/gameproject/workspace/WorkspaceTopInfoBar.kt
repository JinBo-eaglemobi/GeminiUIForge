package org.gemini.ui.forge.ui.feature.gameproject.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_ws_language
import geminiuiforge.composeapp.generated.resources.gp_ws_open_dir
import geminiuiforge.composeapp.generated.resources.gp_ws_pm
import geminiuiforge.composeapp.generated.resources.gp_ws_selected_game
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.gameproject.GameProjectInfo
import org.gemini.ui.forge.ui.theme.AppShapes
import org.jetbrains.compose.resources.stringResource

/**
 * 顶部信息条：项目名 + 游戏/语言/管理模式胶囊 + 本地路径 + 打开目录。
 */
@Composable
fun TopInfoBar(project: GameProjectInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            InfoChip(stringResource(Res.string.gp_ws_selected_game, project.selectedGame.ifBlank { "-" }))
            InfoChip(stringResource(Res.string.gp_ws_language, project.language.name))
            InfoChip(stringResource(Res.string.gp_ws_pm, project.packageManager.name))
            Text(
                text = project.localPath,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { getPlatform().openInFileExplorer(project.localPath) }) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = stringResource(Res.string.gp_ws_open_dir),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 顶部信息小胶囊。
 */
@Composable
private fun InfoChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        shape = AppShapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
