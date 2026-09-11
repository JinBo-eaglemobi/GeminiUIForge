package org.gemini.ui.forge.ui.dialog.system.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.manager.PromptMeta
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 提示词查看模式枚举
 */
enum class PromptViewMode {
    /** 当前生效/正在编辑的版本 */
    CURRENT,
    /** 出厂内置默认预设（只读旧版对比） */
    FACTORY_DEFAULT
}

/**
 * 提示词管理设置子面板组件。
 *
 * 核心特性：
 * 1. 集中管理系统内置的 11 大核心 AI 提示词模板；
 * 2. 支持直接在线查看并编辑 Prompt 内容（不分中英文，通用文本编辑）；
 * 3. 增强对比功能：支持随时切换查看出厂原始旧版预设（只读模式），并提供一键回填到当前编辑框；
 * 4. 实质内容比对：基于换行符归一化与空白抹平，仅在内容存在实质差异时判定为"外部已自定义"；
 * 5. 修改保存一律写入外部本地存储目录 (~/.geminiuiforge/prompts/)，大模型调用时 100% 优先读取外部缓存；
 * 6. 支持一键重置回滚并物理清理外部历史冗余文件；
 * 7. 全量遵循 Design Tokens 间距标准与 Modifier.tip 规范。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptSettings(
    appViewModel: AppViewModel
) {
    val promptManager = remember { appViewModel.aiService.promptManager }
    val metas = remember { promptManager.promptMetas }
    var selectedMeta by remember { mutableStateOf(metas.first()) }
    
    var currentContent by remember(selectedMeta) { mutableStateOf("") }
    var defaultContent by remember(selectedMeta) { mutableStateOf("") }
    var initialContent by remember(selectedMeta) { mutableStateOf("") }
    var hasPhysicalFile by remember(selectedMeta) { mutableStateOf(false) }
    var isCustomized by remember(selectedMeta) { mutableStateOf(false) }
    var isLoading by remember(selectedMeta) { mutableStateOf(true) }
    var viewMode by remember(selectedMeta) { mutableStateOf(PromptViewMode.CURRENT) }

    // 全局所有模板的外部自定义状态缓存字典 (meta.id -> Boolean)
    var customizedMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    val coroutineScope = rememberCoroutineScope()
    val spacing = LocalAppSpacing.current

    // 重新扫描所有模板是否已被用户自定义
    fun reloadCustomizedMap() {
        coroutineScope.launch {
            val map = metas.associate { it.id to promptManager.isCustomized(it.id) }
            customizedMap = map
        }
    }

    LaunchedEffect(Unit) {
        reloadCustomizedMap()
    }

    val saveSuccessTip = stringResource(Res.string.prompt_settings_save_success)
    val resetSuccessTip = stringResource(Res.string.prompt_settings_reset_success)
    val saveBtnText = stringResource(Res.string.prompt_settings_save_btn)
    val resetBtnText = stringResource(Res.string.prompt_settings_reset_btn)
    val applyDefaultSuccessTip = stringResource(Res.string.prompt_apply_default_success)
    val applyDefaultBtnText = stringResource(Res.string.prompt_apply_default_btn)
    val applyDefaultTip = stringResource(Res.string.prompt_apply_default_tip)
    val tabCurrentText = stringResource(Res.string.prompt_tab_current)
    val tabDefaultText = stringResource(Res.string.prompt_tab_default)
    val badgeModifiedText = stringResource(Res.string.prompt_badge_modified)
    val readonlyNoticeText = stringResource(Res.string.prompt_readonly_notice)

    // 加载当前选中模板的内容与出厂默认内容
    LaunchedEffect(selectedMeta) {
        isLoading = true
        viewMode = PromptViewMode.CURRENT
        val text = promptManager.getPrompt(selectedMeta.id)
        val defaultText = promptManager.getDefaultResourcePrompt(selectedMeta.id)
        currentContent = text
        defaultContent = defaultText
        initialContent = text
        hasPhysicalFile = promptManager.hasPhysicalExternalFile(selectedMeta.id)
        isCustomized = promptManager.isCustomized(selectedMeta.id)
        isLoading = false
    }

    val isModified = currentContent != initialContent
    // 精准实质内容差异（抹平换行符与首尾空白）
    val hasDiffFromDefault = promptManager.normalizeText(currentContent) != promptManager.normalizeText(defaultContent)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // 1. 标题与说明
        SettingSectionTitle(stringResource(Res.string.settings_category_prompts))

        Text(
            text = stringResource(Res.string.prompt_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 2. 模板选择列表与快速状态栏
        val customizedCount = customizedMap.count { it.value }
        val defaultCount = metas.size - customizedCount

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = AppShapes.medium
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择要配置的提示词功能模板:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = AppShapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "已自定义: $customizedCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = AppShapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "出厂预置: $defaultCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 模板标签选择群组：对已修改自定义模板做醒目区分
                OptInHorizontalFlowRow(
                    metas = metas,
                    selectedMeta = selectedMeta,
                    customizedMap = customizedMap,
                    onSelect = { selectedMeta = it }
                )
            }
        }

        // 3. 当前选中模板的详细元数据与编辑区
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            shape = AppShapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                // 顶部元数据与版本切换 Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(spacing.small))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedMeta.displayNameZh,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(spacing.small))
                            Surface(
                                color = when {
                                    isModified -> MaterialTheme.colorScheme.errorContainer
                                    isCustomized -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = AppShapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCustomized || isModified) {
                                        Icon(
                                            imageVector = if (isModified) Icons.Default.EditNote else Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = if (isModified) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = when {
                                            isModified -> "正在编辑 (未保存 *)"
                                            isCustomized -> "已自定义 (外部生效中)"
                                            else -> "出厂预置默认"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isModified -> MaterialTheme.colorScheme.onErrorContainer
                                            isCustomized -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            text = selectedMeta.descZh,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 版本对比与切换：官方 ButtonGroup + customItem + ToggleButton 官方连接形状（ButtonGroupDefaults.connected*），
                    // 零间距拼接 + 锁定形状不形变，重现旧 SingleChoiceSegmentedButtonRow 一颗胶囊分两半的经典分段外观
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    ButtonGroup(
                        // 仅 2 个切换项，禁用溢出指示器；零间距让两段无缝拼为一颗胶囊
                        overflowIndicator = { },
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier
                    ) {
                        // 左半段：当前生效/编辑中版本（外侧全圆角、内侧直角、选中不形变）
                        customItem(
                            buttonGroupContent = {
                                val leadingShape = RoundedCornerShape(
                                    topStart = 20.dp, bottomStart = 20.dp,
                                    topEnd = 0.dp, bottomEnd = 0.dp
                                )
                                ToggleButton(
                                    checked = viewMode == PromptViewMode.CURRENT,
                                    onCheckedChange = { viewMode = PromptViewMode.CURRENT },
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(
                                        shape = leadingShape,
                                        pressedShape = leadingShape,
                                        checkedShape = leadingShape
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(spacing.small))
                                    Text(
                                        text = if (hasDiffFromDefault) "$tabCurrentText ($badgeModifiedText)" else tabCurrentText,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            },
                            menuContent = { }
                        )
                        // 右半段：出厂预置原始模板（镜像对称，选中不形变）
                        customItem(
                            buttonGroupContent = {
                                val trailingShape = RoundedCornerShape(
                                    topStart = 0.dp, bottomStart = 0.dp,
                                    topEnd = 20.dp, bottomEnd = 20.dp
                                )
                                ToggleButton(
                                    checked = viewMode == PromptViewMode.FACTORY_DEFAULT,
                                    onCheckedChange = { viewMode = PromptViewMode.FACTORY_DEFAULT },
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(
                                        shape = trailingShape,
                                        pressedShape = trailingShape,
                                        checkedShape = trailingShape
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(spacing.small))
                                    Text(
                                        text = tabDefaultText,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            },
                            menuContent = { }
                        )
                    }
                }

                Spacer(Modifier.height(spacing.medium))

                // Prompt 多行编辑/查看输入框（400dp 大视口高度，内部纵向滚动，保证底部操作栏始终在可视区域）
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    if (viewMode == PromptViewMode.FACTORY_DEFAULT) {
                        // 出厂预设查看模式（只读视图）
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                shape = AppShapes.small,
                                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.extraSmall)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = spacing.small, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(Modifier.width(spacing.extraSmall))
                                    Text(
                                        text = readonlyNoticeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }

                            SelectAllOutlinedTextField(
                                value = defaultContent,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("出厂默认模板内容 (只读参考)") },
                                modifier = Modifier.fillMaxWidth().height(364.dp),
                                shape = AppShapes.small,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            )
                        }
                    } else {
                        // 当前版本编辑模式
                        SelectAllOutlinedTextField(
                            value = currentContent,
                            onValueChange = { currentContent = it },
                            label = { Text("Prompt 模板文本内容 (可直接修改)") },
                            modifier = Modifier.fillMaxWidth().height(400.dp),
                            shape = AppShapes.small,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }

                Spacer(Modifier.height(spacing.medium))

                // 4. 底部操作按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (viewMode == PromptViewMode.FACTORY_DEFAULT) {
                        // 在出厂预设模式下，提供【以此覆盖当前编辑】按钮
                        Button(
                            onClick = {
                                currentContent = defaultContent
                                viewMode = PromptViewMode.CURRENT
                                Toast.show(applyDefaultSuccessTip, ToastType.INFO)
                            },
                            shape = AppShapes.medium,
                            modifier = Modifier.tip(applyDefaultTip)
                        ) {
                            Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text(applyDefaultBtnText)
                        }
                    } else {
                        // 当前版本模式下的【恢复出厂默认】与【保存至外部缓存】按钮
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val success = promptManager.resetPrompt(selectedMeta.id)
                                    val defaultText = promptManager.getDefaultResourcePrompt(selectedMeta.id)
                                    currentContent = defaultText
                                    initialContent = defaultText
                                    hasPhysicalFile = false
                                    isCustomized = false
                                    reloadCustomizedMap()
                                    Toast.show(resetSuccessTip, ToastType.SUCCESS)
                                }
                            },
                            enabled = hasPhysicalFile || isCustomized || hasDiffFromDefault,
                            shape = AppShapes.medium,
                            modifier = Modifier.tip("删除外部缓存并回滚至打包内置的出厂默认提示词")
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text(resetBtnText)
                        }

                        Spacer(Modifier.width(spacing.small))

                        // 保存至外部缓存按钮
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val ok = promptManager.savePrompt(selectedMeta.id, currentContent)
                                    if (ok) {
                                        initialContent = currentContent
                                        hasPhysicalFile = true
                                        isCustomized = promptManager.isCustomized(selectedMeta.id)
                                        reloadCustomizedMap()
                                        Toast.show(saveSuccessTip, ToastType.SUCCESS)
                                    } else {
                                        Toast.show("保存提示词失败", ToastType.ERROR)
                                    }
                                }
                            },
                            enabled = isModified || !isCustomized,
                            colors = if (isModified) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            else ButtonDefaults.buttonColors(),
                            shape = AppShapes.medium,
                            modifier = Modifier.tip(saveBtnText)
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text(saveBtnText)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 模板横向流式选择组件：对已修改/已自定义模板实现鲜明视觉高亮与徽章区分
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptInHorizontalFlowRow(
    metas: List<PromptMeta>,
    selectedMeta: PromptMeta,
    customizedMap: Map<String, Boolean>,
    onSelect: (PromptMeta) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        metas.forEach { meta ->
            val isSelected = selectedMeta.id == meta.id
            val isCustomized = customizedMap[meta.id] == true

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isSelected && isCustomized -> MaterialTheme.colorScheme.primary
                    isSelected -> MaterialTheme.colorScheme.secondary
                    isCustomized -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = when {
                        isSelected && isCustomized -> MaterialTheme.colorScheme.primary
                        isSelected -> MaterialTheme.colorScheme.secondary
                        isCustomized -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    }
                ),
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(meta) }
                    .tip(if (isCustomized) "【已自定义修改】${meta.descZh}" else "【出厂预置】${meta.descZh}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCustomized) {
                        // 鲜明的已修改标记小图标
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = meta.displayNameZh,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected || isCustomized) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected && isCustomized -> MaterialTheme.colorScheme.onPrimary
                            isSelected -> MaterialTheme.colorScheme.onSecondary
                            isCustomized -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (isCustomized) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "已修改",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }

}
