package org.gemini.ui.forge.ui.feature.analysis

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.formatTimestamp
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.CloudAssetManager
import org.gemini.ui.forge.service.ConfigManager
import org.gemini.ui.forge.ui.dialog.CloudAssetDialog
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.rememberImagePicker
import org.jetbrains.compose.resources.stringResource

@Composable
fun TemplateGeneratorScreen(
    onNavigateBack: () -> Unit,
    onTemplateSaved: (String, ProjectState) -> Unit,
    apiKey: String,
    configManager: ConfigManager = remember { ConfigManager() },
    cloudAssetManager: CloudAssetManager = remember {
        CloudAssetManager(ConfigManager())
    },
    aiService: AIGenerationService = remember { AIGenerationService(cloudAssetManager) },
    templateRepo: TemplateRepository = remember { TemplateRepository() },
    maxRetries: Int = 3
) {
    val coroutineScope = rememberCoroutineScope()
    var inputUris by remember { mutableStateOf("") }
    var templateName by remember { mutableStateOf("") }
    var generatedState by remember { mutableStateOf<ProjectState?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }
    val logs = remember { mutableStateListOf<String>() }
    var streamedJson by remember { mutableStateOf("") }
    var showAssetManager by remember { mutableStateOf(false) } // 新增状态：控制资产管理器显示

    if (showAssetManager) {
        CloudAssetDialog(
            cloudAssetManager = cloudAssetManager,
            onDismiss = { showAssetManager = false }
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
            enabled = !isAnalyzing
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 新增：模板名称输入框，提前到分析之前
        OutlinedTextField(
            value = templateName,
            onValueChange = { templateName = it },
            label = { Text("模板名称 (留空则自动根据图片命名)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isAnalyzing
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = { imagePicker() },
                enabled = !isAnalyzing,
                shape = AppShapes.medium
            ) {
                Text(stringResource(Res.string.template_gen_pick_local))
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isAnalyzing = true
                        generatedState = null
                        saveStatus = ""
                        streamedJson = ""
                        logs.clear()
                        
                        // 1. 确定最终模板名称
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

                        logs.add("[${formatTimestamp(getCurrentTimeMillis())}] 🔍 正在预验证图片资源有效性...")
                        val allImageUris = inputUris.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        
                        // 执行预验证 (URL 连通性、Base64 格式、本地文件存在性)
                        val validationError = aiService.validateImageUris(allImageUris)
                        if (validationError != null) {
                            logs.add("[${formatTimestamp(getCurrentTimeMillis())}] ❌ 验证失败: $validationError")
                            saveStatus = "验证失败，请修正路径后重试。"
                            isAnalyzing = false
                            return@launch
                        }

                        logs.add("[${formatTimestamp(getCurrentTimeMillis())}] 🚀 准备分析图片并创建模板 [$finalTemplateName]...")
                        
                        try {
                            // 2. 执行 AI 分析
                            val resultState = aiService.analyzeImagesForTemplate(
                                imageUris = inputUris.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                apiKey = apiKey,
                                maxRetries = maxRetries,
                                onLog = { logMsg -> logs.add("[${formatTimestamp(getCurrentTimeMillis())}] $logMsg") },
                                onChunk = { chunk -> streamedJson += chunk }
                            )
                            
                            generatedState = resultState
                            logs.add("[${formatTimestamp(getCurrentTimeMillis())}] ✅ 分析成功！")

                            // 3. 立即自动保存到本地缓存目录 (包含全量参考图归档)
                            logs.add("[${formatTimestamp(getCurrentTimeMillis())}] 💾 正在自动归档参考图并保存模板...")
                            val allImageUris = inputUris.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                            
                            val stateToSave = resultState.copy(
                                createdAt = getCurrentTimeMillis(),
                                referenceImages = allImageUris // 传递全量路径供 Repository 执行搬迁
                            )
                            
                            templateRepo.saveTemplate(finalTemplateName, stateToSave)
                            
                            // 4. 全部任务成功后，触发自动跳转至编辑器界面
                            onTemplateSaved(finalTemplateName, stateToSave)
                            
                        } catch (e: Exception) {
                            saveStatus = "分析失败: ${e.message}"
                            logs.add("[${formatTimestamp(getCurrentTimeMillis())}] ❌ 发生错误: ${e.message}")
                        }
                        isAnalyzing = false
                    }
                },
                enabled = inputUris.isNotBlank() && !isAnalyzing,
                shape = AppShapes.medium,
                modifier = Modifier.widthIn(min = 120.dp) // 设定最小宽度防止跳变
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.template_gen_analyze))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.small
        ) {
            if (logs.isEmpty() && !isAnalyzing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无运行日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                SelectionContainer {
                    val listState = rememberLazyListState()

                    // 智能滚动到最新日志 (仅在新增一行日志时判断，避免流数据高频触发导致强制拉到底部)
                    LaunchedEffect(logs.size) {
                        if (logs.isNotEmpty()) {
                            // 允许一定的容错范围：如果用户在最底部或只差几个元素，或者是第一条日志，则自动滚动
                            val isAtBottom = !listState.canScrollForward || 
                                             (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 2
                            
                            if (isAtBottom || logs.size == 1) {
                                // 滚动到最后一个元素 (包括可能存在的数据流 item)
                                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            }
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

                            // 关键：实时展示接收中的 JSON 数据流 (精简版)
                            if (isAnalyzing && streamedJson.isNotEmpty() && generatedState == null) {
                                item {
                                    Text(
                                        text = "[数据流同步中] 长度: ${streamedJson.length}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        // 恢复物理滚动条
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(scrollState = listState)
                        )
                    }
                }
            }
        }

        if (saveStatus.isNotBlank()) {
            Text(saveStatus, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
