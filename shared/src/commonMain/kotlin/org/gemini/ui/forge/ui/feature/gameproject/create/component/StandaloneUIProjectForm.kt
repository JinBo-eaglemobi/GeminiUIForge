package org.gemini.ui.forge.ui.feature.gameproject.create.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_btn_create
import geminiuiforge.composeapp.generated.resources.gp_form_name
import geminiuiforge.composeapp.generated.resources.gp_form_name_placeholder
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.model.ui.UIPage
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 预设分辨率选项数据定义
 */
private data class ResolutionPreset(
    val title: String,
    val subtitle: String,
    val width: Float,
    val height: Float,
    val isPortrait: Boolean
)

/**
 * 独立新建 UI 项目表单组件（直通 UI Studio）。
 * 遵循一文件一 Composable 铁律，具备 Bento 空间网格排版、720×1280 竖屏小屏与全景落盘路径预览。
 */
@Composable
fun StandaloneUIProjectForm(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copyAction = rememberClipboardAction()

    var projectName by remember { mutableStateOf("New_UI_Project") }
    val effectiveName = projectName.trim().ifEmpty { "New_UI_Project" }

    // 预设分辨率列表（涵盖 1920x1080, 1280x720, 1080x1920 以及用户要求的 720x1280）
    val presets = remember {
        listOf(
            ResolutionPreset("1920 × 1080", "横屏 16:9 (FHD)", 1920f, 1080f, false),
            ResolutionPreset("1280 × 720", "横屏 16:9 (HD)", 1280f, 720f, false),
            ResolutionPreset("1080 × 1920", "竖屏 9:16 (FHD)", 1080f, 1920f, true),
            ResolutionPreset("720 × 1280", "竖屏 9:16 (小屏)", 720f, 1280f, true)
        )
    }

    var selectedIndex by remember { mutableStateOf(0) } // 0~3 预设，4 为自定义
    var customWidth by remember { mutableStateOf("1280") }
    var customHeight by remember { mutableStateOf("720") }

    val scope = rememberCoroutineScope()
    val templateRepo = remember { TemplateRepository() }
    var isCheckingDuplicate by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var duplicateProjectName by remember { mutableStateOf("") }
    var existingLoadedProject by remember { mutableStateOf<ProjectState?>(null) }

    // 物理磁盘存储根路径探测
    val storage = remember { LocalFileStorage() }
    var baseDataDir by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            baseDataDir = storage.getDataDir().replace("\\", "/")
        } catch (_: Exception) {
            baseDataDir = "~/.geminiuiforge"
        }
    }
    val physicalDiskPath = if (baseDataDir.isNotEmpty()) "$baseDataDir/templates/$effectiveName" else "~/.geminiuiforge/templates/$effectiveName"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // 卡片 1：项目名称与磁盘落盘路径预览
        Card(
            shape = AppShapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Text(
                    text = "基本信息 (Project Metadata)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text(stringResource(Res.string.gp_form_name)) },
                    placeholder = { Text(stringResource(Res.string.gp_form_name_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(spacing.small),
                    modifier = Modifier.fillMaxWidth()
                )

                // 物理磁盘保存路径预览条
                Surface(
                    shape = RoundedCornerShape(spacing.extraSmall),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text(
                                text = "落盘路径: $physicalDiskPath",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { copyAction(physicalDiskPath, "已复制物理落盘路径") },
                            modifier = Modifier.size(28.dp).tip("复制完整存储路径")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 卡片 2：画布初始分辨率矩阵
        Card(
            shape = AppShapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "画布分辨率 (Canvas Resolution)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Bento 风格分辨率卡片网格
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    presets.forEachIndexed { index, preset ->
                        val isSelected = selectedIndex == index
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface

                        Surface(
                            shape = RoundedCornerShape(spacing.small),
                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                            color = bgColor,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(spacing.small))
                                .clickable { selectedIndex = index }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = spacing.small, horizontal = spacing.extraSmall),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // 比例微缩图标框
                                Box(
                                    modifier = Modifier
                                        .width(if (preset.isPortrait) 16.dp else 24.dp)
                                        .height(if (preset.isPortrait) 24.dp else 16.dp)
                                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                )
                                Spacer(Modifier.height(spacing.extraSmall))
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 自定义尺寸卡片
                    val isCustomSelected = selectedIndex == 4
                    Surface(
                        shape = RoundedCornerShape(spacing.small),
                        border = BorderStroke(
                            if (isCustomSelected) 1.5.dp else 1.dp,
                            if (isCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        color = if (isCustomSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(spacing.small))
                            .clickable { selectedIndex = 4 }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = spacing.small, horizontal = spacing.extraSmall),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(spacing.extraSmall))
                            Text(
                                text = "自定义",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "自由指定",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 自定义分辨率展开输入框
                AnimatedVisibility(
                    visible = selectedIndex == 4,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = spacing.extraSmall),
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customWidth,
                            onValueChange = { customWidth = it.filter { ch -> ch.isDigit() } },
                            label = { Text("画布宽度 (px)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(spacing.small),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "×",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = customHeight,
                            onValueChange = { customHeight = it.filter { ch -> ch.isDigit() } },
                            label = { Text("画布高度 (px)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(spacing.small),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        // 创建并进入工作室按钮（居中显示）
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    val finalWidth: Float
                    val finalHeight: Float
                    if (selectedIndex < 4) {
                        val p = presets[selectedIndex]
                        finalWidth = p.width
                        finalHeight = p.height
                    } else {
                        finalWidth = customWidth.toFloatOrNull() ?: 1280f
                        finalHeight = customHeight.toFloatOrNull() ?: 720f
                    }

                    scope.launch {
                        isCheckingDuplicate = true
                        try {
                            val sanitized = templateRepo.sanitizeName(effectiveName)
                            val existing = templateRepo.getTemplateByName(effectiveName)
                            val dirExists = existing != null || TemplateFile("templates/$sanitized").exists()

                            if (dirExists) {
                                // 发现同名项目：拦截并弹出决策弹窗
                                existingLoadedProject = existing
                                duplicateProjectName = effectiveName
                                showDuplicateDialog = true
                            } else {
                                // 不存在同名项目：正常创建并持久化，直接进入 UI Studio
                                val newProject = ProjectState(
                                    projectId = effectiveName,
                                    pages = listOf(
                                        UIPage(
                                            id = "page_main",
                                            nameStr = "MainPage",
                                            width = finalWidth,
                                            height = finalHeight,
                                            blocks = emptyList()
                                        )
                                    )
                                )

                                // 1. 物理持久化落盘至本地磁盘 (~/.geminiuiforge/templates/<effectiveName>/template.json)
                                appViewModel.saveProject(effectiveName, newProject)
                                // 2. 将新建工程绑定至内存并直接路由导航进入 UI Studio
                                appViewModel.loadProject(effectiveName, newProject)
                                appViewModel.navigateTo(AppScreen.UI_STUDIO)
                                Toast.show("成功创建、持久化并进入 UI Studio", type = ToastType.SUCCESS)
                            }
                        } finally {
                            isCheckingDuplicate = false
                        }
                    }
                },
                enabled = !isCheckingDuplicate,
                shape = RoundedCornerShape(spacing.small),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 380.dp)
                    .height(48.dp)
                    .tip(stringResource(Res.string.gp_btn_create))
            ) {
                if (isCheckingDuplicate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(spacing.small))
                    Text(
                        text = "正在检测项目名称...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(spacing.small))
                    Text(
                        text = "${stringResource(Res.string.gp_btn_create)} UI Studio 工作台",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // 项目同名冲突确认与决策弹窗
        if (showDuplicateDialog) {
            DuplicateProjectDialog(
                projectName = duplicateProjectName,
                onOpenExisting = {
                    showDuplicateDialog = false
                    scope.launch {
                        val projToLoad = existingLoadedProject ?: templateRepo.getTemplateByName(duplicateProjectName)
                        if (projToLoad != null) {
                            appViewModel.loadProject(duplicateProjectName, projToLoad)
                            appViewModel.navigateTo(AppScreen.UI_STUDIO)
                            Toast.show("已打开已有项目：$duplicateProjectName", type = ToastType.SUCCESS)
                        } else {
                            // 极罕见情况：目录存在但 template.json 为空，重新构建基础工程进入
                            val finalWidth = if (selectedIndex < 4) presets[selectedIndex].width else (customWidth.toFloatOrNull() ?: 1280f)
                            val finalHeight = if (selectedIndex < 4) presets[selectedIndex].height else (customHeight.toFloatOrNull() ?: 720f)
                            val fallbackProj = ProjectState(
                                projectId = duplicateProjectName,
                                pages = listOf(UIPage(id = "page_main", nameStr = "MainPage", width = finalWidth, height = finalHeight, blocks = emptyList()))
                            )
                            appViewModel.saveProject(duplicateProjectName, fallbackProj)
                            appViewModel.loadProject(duplicateProjectName, fallbackProj)
                            appViewModel.navigateTo(AppScreen.UI_STUDIO)
                            Toast.show("已修复并打开已有项目：$duplicateProjectName", type = ToastType.SUCCESS)
                        }
                    }
                },
                onRenameOrDismiss = {
                    showDuplicateDialog = false
                }
            )
        }
    }
}
