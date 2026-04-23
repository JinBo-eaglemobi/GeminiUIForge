package org.gemini.ui.forge.ui.feature.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.formatTimestamp
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.app.AppGlobalState
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.service.*
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.dialog.AITaskProgressDialog
import org.gemini.ui.forge.ui.dialog.CloudAssetDialog
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.rememberImagePicker
import org.jetbrains.compose.resources.stringResource

@Composable
fun TemplateGeneratorScreen(
    onTemplateSaved: (String, ProjectState) -> Unit,
    globalState: AppGlobalState,
    cloudAssetManager: CloudAssetManager,
    configManager: ConfigManager,
    templateRepo: TemplateRepository
) {
    val aiService: AIGenerationService = remember { AIGenerationService(cloudAssetManager, configManager) }
    val coroutineScope = rememberCoroutineScope()
    
    var inputUris by remember { mutableStateOf("") }
    var templateName by remember { mutableStateOf("") }
    var showAssetManager by remember { mutableStateOf(false) }

    // 使用 AITask 统一管理任务状态
    var currentTask by remember { mutableStateOf<AITask<ProjectState>?>(null) }
    val taskStatus by (currentTask?.status ?: MutableStateFlow(AITaskStatus.IDLE)).collectAsState()
    val taskResult by (currentTask?.result ?: MutableStateFlow<ProjectState?>(null)).collectAsState()
    
    var showLogs by remember { mutableStateOf(true) }
    var streamedJson by remember { mutableStateOf("") }

    // 当任务成功时的自动保存逻辑
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
                
                // 先执行归档，获取 TemplateFile 列表
                val archivedFiles = templateRepo.archiveExternalImages(finalTemplateName, allImageUris)
                
                // 更新 ProjectState 中的参考图和页面关联图
                val stateToSave = resultState.copy(
                    createdAt = getCurrentTimeMillis(),
                    referenceImages = archivedFiles,
                    pages = resultState.pages.mapIndexed { index, page ->
                        page.copy(sourceImageUri = archivedFiles.getOrNull(index) ?: archivedFiles.firstOrNull())
                    }
                )
                
                templateRepo.saveTemplate(finalTemplateName, stateToSave)
                onTemplateSaved(finalTemplateName, stateToSave)
            } catch (e: Exception) {
                currentTask?.log("❌ 保存失败: ${e.message}")
            }
        }
    }

    if (showAssetManager) {
        CloudAssetDialog(
            cloudAssetManager = cloudAssetManager,
            onDismiss = { showAssetManager = false }
        )
    }

    if (currentTask != null && taskStatus != AITaskStatus.IDLE) {
        AITaskProgressDialog(
            title = "Gemini 智能 UI 分析中...",
            logs = currentTask!!.logs,
            isProcessing = taskStatus == AITaskStatus.RUNNING,
            isLogVisible = showLogs,
            onToggleLogVisibility = { showLogs = !showLogs },
            onActionClick = { 
                if (taskStatus == AITaskStatus.RUNNING) {
                    currentTask?.cancel() 
                } else {
                    currentTask = null
                }
            },
            onDismiss = { if (taskStatus != AITaskStatus.RUNNING) currentTask = null }
        )
    }

    val imagePicker = rememberImagePicker { uris ->
        if (uris.isNotEmpty()) {
            val current = inputUris.trim()
            val newUris = uris.joinToString("\n")
            inputUris = if (current.isEmpty()) newUris else "$current\n$newUris"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.template_gen_title),
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(
                onClick = { showAssetManager = true },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Cloud, contentDescription = "Cloud Assets")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputUris,
            onValueChange = { inputUris = it },
            label = { Text(stringResource(Res.string.template_gen_input_hint)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            enabled = taskStatus != AITaskStatus.RUNNING
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = templateName,
            onValueChange = { templateName = it },
            label = { Text("模板名称 (留空则自动根据图片命名)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = taskStatus != AITaskStatus.RUNNING
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = { imagePicker() },
                enabled = taskStatus != AITaskStatus.RUNNING,
                shape = AppShapes.medium
            ) {
                Text(stringResource(Res.string.template_gen_pick_local))
            }

            Button(
                onClick = {
                    val task = aiService.createTask<ProjectState>("UI 模板分析", coroutineScope)
                    currentTask = task
                    streamedJson = ""
                    
                    task.execute {
                        val finalName = if (templateName.isBlank()) "自动命名" else templateName
                        log("🔍 正在预验证图片资源有效性...")
                        
                        val allImageUris = inputUris.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        val validationError = aiService.validateImageUris(allImageUris)
                        if (validationError != null) {
                            throw Exception("验证失败: $validationError")
                        }

                        log("🚀 准备分析图片并创建模板 [$finalName]...")
                        updateProgress(0.2f)

                        // 执行 AI 分析
                        val result = aiService.analyzeImagesForTemplate(
                            imageUris = allImageUris,
                            apiKey = globalState.effectiveApiKey,
                            maxRetries = globalState.maxRetries,
                            onLog = { log(it) },
                            onChunk = { streamedJson += it }
                        )
                        
                        updateProgress(1.0f)
                        result
                    }
                },
                enabled = inputUris.isNotBlank() && (taskStatus != AITaskStatus.RUNNING),
                shape = AppShapes.medium,
                modifier = Modifier.widthIn(min = 120.dp)
            ) {
                Text(stringResource(Res.string.template_gen_analyze))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 下方日志区域可以作为历史记录显示，或者在 AITask 结束后仍能查看
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.small
        ) {
            val logs = currentTask?.logs ?: remember { mutableStateListOf<String>() }
            
            if (logs.isEmpty() && taskStatus == AITaskStatus.IDLE) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("等待任务启动...", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            items(logs) { log ->
                                Text(
                                    text = log, 
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
