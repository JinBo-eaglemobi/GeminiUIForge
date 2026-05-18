package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Help
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 应用顶部导航栏组件。
 * 提供返回、标题显示、当前模式标识以及核心功能按钮（保存、云资产、帮助、设置）。
 *
 * @param currentScreen 当前所在的屏幕页面。
 * @param onNavigateHome 点击返回首页的回调。
 * @param onGenerateTemplateClicked 点击 AI 生成模板的回调。
 * @param onCloudAssetManagerClicked 点击云端资产管理的回调。
 * @param onSaveClicked 点击保存项目的回调。
 * @param onSettingsClicked 点击应用设置的回调。
 * @param onHelpClicked 点击帮助的回调。
 */
@Composable
fun AppTopBar(
    currentScreen: AppScreen,
    onNavigateHome: () -> Unit,
    onGenerateTemplateClicked: () -> Unit = {},
    onCloudAssetManagerClicked: () -> Unit = {},
    onSaveClicked: () -> Unit = {},
    onSettingsClicked: () -> Unit = {},
    onHelpClicked: () -> Unit = {}
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：返回按钮 + 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentScreen != AppScreen.HOME) {
                    IconButton(
                        onClick = onNavigateHome, 
                        modifier = Modifier.size(32.dp).tip("返回首页")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 当前模式标识
                val modeNameRes = when (currentScreen) {
                    AppScreen.HOME -> Res.string.screen_home
                    AppScreen.TEMPLATE_ASSET_GEN -> Res.string.screen_template_asset_gen
                    AppScreen.TEMPLATE_EDITOR -> Res.string.screen_template_editor
                    AppScreen.TEMPLATE_GENERATOR -> Res.string.screen_template_generator
                    AppScreen.PROJECT_WORKSPACE -> Res.string.screen_project_workspace
                }
                
                Surface(
                    modifier = Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = AppShapes.small
                ) {
                    Text(
                        text = stringResource(modeNameRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // 右侧功能按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentScreen == AppScreen.HOME) {
                    TextButton(
                        onClick = onGenerateTemplateClicked,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = AppShapes.medium,
                        modifier = Modifier.tip("通过 AI 分析生图并创建新模板")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.menu_generate_template), style = MaterialTheme.typography.labelLarge)
                    }

                    TextButton(
                        onClick = onCloudAssetManagerClicked,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = AppShapes.medium,
                        modifier = Modifier.tip("管理已上传至云端的媒体资产")
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.menu_cloud_assets), style = MaterialTheme.typography.labelLarge)
                    }
                } else if (currentScreen == AppScreen.TEMPLATE_EDITOR || currentScreen == AppScreen.TEMPLATE_ASSET_GEN || currentScreen == AppScreen.PROJECT_WORKSPACE) {
                    IconButton(
                        onClick = onSaveClicked,
                        modifier = Modifier.tip("保存当前项目修改 (Ctrl+S)")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save Layout", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                // 帮助与设置按钮（始终显示）
                IconButton(onClick = onHelpClicked, modifier = Modifier.tip("查看使用说明")) {
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = stringResource(Res.string.menu_help))
                }
                IconButton(onClick = onSettingsClicked, modifier = Modifier.tip("应用全局设置")) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.menu_settings))
                }
            }
        }
    }
}
