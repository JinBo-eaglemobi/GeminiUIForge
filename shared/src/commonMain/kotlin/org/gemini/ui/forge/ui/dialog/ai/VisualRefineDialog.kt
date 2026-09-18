package org.gemini.ui.forge.ui.dialog.ai

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.action_refine_area
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIPage
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.selector.RegionImageSource
import org.gemini.ui.forge.ui.component.selector.UniversalImageRegionSelector
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.ai.component.RefineLogSidePanel
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.calculateMd5
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.utils.getMimeType
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * 视觉框选重塑对话框 (VisualRefineDialog)
 *
 * 现代化左右分栏架构：
 * 1. 左侧主视口：通用原图选区、重塑指令与操作控制；
 * 2. 右侧全高折叠抽屉：多线程终端流水日志 + 2空格标准格式化 Pretty JSON 预览代码块；
 * 3. 生成完成后支持调起左右分栏代码差异比对窗体 (SideBySideDiffInspectorDialog) 进行人工严格审核与替换。
 */
@Composable
fun VisualRefineDialog(
    viewModel: ProjectWorkspaceViewModel,
    state: ProjectWorkspaceState,
    apiKey: String
) {
    val blockId = state.refineTargetId
    val imageUri = state.currentPage?.sourceImageUri
    val pageWidth = state.currentPage?.width ?: 1080f
    val pageHeight = state.currentPage?.height ?: 1920f
    val initialInstruction = if (blockId != null) state.defaultRefineInstructionUpdate else state.defaultRefineInstructionNew

    var instruction by remember { mutableStateOf(initialInstruction) }
    var selectedRect by remember { mutableStateOf<SerialRect?>(null) }
    var useChatContext by remember { mutableStateOf(false) }
    var isSidePanelExpanded by remember { mutableStateOf(true) }
    val spacing = LocalAppSpacing.current
    val coroutineScope = rememberCoroutineScope()

    // 任务执行与状态
    var isExecuting by remember { mutableStateOf(false) }
    var executionJob by remember { mutableStateOf<Job?>(null) }
    var statusText by remember { mutableStateOf("") }
    var progressVal by remember { mutableStateOf(0f) }
    val executionLogs = remember { mutableStateListOf<String>() }

    // 重塑生成的结构实体
    var pendingUpdatedPages by remember { mutableStateOf<List<UIPage>?>(null) }
    var showDiffInspector by remember { mutableStateOf(false) }

    val prettyJson = remember { Json { prettyPrint = true; prettyPrintIndent = "  " } }

    Dialog(
        onDismissRequest = {
            if (!isExecuting) viewModel.hideVisualRefine()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.96f),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
                // ==========================================
                // 1. 左侧主操作区 (自适应宽度，视口开阔)
                // ==========================================
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // 1.1 顶部标题与展开日志按钮
                    val titleSuffix = if (blockId != null) " - 模块: $blockId" else " - 全局区域"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.action_refine_area) + titleSuffix,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (!isSidePanelExpanded) {
                            OutlinedButton(
                                onClick = { isSidePanelExpanded = true },
                                shape = AppShapes.small,
                                modifier = Modifier.height(30.dp).tip("展开右侧执行终端日志与格式化代码审查面板")
                            ) {
                                Icon(Icons.Default.Terminal, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("展开流水日志 (${executionLogs.size})", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(spacing.small))

                    // 1.2 底图尺寸与选区信息条
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), AppShapes.small)
                            .padding(spacing.small),
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("底图尺寸: ${pageWidth.toInt()} x ${pageHeight.toInt()}", style = MaterialTheme.typography.labelMedium)

                        val rect = selectedRect
                        if (rect != null) {
                            val selW = abs(rect.width).toInt()
                            val selH = abs(rect.height).toInt()
                            Text(
                                text = "当前重塑选区: $selW x $selH",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "当前选区: 未选择（在底图上任意拖拽拉出重塑选区）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(Modifier.height(spacing.small))

                    // 1.3 通用图片选区组件 (填满主要可视区域)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        UniversalImageRegionSelector(
                            imageSource = imageUri?.let { RegionImageSource.FromFile(it) },
                            pageWidth = pageWidth,
                            pageHeight = pageHeight,
                            initialRect = selectedRect,
                            showThirdsGrid = true,
                            showDimensionBadge = true,
                            selectionBorderColor = Color(0xFF00E5FF),
                            modifier = Modifier.fillMaxSize().clip(AppShapes.medium),
                            onSelectionChange = { if (!isExecuting) selectedRect = it },
                            onSelectionConfirmed = { if (!isExecuting) selectedRect = it }
                        )
                    }

                    Spacer(Modifier.height(spacing.small))

                    // 1.4 重塑指令输入框
                    SelectAllOutlinedTextField(
                        value = instruction,
                        onValueChange = { instruction = it },
                        label = { Text("重塑指令 (Prompt)") },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        shape = AppShapes.small,
                        enabled = !isExecuting
                    )

                    Spacer(Modifier.height(spacing.small))

                    // 1.5 底部选项与操作按钮栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = useChatContext,
                                onCheckedChange = { useChatContext = it },
                                enabled = !isExecuting
                            )
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text("携带历史上下文 (会话模式)", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), verticalAlignment = Alignment.CenterVertically) {
                            if (isExecuting) {
                                OutlinedButton(
                                    onClick = {
                                        executionJob?.cancel()
                                        isExecuting = false
                                        statusText = "任务已被用户中断"
                                        executionLogs.add("⚠️ 任务已被用户主动中断")
                                    },
                                    shape = AppShapes.medium,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("中断分析")
                                }
                            } else {
                                TextButton(
                                    onClick = { viewModel.hideVisualRefine() },
                                    shape = AppShapes.medium,
                                    modifier = Modifier.tip("取消并退出重塑")
                                ) {
                                    Text("关闭")
                                }
                            }

                            if (pendingUpdatedPages != null && !isExecuting) {
                                Button(
                                    onClick = { showDiffInspector = true },
                                    shape = AppShapes.medium,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.tip("打开左右对称代码审查窗体，比对原代码与生成的新代码段")
                                ) {
                                    Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("审核并应用代码段")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val rect = selectedRect ?: return@Button
                                        val originalImage = imageUri ?: return@Button
                                        isExecuting = true
                                        isSidePanelExpanded = true // 启动时自动展开侧边日志面板
                                        executionLogs.clear()
                                        pendingUpdatedPages = null
                                        statusText = "正在准备裁剪局部切片..."
                                        progressVal = 0.1f

                                        executionJob = coroutineScope.launch {
                                            try {
                                                executionLogs.add("✂️ 正在裁剪目标选区图片...")
                                                val croppedBytes = cropImage(originalImage.getAbsolutePath(), rect, pageWidth, pageHeight)
                                                    ?: throw Exception("选区图片裁剪失败")

                                                progressVal = 0.25f
                                                executionLogs.add("☁️ 正在上传/复用原图资源...")
                                                val originalBytes = originalImage.readBytes() ?: throw Exception("无法读取底图二进制内容")
                                                val fingerprint = originalBytes.calculateMd5()
                                                var originalFileUri = viewModel.cloudAssetManager.assets.value.find {
                                                    it.displayName?.contains(fingerprint) == true && it.state == "ACTIVE"
                                                }?.uri ?: ""

                                                if (originalFileUri.isBlank()) {
                                                    originalFileUri = viewModel.cloudAssetManager.getOrUploadFile(
                                                        originalImage.relativePath.substringAfterLast("/"),
                                                        originalBytes,
                                                        getMimeType(originalImage.getAbsolutePath())
                                                    ) { _, _ -> } ?: ""
                                                }

                                                progressVal = 0.4f
                                                val historyKey = blockId ?: "GLOBAL_REFINE"
                                                val history = if (useChatContext) state.chatHistories[historyKey] ?: emptyList() else emptyList()

                                                statusText = "正在向 Gemini 提交多模态图元重塑请求..."
                                                executionLogs.add("🚀 正在提交多模态图元重塑请求...")

                                                val updatedPages = viewModel.aiService.refineAreaForTemplate(
                                                    originalImageUri = originalFileUri,
                                                    croppedBytes = croppedBytes,
                                                    currentJson = prettyJson.encodeToString(state.project.pages),
                                                    userInstruction = instruction,
                                                    apiKey = apiKey,
                                                    history = history,
                                                    onLog = { msg ->
                                                        executionLogs.add(msg)
                                                        statusText = msg
                                                    }
                                                )

                                                progressVal = 1.0f
                                                statusText = "✅ 重塑成功！可右侧预览或点击审核"
                                                executionLogs.add("🎉 结构重塑完成，生成的格式化 JSON 已就绪！")
                                                pendingUpdatedPages = updatedPages
                                                isExecuting = false
                                            } catch (e: Exception) {
                                                isExecuting = false
                                                statusText = "❌ 重塑失败: ${e.message}"
                                                executionLogs.add("❌ 错误: ${e.message}")
                                            }
                                        }
                                    },
                                    enabled = selectedRect != null && !isExecuting,
                                    shape = AppShapes.medium,
                                    modifier = Modifier.tip("开始执行 AI 局部视觉重塑")
                                ) {
                                    if (isExecuting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(Modifier.width(6.dp))
                                        Text("正在分析重塑...")
                                    } else {
                                        Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("开始重塑")
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. 右侧全高可折叠日志与格式化代码抽屉
                // ==========================================
                AnimatedVisibility(
                    visible = isSidePanelExpanded,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.width(spacing.medium))

                        val previewJson = remember(pendingUpdatedPages, blockId) {
                            pendingUpdatedPages?.let { pages ->
                                val target = if (blockId != null) pages.flatMap { it.blocks }.findBlockById(blockId) else null
                                if (target != null) {
                                    try {
                                        prettyJson.encodeToString(UIBlock.serializer(), target)
                                    } catch (_: Exception) {
                                        prettyJson.encodeToString(pages)
                                    }
                                } else {
                                    prettyJson.encodeToString(pages)
                                }
                            }
                        }

                        RefineLogSidePanel(
                            isExecuting = isExecuting,
                            progress = progressVal,
                            statusText = statusText,
                            logs = executionLogs,
                            previewJson = previewJson,
                            targetBlockId = blockId,
                            onCollapse = { isSidePanelExpanded = false },
                            onClearLogs = { executionLogs.clear() },
                            onOpenDiffInspector = { showDiffInspector = true }
                        )
                    }
                }
            }
        }
    }

    // 左右分栏代码差异比对审核弹窗
    if (showDiffInspector && pendingUpdatedPages != null) {
        val originalFullJson = remember(state.project.pages) { prettyJson.encodeToString(state.project.pages) }
        val newFullJson = remember(pendingUpdatedPages) { prettyJson.encodeToString(pendingUpdatedPages) }

        val originalSegmentJson = remember(blockId, state.currentPage) {
            val target = state.currentPage?.blocks?.findBlockById(blockId ?: "")
            if (target != null) prettyJson.encodeToString(UIBlock.serializer(), target)
            else originalFullJson
        }

        val newSegmentJson = remember(blockId, pendingUpdatedPages) {
            val target = pendingUpdatedPages?.flatMap { it.blocks }?.findBlockById(blockId ?: "")
            if (target != null) prettyJson.encodeToString(UIBlock.serializer(), target)
            else newFullJson
        }

        SideBySideDiffInspectorDialog(
            originalSegmentJson = originalSegmentJson,
            newSegmentJson = newSegmentJson,
            originalFullJson = originalFullJson,
            newFullJson = newFullJson,
            targetBlockId = blockId,
            onConfirm = {
                val updated = pendingUpdatedPages ?: return@SideBySideDiffInspectorDialog
                val historyKey = blockId ?: "GLOBAL_REFINE"
                viewModel.layoutEditor.applyRefinedPages(updated, historyKey, instruction)
                showDiffInspector = false
                viewModel.hideVisualRefine()
            },
            onDismiss = {
                showDiffInspector = false
            }
        )
    }
}
