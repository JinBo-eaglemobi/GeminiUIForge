package org.gemini.ui.forge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import geminiuiforge.composeapp.generated.resources.Res
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.domain.ProjectState
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.TemplateRepository
import org.jetbrains.compose.resources.stringResource

import org.gemini.ui.forge.utils.rememberImagePicker

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.text.selection.SelectionContainer

import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.formatTimestamp

@Composable
fun TemplateGeneratorScreen(
    onNavigateBack: () -> Unit,
    onTemplateSaved: (String, ProjectState) -> Unit,
    aiService: AIGenerationService = remember { AIGenerationService() },
    templateRepo: TemplateRepository = remember { TemplateRepository() },
    apiKey: String
) {
    val coroutineScope = rememberCoroutineScope()
    var inputUris by remember { mutableStateOf("") }
    var templateName by remember { mutableStateOf("") }
    var generatedState by remember { mutableStateOf<ProjectState?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }
    val logs = remember { mutableStateListOf<String>() }
    var streamedJson by remember { mutableStateOf("") }

    val imagePicker = rememberImagePicker { uris ->
        if (uris.isNotEmpty()) {
            val current = inputUris.trim()
            val newUris = uris.joinToString(",")
            inputUris = if (current.isEmpty()) newUris else "$current,$newUris"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = stringResource(Res.string.template_gen_title),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputUris,
            onValueChange = { inputUris = it },
            label = { Text(stringResource(Res.string.template_gen_input_hint)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { imagePicker() }) {
                Text("选择本地图片")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isAnalyzing = true
                        generatedState = null
                        saveStatus = ""
                        streamedJson = ""
                        logs.clear()
                        logs.add("[${org.gemini.ui.forge.formatTimestamp(getCurrentTimeMillis())}] 🚀 开始准备上传图片并分析...")
                        try {
                            generatedState = aiService.analyzeImagesForTemplate(
                                imageUris = inputUris.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                apiKey = apiKey,
                                onLog = { logMsg -> logs.add("[${org.gemini.ui.forge.formatTimestamp(getCurrentTimeMillis())}] $logMsg") },
                                onChunk = { chunk -> streamedJson += chunk }
                            )
                            logs.add("[${org.gemini.ui.forge.formatTimestamp(getCurrentTimeMillis())}] ✅ 分析成功并已生成数据模型！")
                        } catch (e: Exception) {
                            saveStatus = "分析失败: ${e.message}"
                            logs.add("[${org.gemini.ui.forge.formatTimestamp(getCurrentTimeMillis())}] ❌ 发生错误: ${e.message}")
                        }
                        isAnalyzing = false
                    }
                },
                enabled = inputUris.isNotBlank() && !isAnalyzing
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(Res.string.template_gen_analyze))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var selectedTabIndex by remember { mutableStateOf(0) }
        val tabLog = stringResource(Res.string.tab_log)
        val tabResult = stringResource(Res.string.tab_result)
        val tabs = listOf(tabLog, tabResult)

        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val currentTab = tabs.getOrNull(selectedTabIndex)
        
        if (currentTab == tabLog) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            ) {
                if (logs.isEmpty() && !isAnalyzing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    SelectionContainer {
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                        
                        // Smart auto-scroll logic
                        LaunchedEffect(logs.size) {
                            if (logs.isNotEmpty()) {
                                // 允许 5 像素的误差来判断是否在底部，增强触控环境下的稳定性
                                val isAtBottom = !listState.canScrollForward || listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == logs.size - 2
                                if (isAtBottom || logs.size == 1) {
                                    listState.animateScrollToItem(logs.size - 1)
                                }
                            }
                        }
                        
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp) // 紧凑排列，模拟连续文本
                        ) {
                            items(logs) { log ->
                                Text(
                                    text = log, 
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace, // 控制台等宽字体
                                        lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                                    ), 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else if (currentTab == tabResult) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                val displayJson = if (generatedState != null) {
                    val jsonFormat = Json { prettyPrint = true }
                    jsonFormat.encodeToString(ProjectState.serializer(), generatedState!!)
                } else {
                    streamedJson
                }

                if (displayJson.isNotEmpty()) {
                    SelectionContainer {
                        val scrollState = rememberScrollState()
                        Text(
                            text = displayJson,
                            modifier = Modifier.padding(8.dp).verticalScroll(scrollState),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("等待 AI 返回数据流...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (generatedState != null) {
            OutlinedTextField(
                value = templateName,
                onValueChange = { templateName = it },
                label = { Text(stringResource(Res.string.template_name_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (templateName.isNotBlank() && generatedState != null) {
                        try {
                            val stateToSave = generatedState!!.copy(createdAt = getCurrentTimeMillis())
                            templateRepo.saveTemplate(templateName, stateToSave)
                            saveStatus = "保存成功！正在进入编辑模式..."
                            onTemplateSaved(templateName, stateToSave)
                        } catch (e: Exception) {
                            saveStatus = "保存错误: ${e.message}"
                        }
                    }
                },
                enabled = templateName.isNotBlank()
            ) {
                Text(stringResource(Res.string.template_gen_save))
            }
        }

        if (saveStatus.isNotBlank()) {
            Text(saveStatus, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}