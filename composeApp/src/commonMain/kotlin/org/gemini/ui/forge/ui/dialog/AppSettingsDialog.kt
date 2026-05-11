package org.gemini.ui.forge.ui.dialog

import org.gemini.ui.forge.ui.dialog.settings.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.PaddingValues
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import androidx.compose.foundation.gestures.*
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ProjectConfig
import org.gemini.ui.forge.ResizeHorizontalIcon
import org.gemini.ui.forge.getPlatform
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.rememberDirectoryPicker
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator

/**
 * 应用程序全局设置对话框。
 *
 * 包含常规、AI、环境、快捷键和关于等多个设置分类面板的切换和展示。
 *
 * @param currentTheme 当前选中的主题模式。
 * @param currentLayoutMode 当前选中的布局模式。
 * @param currentLanguage 当前选中的语言（如 "zh", "en", "auto"）。
 * @param currentApiKey 当前配置的 Gemini API 密钥。
 * @param currentStorageDir 当前配置的本地存储目录路径。
 * @param currentMaxRetries AI 生成失败时的最大重试次数，默认 3。
 * @param currentImageGenCount 每次批量生图生成的数量，默认 4。
 * @param currentPromptLang 生成提示词使用的语言偏好，默认为自动。
 * @param shortcuts 当前快捷键配置的映射。
 * @param envStatus Python 等环境状态检测结果。
 * @param pipPackages 本地已安装的 Pip 包列表。
 * @param isPipLoading 是否正在加载 Pip 包列表。
 * @param pipLogs Pip 操作执行日志。
 * @param isPipActionInProgress 是否正在进行 Pip 相关的安装/卸载操作。
 * @param searchResult 云端市场包的搜索结果。
 * @param isSearching 是否正在搜索。
 * @param topMarketPackages 云端市场热门推荐包列表。
 * @param isMarketLoading 是否正在加载市场数据。
 * @param marketPage 市场数据的当前分页索引。
 * @param initialCategory 打开对话框时默认选中的设置分类。
 * @param updateStatus 应用程序更新状态。
 * @param onDismiss 关闭对话框的回调。
 * @param onLanguageSelected 选择语言后的回调。
 * @param onLayoutModeSelected 选择布局模式后的回调。
 * @param onThemeSelected 选择主题模式后的回调。
 * @param onApiKeySaved 保存 API 密钥的回调。
 * @param onStorageDirSaved 保存存储目录的回调。
 * @param onMaxRetriesSaved 保存最大重试次数的回调。
 * @param onImageGenCountSaved 保存每次生图数量的回调。
 * @param onPromptLangSelected 选择提示词语言的回调。
 * @param onShortcutSaved 保存修改后的快捷键的回调。
 * @param onCheckEnv 触发重新检测环境状态的回调。
 * @param onInstallEnvItem 安装指定环境组件（如 Python）的回调。
 * @param onUninstallEnvItem 卸载指定环境组件的回调。
 * @param onBatchInstallPip 批量安装 Pip 包的回调。
 * @param onBatchUninstallPip 批量卸载 Pip 包的回调。
 * @param onOpenPackageUrl 在浏览器中打开 Pip 包详情链接的回调。
 * @param onSearchPipPackage 在云端市场搜索 Pip 包的回调。
 * @param onClearSearchResult 清除搜索结果的回调。
 * @param onLoadMarketPage 加载市场指定分页数据的回调。
 * @param onCheckUpdate 触发检查应用更新的回调。
 * @param onStartUpdate 确认开始下载并更新应用的回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    currentTheme: ThemeMode,
    currentLayoutMode: org.gemini.ui.forge.model.app.LayoutMode,
    currentLanguage: String,
    currentApiKey: String,
    currentStorageDir: String,
    currentMaxRetries: Int = 3,
    currentImageGenCount: Int = 4,
    currentPromptLang: PromptLanguage = PromptLanguage.AUTO,
    shortcuts: Map<ShortcutAction, String>,
    envStatus: FullEnvironmentStatus,
    pipPackages: List<PipPackageInfo> = emptyList(),
    isPipLoading: Boolean = false,
    pipLogs: List<String> = emptyList(),
    isPipActionInProgress: Boolean = false,
    searchResult: PipPackageInfo? = null,
    isSearching: Boolean = false,
    topMarketPackages: List<PipPackageInfo> = emptyList(),
    isMarketLoading: Boolean = false,
    marketPage: Int = 0,
    initialCategory: SettingCategory = SettingCategory.GENERAL,
    updateStatus: UpdateStatus = UpdateStatus.Idle,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onLayoutModeSelected: (org.gemini.ui.forge.model.app.LayoutMode) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onApiKeySaved: (String) -> Unit,
    onStorageDirSaved: (String) -> Unit,
    onMaxRetriesSaved: (Int) -> Unit = {},
    onImageGenCountSaved: (Int) -> Unit = {},
    onPromptLangSelected: (PromptLanguage) -> Unit = {},
    onShortcutSaved: (ShortcutAction, String) -> Unit = { _, _ -> },
    onCheckEnv: () -> Unit = {},
    onInstallEnvItem: (String) -> Unit = {},
    onUninstallEnvItem: (String) -> Unit = {},
    onBatchInstallPip: (List<String>) -> Unit = {},
    onBatchUninstallPip: (List<String>) -> Unit = {},
    onOpenPackageUrl: (String) -> Unit = {},
    onSearchPipPackage: (String) -> Unit = {},
    onClearSearchResult: () -> Unit = {},
    onLoadMarketPage: (Int) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onStartUpdate: (UpdateInfo) -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf(initialCategory) }
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
                                SettingCategory.entries.forEach { category ->
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
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rightScrollState).padding(LocalAppSpacing.current.medium),
                                verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.medium)
                            ) {
                                when (selectedCategory) {
                                    SettingCategory.GENERAL -> GeneralSettings(
                                        currentTheme, currentLayoutMode, currentLanguage, currentStorageDir,
                                        onThemeSelected, onLayoutModeSelected, onLanguageSelected, onStorageDirSaved
                                    )

                                    SettingCategory.AI -> AISettings(
                                        currentApiKey, currentMaxRetries, currentImageGenCount, currentPromptLang,
                                        onApiKeySaved, onMaxRetriesSaved, onImageGenCountSaved, onPromptLangSelected
                                    )

                                    SettingCategory.ENVIRONMENT -> EnvironmentSettings(
                                        status = envStatus,
                                        pipPackages = pipPackages,
                                        isPipLoading = isPipLoading,
                                        pipLogs = pipLogs,
                                        isPipActionInProgress = isPipActionInProgress,
                                        searchResult = searchResult,
                                        isSearching = isSearching,
                                        topMarketPackages = topMarketPackages,
                                        isMarketLoading = isMarketLoading,
                                        marketPage = marketPage,
                                        onCheck = onCheckEnv,
                                        onInstall = onInstallEnvItem,
                                        onUninstall = onUninstallEnvItem,
                                        onBatchInstallPip = onBatchInstallPip,
                                        onBatchUninstallPip = onBatchUninstallPip,
                                        onOpenPackageUrl = onOpenPackageUrl,
                                        onSearchPipPackage = onSearchPipPackage,
                                        onClearSearchResult = onClearSearchResult,
                                        onLoadMarketPage = onLoadMarketPage
                                    )

                                    SettingCategory.SHORTCUTS -> ShortcutSettings(shortcuts, onShortcutSaved)
                                    SettingCategory.ABOUT -> AboutSection(updateStatus, onCheckUpdate, onStartUpdate)
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






