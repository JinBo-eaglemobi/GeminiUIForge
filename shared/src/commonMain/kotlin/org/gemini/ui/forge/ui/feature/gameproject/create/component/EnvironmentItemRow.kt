package org.gemini.ui.forge.ui.feature.gameproject.create.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_env_installed
import geminiuiforge.composeapp.generated.resources.gp_env_missing
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 单条环境项组件：名称 + 版本/未安装状态。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun EnvironmentItemRow(
    name: String,
    version: String?,
    installed: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (installed) {
                stringResource(Res.string.gp_env_installed, version ?: "")
            } else {
                stringResource(Res.string.gp_env_missing)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
