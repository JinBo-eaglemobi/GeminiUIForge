package org.gemini.ui.forge.ui.dialog.system

import org.gemini.ui.forge.ui.dialog.system.settings.*

import androidx.compose.foundation.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import androidx.compose.foundation.gestures.*
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ProjectConfig
import org.gemini.ui.forge.ResizeHorizontalIcon
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.app.*
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.launch
import org.jetbrains.skiko.hostOs

/**
 * 应用程序全局设置对话框。
 *
 * 包含常规、AI、环境、快捷键和关于等多个设置分类面板的切换和展示。
 *
 * @param globalState 全局状态管理对象，包含各项底层设置的当前状态
 * @param appViewModel 全局 App 视图模型，负责全局操作状态的下发
 * @param settingsViewModel 设置相关的业务逻辑视图模型，控制通用/AI/快捷键持久化配置
 * @param envViewModel 环境依赖与包管理的视图模型，维护核心依赖检测和 Python 生态状态
 * @param updateViewModel 应用程序检查更新的视图模型，负责升级检测和升级动作触发
 * @param initialCategory 打开对话框时默认选中的设置分类。
 * @param onDismiss 关闭对话框的回调。
 * @param onLanguageChanged 语言改变时的回调，用于通知父组件刷新资源
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    globalState: org.gemini.ui.forge.state.app.AppGlobalState,
    appViewModel: org.gemini.ui.forge.viewmodel.AppViewModel,
    settingsViewModel: org.gemini.ui.forge.viewmodel.AppSettingsViewModel,
    envViewModel: org.gemini.ui.forge.viewmodel.AppEnvViewModel,
    updateViewModel: org.gemini.ui.forge.viewmodel.AppUpdateViewModel,
    initialCategory: SettingCategory = SettingCategory.GENERAL,
    onDismiss: () -> Unit,
    onLanguageChanged: () -> Unit
) {
    val isPc = remember { hostOs.isWindows || hostOs.isMacOS || hostOs.isLinux }
    var selectedCategory by remember {
        mutableStateOf(
            if (!isPc && initialCategory == SettingCategory.SHORTCUTS) SettingCategory.GENERAL else initialCategory
        )
    }
    var leftWeight by remember { mutableStateOf(0.3f) }

    LaunchedEffect(Unit) {
        org.gemini.ui.forge.utils.AppLogger.d("AppSettingsDialog", "Current Project Version: ${ProjectConfig.VERSION}")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false // 允许自定义超出默认系统宽度的尺寸
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(horizontal = LocalAppSpacing.current.medium, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.settings_app_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(LocalAppSpacing.current.extraLarge)) { Icon(Icons.Default.Close, null) }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Navigation
                        Box(modifier = Modifier.weight(leftWeight).fillMaxHeight()) {
                            val leftScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(leftScrollState).padding(LocalAppSpacing.current.small),
                                verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.extraSmall)
                            ) {
                                SettingCategory.entries.filter { isPc || it != SettingCategory.SHORTCUTS }.forEach { category ->
                                    val isSelected = selectedCategory == category
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedCategory = category },
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = category.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = stringResource(category.labelRes),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            VerticalScrollbarAdapter(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                scrollState = leftScrollState
                            )
                        }

                        // Draggable Divider
                        Box(
                            modifier = Modifier
                                .width(LocalAppSpacing.current.extraSmall)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                .pointerHoverIcon(ResizeHorizontalIcon)
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        val deltaWeight = delta / totalWidthPx
                                        leftWeight = (leftWeight + deltaWeight).coerceIn(0.2f, 0.45f)
                                    }
                                )
                        )

                        // Right Content
                        Box(modifier = Modifier.weight(1f - leftWeight).fillMaxHeight()) {
                            val rightScrollState = rememberScrollState()
                            val coroutineScope = rememberCoroutineScope()
                            var currentJvmXmx by remember { mutableStateOf("2G") }
                            val configManager = remember { settingsViewModel.getConfigManager() }
                            
                            LaunchedEffect(Unit) {
                                currentJvmXmx = configManager.loadJvmXmx()
                            }

                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rightScrollState).padding(LocalAppSpacing.current.medium),
                                verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.medium)
                            ) {
                                when (selectedCategory) {
                                    SettingCategory.GENERAL -> GeneralSettings(
                                        globalState = globalState,
                                        currentJvmXmx = currentJvmXmx,
                                        appViewModel = appViewModel,
                                        settingsViewModel = settingsViewModel,
                                        onLanguageChanged = onLanguageChanged
                                    )

                                    SettingCategory.AI -> AISettings(
                                        globalState = globalState,
                                        appViewModel = appViewModel,
                                        settingsViewModel = settingsViewModel
                                    )

                                    SettingCategory.PROMPTS -> PromptSettings(
                                        appViewModel = appViewModel
                                    )

                                    SettingCategory.ENVIRONMENT -> EnvironmentSettings(
                                        envViewModel = envViewModel
                                    )

                                    SettingCategory.SHORTCUTS -> ShortcutSettings(
                                        globalState = globalState,
                                        appViewModel = appViewModel,
                                        settingsViewModel = settingsViewModel
                                    )
                                    SettingCategory.ABOUT -> AboutSection(
                                        updateViewModel = updateViewModel
                                    )
                                }
                            }
                            VerticalScrollbarAdapter(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                scrollState = rightScrollState
                            )
                        }
                    }
                }
            }
        }
    }
}






