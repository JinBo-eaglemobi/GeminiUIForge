package org.gemini.ui.forge.ui.feature.gameproject.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_platform_unsupported
import org.jetbrains.compose.resources.stringResource

/**
 * 游戏 HTML 预览面板的 Android 占位实现（待适配：遵循桌面版优先规范）。
 */
@Composable
actual fun GameHtmlPreview(
    htmlPath: String?,
    debugMode: Boolean,
    inspectTarget: String?,
    onDebugMessage: (String) -> Unit,
    reloadTrigger: Int,
    devToolsTrigger: Int,
    isDemoSelected: Boolean,
    isDebugSelected: Boolean,
    selectedGame: String,
    onUrlComputed: (String) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.gp_platform_unsupported),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
