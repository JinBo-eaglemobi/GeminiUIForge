package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.gemini.ui.forge.getCurrentTimeMillis

/**
 * 转轴符号管理器对话框
 * 用于管理转轴（Reel）组件中的符号元素（SYMBOL）集。
 * 允许用户添加、编辑、删除符号元素，并触发 AI 生成符号资产。
 */
@Composable
fun ReelSymbolManagerDialog(
    props: BlockProperties.ReelProperties,
    onDismiss: () -> Unit,
    onPropertiesChanged: (BlockProperties) -> Unit,
    viewModel: ProjectWorkspaceViewModel,
    apiKey: String,
    state: ProjectWorkspaceState,
    onShowHistory: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    // 控制是否显示新增/编辑符号元素的二级对话框
    var showAddItemDialog by remember { mutableStateOf(false) }
    // 当前正在编辑的元素；为 null 时表示当前处于“新增”模式
    var editingItem by remember { mutableStateOf<UIBlock?>(null) }

    // 在对话框中临时缓存的中英文描述，用于输入绑定
    var newItemPromptZh by remember { mutableStateOf("") }
    var newItemPromptEn by remember { mutableStateOf("") }

    // 提示词 Tab 状态：0 = 中文, 1 = 英文
    var promptTab by remember { mutableStateOf(0) }

    // 用于预览大图的状态
    var previewImageUri by remember { mutableStateOf<String?>(null) }
    // 用于删除确认的状态
    var itemToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    // 用于提示词优化的状态
    var isOptimizing by remember { mutableStateOf(false) }
    // 用于生图确认的状态
    var showGenConfirmDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            // 标题栏：包含标题文字和新增按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("转轴符号集管理器")
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        editingItem = null
             = null
                        newItemPromptZh = ""
          PromptEn = ""
                        promptTab = 0
                        showAddItemDialog = true
                    },
                    modifier = Modifier.tip("添加新符号")
                ) {
                    Icon(Icons.Default.AddCircle, "添加", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            // 内容区域：若没有符号则显示提示文字
            if (props.items.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无符号元素，请点击右上角添加",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                // 使用滚动列表展示已有的符号
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 600.dp)
                ) {
                    items(props.items.size) { index ->
                        val item = props.items[index]
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = AppShapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 符号预览图：展示当前生成的资产图片，若无则显示缺省图标
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .clip(AppShapes.extraSmall)
                                        .background(Color.Black.copy(alpha = 0.05f))
                                        .clickable(enabled = item.currentImageUri != null) {
                                            previewImageUri = item.currentImageUri?.getAbsolutePath()
                                        }
                                        .tip(if (item.currentImageUri != null) "点击预览大图" else null)
                                ) {
                                    if (item.currentImageUri != null) {
                                        AsyncImage(
                                            model = item.currentImageUri.getAbsolutePath(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.ImageNotSupported,
                                            null,
                                            Modifier.size(24.dp).align(Alignment.Center),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                // 文本描述：展示中文名称和英文 Prompt
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.userPromptZh.ifBlank { item.id },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                    
                                    Text(
                    tem.userPromptEn.ifBlank { "No English Prompt" },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 2,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                // 操作按钮组：编辑、生成、删除

                                // 编辑按钮：点击打开二级对话框修改文本
                                IconButton(onClick = {
                                    editingItem = item
                                    newItemPromptZh = item.userPromptZh
                                    newItemPromptEn = item.userPromptEn
                                    promptTab =
                                        if (item.userPromptEn.isNotBlank() && item.userPromptZh.isBlank()) 1 else 0
                                    showAddItemDialog = true
                                }, modifier = Modifier.size(32.dp).tip("编辑符号及生成资源")) {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(Modifier.width(4.dp))

                                // 生成按钮：点击弹出确认并生成
                                IconButton(onClick = {
                                    editingItem = item
                                    newItemPromptZh = item.userPromptZh
                                    newItemPromptEn = item.userPromptEn
                                    promptTab =
                                        if (item.userPromptEn.isNotBlank() && item.userPromptZh.isBlank()) 1 else 0
                                    showGenConfirmDialog = true
                                }, modifier = Modifier.size(32.dp).tip("快速触发 AI 生图")) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Spacer(Modifier.width(4.dp))

                                // 历史生成记录按钮
                                IconButton(onClick = {
                                    onShowHistory(item.id)
                                }, modifier = Modifier.size(32.dp).tip("查看历史候选图")) {
                                    Icon(
                                        Icons.Default.History,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(Modifier.width(4.dp))

                                // 删除按钮：将该符号从集合中移除
                                IconButton(onClick = {
                                    itemToDeleteIndex = index
                                }, modifier = Modifier.size(32.dp).tip("删除此符号")) {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                                     }
                            }     }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("完成") }
        }
    )

    // � }
    )

    // 大图预览对话框
    if (previewImageUri != null) {
        Dialog(onDismissRequest = { previewImageUri = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).aspectRatio(1f),
                shape = AppShapes.medium,
                color = Color.Black.copy(alpha = 0.9f)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = previewImageUri,
                        contentDescription = "大图预览",
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { previewImageUri = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (itemToDeleteIndex != null) {
        AppConfirmDialog(
            title = "确认删除",
            message = "确定要删除这个符号吗？此操作不可撤销。",
            isDestructive = true,
            onConfirm = {
                val newItems = props.items.toMutableList().apply {
                    removeAt(itemToDeleteIndex!!)
                }
                onPropertiesChanged(props.copy(items = newItems))
                itemToDeleteIndex = null
            }
        )
    }

    // 新增/编辑符号信息的二级对话框
    if (showAddItemDialog) {
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            modifier = Modifier.fillMaxWidth(0.85f),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(if (editingItem == null) "新增符号元素 (SYMBOL)" else "编辑符号元素")
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showAddItemDialog = false }) {
                        Icon(Icons.Default.Close, "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TabRow(selectedTabIndex = promptTab, containerColor = Color.Transparent) {
                        Tab(selected = promptTab == 0, onClick = { promptTab = 0 }) {
                            Text("中文描述 (Chinese)", modifier = Modifier.padding(12.dp))
                        }
                        Tab(selected = promptTab == 1, onClick = { promptTab = 1 }) {
                            Text("英文 Prompt (English)", modifier = Modifier.padding(12.dp))
                        }
                    }

                    // 预定义优化按钮，移入 trailingIcon 槽位以防遮挡文字
                    val optimizeButton: @Composable (() -> Unit)? = if (newItemPromptZh.isNotBlank() || newItemPromptEn.isNotBlank()) {
                        {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isOptimizing = true
                                        try {
                                            val source = newItemPromptEn.ifBlank { newItemPromptZh }
                                            val optimized = viewModel.assetGen.optimizePrompt(source, apiKey)
                                            newItemPromptEn = optimized
                                            promptTab = 1 // 优化完自动切换到英文 Tab 查看结果
                                        } catch (e: Exception) {
                                            // 可以在此处添加 Toast 或日志提示
                                        } finally {
                                    = false
                                        }
                                    }
                                },
                                modifier = Modifier.tip("AI 自动优化提示词 (将生成英文 Prompt)"),
                                enabled = !isOptimizing
                            ) {
                                if (isOptimizing) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Default.Translate,
                                        null,
                                        Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else null

                    if (promptTab == 0) {
                        SelectAllOutlinedTextField(
                            value = newItemPromptZh,
                            onValueChange = { newItemPromptZh = it },
                            label = { Text("请输入中文描述，可作为提示词基础或占位名称") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                            trailingIcon = optimizeButton
                        )
                    } else {
                        SelectAllOutlinedTextField(
                            value = newItemPromptEn,
                            onValueChange = { newItemPromptEn = it },
                            label = { Text("请输入 English Prompt，用于精准的 AI 生图") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                            trailingIcon = optimizeButton
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 仅保存按钮
                    Button(onClick = {
                        val targetItem = if (editingItem != null) {
                            editingItem!!.copy(
                                userPromptZh = newItemPromptZh,
                                userPromptEn = newItemPromptEn
                            )
                        } else {
                            UIBlock(
                                id = "sym_${getCurrentTimeMillis()}",
                                type = UIBlockType.SYMBOL,
                                bounds = SerialRect(0f, 0f, 100f, 100f),
                                userPromptZh = newItemPromptZh,
                                userPromptEn = newItemPromptEn
                            )
                        }

                        val newItems = if (editingItem != null) {
                            props.items.map { if (it.id == editingItem!!.id) targetItem else it }
                        } else {
                            props.items + targetItem
                        }

                        onPropertiesChanged(props.copy(items = newItems))
                        showAddItemDialog = false
                    }) {
                        Text("保存信息")
                    }

                    // 生成并保存按钮
                    FilledTonalButton(
                        onClick = { showGenConfirmDialog = true },
                        modifier = Modifier.tip("保存并使用当前 Tab 语言立即生图")
             �图")
                    ) {
                        Icon(Icme, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存并生成")
                    }
                }
            }
        )
    }

    // 生图确认对话� 生图确认对enConfirmDialog) {
        val langText = if (promptTab == 0) "【中文】" else "【英文】"
        AppConfirmDialog(
            title = "确认开始 AI 生成",
            message = "将使用当前选中的 $langText 提示词触发 AI 生成任务。生成的图片将作为此符号的候选资产。是否继续？",
            confirmText = "开始生成",
            onConfirm = {
                // 1. 先保存当前数据（确保生图使用的是最新的 Prompt）
                val targetItem = if (editingItem != null) {
                    editingItem!!.copy(
                        userPromptZh = newItemPromptZh,
                        userPromptEn = newItemPromptEn
                    )
                } else {
                    UIBlock(
                        id = "sym_${getCurrentTimeMillis()}",
                        type = UIBlockType.SYMBOL,
                        bounds = SerialRect(0f, 0f, 100f, 100f),
                        userPromptZh = newItemPromptZh,
                        userPromptEn = newItemPromptEn
                    )
                }

                val newItems = if (editingItem != null) {
                    props.items.map { if (it.id == editingItem!!.id) targetItem else it }
                } else {
                    props.items + targetItem
                }

                onPropertiesChanged(props.copy(items = newItems))

                // 2. 触发生成逻辑，强制使用当前选中 Tab 的语言
                val finalPromptText = if (promptTab == 0) targetItem.userPromptZh else targetItem.userPromptEn
                val safePrompt = if (finalPromptText.isNotBlank()) finalPromptText else targetItem.fullPrompt

                viewModel.assetManager.selectReelItem(targetItem.id)
                viewModel.assetGen.onRequestGeneration(apiKey, "${UIBlockType.SYMBOL.defaultPrompt}, $safePrompt")

                showGenConfirmDialog = false
                showAddItemDialog = false
            },
            onDismiss = { showGenConfirmDialog = false }
        )
    }
}
        )
    }
}

}
