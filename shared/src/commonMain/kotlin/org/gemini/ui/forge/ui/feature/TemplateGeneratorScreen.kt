package org.gemini.ui.forge.ui.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.service.detection.onnx.OnnxModelService
import org.gemini.ui.forge.service.*
import org.gemini.ui.forge.state.app.AppGlobalState
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.asset.CloudAssetDialog
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.rememberFilePicker
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 视觉多模态 AI 模板生成主界面
 * 内嵌式流式进度展示、单份终端日志与全态按钮管控，彻底消除模态弹窗遮挡与重复日志
 */
@Composable
fun TemplateGeneratorScreen(
    appViewModel: AppViewModel,
    globalState: AppGlobalState,
    templateRepo: TemplateRepository
) {
    val coroutineScope = rememberCoroutineScope()
    val spacing = LocalAppSpacing.current

    var inputUris by remember { mutableStateOf("") }
    var templateName by remember { mutableStateOf("") }
    var showAssetManager by remember { mutableStateOf(false) }
    var autoCropAndBindRef by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(org.gemini.ui.forge.service.detection.DetectionCategory.ONLINE_AI) }
    var selectedOfflineEngine by remember { mutableStateOf(org.gemini.ui.forge.service.detection.DetectionEngineMode.CLASSIC_CV) }

    // 使用 AITask 统一管理任务状态
    var currentTask by remember { mutableStateOf<AITask<ProjectState>?>(null) }
    val taskStatus by (currentTask?.status ?: MutableStateFlow(AITaskStatus.IDLE)).collectAsState()
    val taskResult by (currentTask?.result ?: MutableStateFlow<ProjectState?>(null)).collectAsState()
    val aiProgress by (currentTask?.currentStatus ?: MutableStateFlow("")).collectAsState()
    val taskProgressVal by (currentTask?.progress ?: MutableStateFlow(0f)).collectAsState()

    var streamedJson by remember { mutableStateOf("") }

    // 当任务成功时的自动归档与落盘逻辑
    LaunchedEffect(taskStatus, taskResult) {
        if (taskStatus == AITaskStatus.SUCCESS && taskResult != null) {
            val resultState = taskResult!!
            val finalTemplateName = if (templateName.isBlank()) {
                inputUris.split("\n")
                    .firstOrNull { it.isNotBlank() }
                    ?.substringAfterLast("/")
                    ?.substringAfterLast("\\")
                    ?.substringBeforeLast(".")
                    ?.ifBlank { "NewTemplate_${getCurrentTimeMillis()}" } ?: "NewTemplate"
            } else {
                templateName
            }

            try {
                currentTask?.log("💾 正在自动归档参考图并保存模板...")
                val allImageUris = inputUris.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

                // 执行物理归档，获取 TemplateFile 列表
                val archivedFiles = templateRepo.archiveExternalImages(finalTemplateName, allImageUris)

                // 递归进行离线边缘吸附校准并绑定页面 sourceImageUri 背景
                val firstRefPath = archivedFiles.firstOrNull()?.getAbsolutePath()
                val refBytes = if (!firstRefPath.isNullOrBlank()) org.gemini.ui.forge.utils.readLocalFileBytes(firstRefPath) else null

                val boundState = resultState.copy(
                    createdAt = getCurrentTimeMillis(),
                    styleReferenceUri = archivedFiles.firstOrNull(),
                    referenceImages = archivedFiles,
                    pages = resultState.pages.mapIndexed { index, page ->
                        page.copy(sourceImageUri = archivedFiles.getOrNull(index) ?: archivedFiles.firstOrNull())
                    }
                )

                val calibratedPages = if (refBytes != null && refBytes.isNotEmpty()) {
                    boundState.pages.map { page ->
                        suspend fun calibrateBlock(block: org.gemini.ui.forge.model.ui.UIBlock): org.gemini.ui.forge.model.ui.UIBlock {
                            val absBounds = block.toAbsoluteBounds()
                            val snapped = org.gemini.ui.forge.utils.SmartEdgeSnapper.snapBounds(
                                imageBytes = refBytes,
                                logicalBounds = absBounds,
                                canvasWidth = page.width,
                                canvasHeight = page.height
                            )
                            val calibratedBounds = if (snapped != null) {
                                block.toLocalBounds(snapped.logicalRect)
                            } else {
                                block.bounds
                            }

                            val newRefImage = if (autoCropAndBindRef) {
                                val cropBytes = org.gemini.ui.forge.utils.SmartEdgeSnapper.cropSnappedComponent(
                                    imageBytes = refBytes,
                                    logicalBounds = absBounds,
                                    canvasWidth = page.width,
                                    canvasHeight = page.height
                                )
                                if (cropBytes != null) {
                                    templateRepo.saveBlockResource(
                                        templateName = finalTemplateName,
                                        blockId = block.id,
                                        fileNamePrefix = "ref_snap",
                                        bytes = cropBytes,
                                        isPng = true
                                    )
                                } else block.referenceImage
                            } else {
                                block.referenceImage
                            }

                            val calibratedChildren = block.children.map { calibrateBlock(it) }
                            return block.copy(bounds = calibratedBounds, referenceImage = newRefImage, children = calibratedChildren)
                        }
                        page.copy(blocks = page.blocks.map { calibrateBlock(it) })
                    }
                } else {
                    boundState.pages
                }

                val finalSavedState = boundState.copy(pages = calibratedPages)

                templateRepo.saveTemplate(finalTemplateName, finalSavedState)
                appViewModel.loadProject(finalTemplateName, finalSavedState)
                appViewModel.navigateTo(AppScreen.PROJECT_WORKSPACE)
            } catch (e: Exception) {
                currentTask?.log("❌ 保存模板失败: ${e.message}")
            }
        }
    }

    if (showAssetManager) {
        CloudAssetDialog(
            cloudAssetManager = appViewModel.cloudAssetManager,
            onDismiss = { showAssetManager = false }
        )
    }

    val imagePicker = rememberFilePicker(
        title = "选择本地设计参考图片",
        isFolder = false,
        extensions = listOf("png", "jpg", "jpeg", "webp"),
        onResult = { uri ->
            if (uri != null) {
                val current = inputUris.trim()
                inputUris = if (current.isEmpty()) uri else "$current\n$uri"
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 1. 顶部 Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.template_gen_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "输入设计图路径或链接，调用 Gemini 视觉大模型全景分析生成层级树并自动物理吸附切片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { showAssetManager = true },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.tip("云端媒体资产管理器")
            ) {
                Icon(Icons.Default.Cloud, contentDescription = "Cloud Assets")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 顶层一级板块切换 (在线 AI 生成 vs 离线极速识别)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            org.gemini.ui.forge.service.detection.DetectionCategory.entries.forEach { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    shape = AppShapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(AppShapes.small)
                        .clickable(enabled = taskStatus != AITaskStatus.RUNNING) {
                            selectedCategory = cat
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (cat == org.gemini.ui.forge.service.detection.DetectionCategory.ONLINE_AI) Icons.Default.Cloud else Icons.Default.FlashOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = cat.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 离线板块内的二级子引擎切换器 (传统 CV vs 端侧 AI)
        if (selectedCategory == org.gemini.ui.forge.service.detection.DetectionCategory.OFFLINE_FAST) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = AppShapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("离线识别算法引擎:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        org.gemini.ui.forge.service.detection.DetectionEngineRegistry.getAvailableOfflineDetectorModes().forEach { mode ->
                            val isModeSelected = selectedOfflineEngine == mode
                            FilterChip(
                                selected = isModeSelected,
                                onClick = { selectedOfflineEngine = mode },
                                label = { Text(mode.displayName) },
                                shape = AppShapes.small,
                                enabled = taskStatus != AITaskStatus.RUNNING
                            )
                        }
                    }
                    if (selectedOfflineEngine == org.gemini.ui.forge.service.detection.DetectionEngineMode.ONNX_AI) {
                        Surface(
                            shape = AppShapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val isReady by OnnxModelService.isModelReadyFlow.collectAsState()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isReady) "✅ 端侧神经网络已就绪 (ui_detector.onnx)" else "⚠️ 未检测到本地专属模型 (ui_detector.onnx)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (isReady) "可直接执行端侧目标检测" else "可将针对特定游戏训练的 .onnx 模型放入目录；未放入时自动由传统 CV 引擎无感兜底",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { OnnxModelService.openModelDirectoryInSystemExplorer() },
                                        shape = AppShapes.small,
                                        modifier = Modifier.height(30.dp).tip("在系统文件管理器中打开 models 目录，可离线放入 ui_detector.onnx"),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Folder, null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("打开模型目录", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. 表单输入区 (处理中全部置灰禁用)
        SelectAllOutlinedTextField(
            value = inputUris,
            onValueChange = { inputUris = it },
            label = { Text(stringResource(Res.string.template_gen_input_hint)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            enabled = taskStatus != AITaskStatus.RUNNING,
            shape = AppShapes.medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        SelectAllOutlinedTextField(
            value = templateName,
            onValueChange = { templateName = it },
            label = { Text("模板工程名称 (留空则自动根据图片文件名命名)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = taskStatus != AITaskStatus.RUNNING,
            shape = AppShapes.medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 可选切图绑定开关 (默认不勾选，仅纠偏坐标)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(AppShapes.small).clickable(enabled = taskStatus != AITaskStatus.RUNNING) {
                autoCropAndBindRef = !autoCropAndBindRef
            }.padding(vertical = 2.dp)
        ) {
            Checkbox(
                checked = autoCropAndBindRef,
                onCheckedChange = { autoCropAndBindRef = it },
                enabled = taskStatus != AITaskStatus.RUNNING,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "识图后自动切割参考图保存到本地并绑定到各模块 (默认关闭，仅纠偏物理坐标与范围)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. 操作按钮控制栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { imagePicker() },
                enabled = taskStatus != AITaskStatus.RUNNING,
                shape = AppShapes.medium
            ) {
                Text(stringResource(Res.string.template_gen_pick_local))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 运行态动态显现“中断分析”按钮
                if (taskStatus == AITaskStatus.RUNNING) {
                    OutlinedButton(
                        onClick = { currentTask?.cancel() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = AppShapes.medium,
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("中断分析")
                    }
                }

                // 主分析/重试按钮
                Button(
                    onClick = {
                        val isOnline = selectedCategory == org.gemini.ui.forge.service.detection.DetectionCategory.ONLINE_AI
                        val taskTitle = if (isOnline) "云端 AI 模板分析" else "离线极速模板构建"
                        val task = appViewModel.aiService.createTask<ProjectState>(taskTitle, coroutineScope)
                        currentTask = task
                        streamedJson = ""

                        task.execute {
                            val finalName = if (templateName.isBlank()) "自动命名" else templateName
                            log("🔍 正在预验证参考图片资源有效性...")

                            val allImageUris = inputUris.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                            val validationError = appViewModel.aiService.validateImageUris(allImageUris)
                            if (validationError != null) {
                                throw Exception("图片资源校验未通过: $validationError")
                            }

                            if (isOnline) {
                                log("🚀 正在向 Gemini 视觉大模型提交多模态图元树推理 [$finalName]...")
                                updateProgress(0.2f)

                                var totalChars = 0
                                val result = appViewModel.aiService.analyzeImagesForTemplate(
                                    imageUris = allImageUris,
                                    apiKey = globalState.effectiveApiKey,
                                    maxRetries = globalState.maxRetries,
                                    onLog = { log(it) },
                                    onChunk = { chunk ->
                                        streamedJson += chunk
                                        totalChars += chunk.length
                                        updateStatus("正在流式接收结构化 JSON: $totalChars 字符")
                                    }
                                )

                                updateProgress(1.0f)
                                result
                            } else {
                                val detector = org.gemini.ui.forge.service.detection.DetectionEngineRegistry.getTemplateDetector(selectedOfflineEngine)
                                log("⚡ 正在执行本地离线引擎 [${detector.displayName}] 构建工程 [$finalName]...")
                                updateProgress(0.2f)

                                val firstUri = allImageUris.firstOrNull() ?: throw Exception("请至少输入或选择一张有效的参考图片路径")
                                log("📁 读取本地参考图片物理像素: $firstUri")
                                val imgBytes = org.gemini.ui.forge.utils.readLocalFileBytes(firstUri)
                                    ?: throw Exception("未能读取图片文件二进制内容: $firstUri")

                                updateProgress(0.5f)
                                log("📐 正在执行物理轮廓切分与多层空间几何拓扑包含树推导...")
                                val result = detector.detectTemplate(
                                    imageBytes = imgBytes,
                                    templateName = finalName,
                                    onProgress = { log(it) }
                                )
                                updateProgress(1.0f)
                                log("✅ 离线工程构建完成，耗时 < 200ms！")
                                result
                            }
                        }
                    },
                    enabled = inputUris.isNotBlank() && (taskStatus != AITaskStatus.RUNNING),
                    shape = AppShapes.medium,
                    modifier = Modifier.widthIn(min = 130.dp)
                ) {
                    if (taskStatus == AITaskStatus.RUNNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (selectedCategory == org.gemini.ui.forge.service.detection.DetectionCategory.ONLINE_AI) "正在分析..." else "正在构建...")
                    } else {
                        val isOnline = selectedCategory == org.gemini.ui.forge.service.detection.DetectionCategory.ONLINE_AI
                        Icon(
                            imageVector = if (isOnline) Icons.Default.AutoAwesome else Icons.Default.FlashOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (taskStatus == AITaskStatus.ERROR) "重新尝试"
                            else if (isOnline) stringResource(Res.string.template_gen_analyze)
                            else "一键离线构建"
                        )
                    }
                }
            }
        }

        // 4. 内嵌式实时进度与状态横幅 (完全替代原模态弹窗)
        if (taskStatus != AITaskStatus.IDLE) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = AppShapes.small,
                colors = CardDefaults.cardColors(
                    containerColor = when (taskStatus) {
                        AITaskStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        AITaskStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (taskStatus == AITaskStatus.RUNNING) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = when (taskStatus) {
                                    AITaskStatus.RUNNING -> if (aiProgress.isNotBlank()) aiProgress else "正在分析结构并构建图元..."
                                    AITaskStatus.ERROR -> "❌ 任务执行失败: 查看下方日志了解详情"
                                    AITaskStatus.CANCELLED -> "⚠️ 任务已被用户主动中断"
                                    AITaskStatus.SUCCESS -> "✅ 模板已成功解析归档并绑定参考图背景"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = when (taskStatus) {
                                    AITaskStatus.ERROR -> MaterialTheme.colorScheme.error
                                    AITaskStatus.CANCELLED -> MaterialTheme.colorScheme.tertiary
                                    AITaskStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        if (streamedJson.isNotEmpty() && taskStatus == AITaskStatus.RUNNING) {
                            Text(
                                text = "已接收: ${streamedJson.length} 字符",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (taskStatus == AITaskStatus.RUNNING) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { taskProgressVal.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(AppShapes.small)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. 唯一下方单份终端日志区域 (绝不重复展示)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val logs = currentTask?.logs ?: remember { mutableStateListOf() }
            Text(
                text = "执行终端日志 (${logs.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (logs.isNotEmpty()) {
                IconButton(
                    onClick = { logs.clear() },
                    modifier = Modifier.size(24.dp).tip("清空当前日志")
                ) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(15.dp))
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = AppShapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            val logs = currentTask?.logs ?: remember { mutableStateListOf() }

            if (logs.isEmpty() && taskStatus == AITaskStatus.IDLE) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("等待任务启动...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            } else {
                SelectionContainer {
                    val listState = rememberLazyListState()
                    LaunchedEffect(logs.size) {
                        if (logs.isNotEmpty()) {
                            listState.animateScrollToItem(logs.size - 1)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = when {
                                        log.contains("❌") -> MaterialTheme.colorScheme.error
                                        log.contains("✅") -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        VerticalScrollbarAdapter(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            scrollState = listState
                        )
                    }
                }
            }
        }
    }
}
