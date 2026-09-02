package org.gemini.ui.forge.ui.dialog.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.dialog.ai.component.BilingualPromptEditor
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 以图生图 / 以图抠图生成资源对话框（标准规范版）。
 *
 * 核心特性：
 * 1. 上部：展示当前模块设置好的局部参考底图（高清等比预览）；
 * 2. 中部：接入全局统一的标准 [BilingualPromptEditor] 双语提示词编辑器，支持中英双语切换与 AI 优化，
 *    以当前输入框中的最新文案为准发起生图；
 * 3. 下部：提供"生成透明背景 (PNG)"与"优先云端抠图"的配置勾选项；
 * 4. 底部：取消与开始以图生图按钮（带加载指示与全量 Tooltip）。
 *
 * @param block 当前选中的 UI 模块
 * @param referenceImage 该模块专用的局部参考图文件对象
 * @param initialTransparent 初始是否勾选透明背景 (PNG)
 * @param initialCloudRemoval 初始是否勾选优先云端抠图
 * @param isGenerating 是否正在生图执行中
 * @param onDismiss 取消/关闭回调
 * @param onStartGen 开始以图生图回调（回传最新 promptZh, promptEn, isTransparent, prioritizeCloud）
 */
@Composable
fun ImageToImageGenDialog(
    block: UIBlock,
    referenceImage: TemplateFile,
    initialTransparent: Boolean = true,
    initialCloudRemoval: Boolean = false,
    isGenerating: Boolean = false,
    onDismiss: () -> Unit,
    onStartGen: (promptZh: String, promptEn: String, isTransparent: Boolean, prioritizeCloud: Boolean) -> Unit
) {
    // 允许用户现场临时修改 Prompt（以弹窗内当前输入为准）
    var promptZh by remember(block.userPromptZh) { mutableStateOf(block.userPromptZh) }
    var promptEn by remember(block.userPromptEn) { mutableStateOf(block.userPromptEn) }
    var isOptimizing by remember { mutableStateOf(false) }

    // 透明图与云端抠图选项
    var isTransparent by remember(initialTransparent) { mutableStateOf(initialTransparent) }
    var prioritizeCloud by remember(initialCloudRemoval) { mutableStateOf(initialCloudRemoval) }

    val coroutineScope = rememberCoroutineScope()
    val spacing = LocalAppSpacing.current

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(LocalAppSpacing.current.dialogConfigWidth + 120.dp)
                .wrapContentHeight(),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(spacing.large)) {
                // 1. 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(spacing.small))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.img2img_dialog_title, block.id),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(Res.string.img2img_dialog_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isGenerating,
                        modifier = Modifier.tip(stringResource(Res.string.btn_close_dialog))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.btn_close_dialog))
                    }
                }

                Spacer(Modifier.height(spacing.medium))

                // 2. 上部：局部参考图预览区域
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = AppShapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(spacing.small),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = referenceImage.getAbsolutePath(),
                            contentDescription = "Reference Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(AppShapes.small),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(Modifier.height(spacing.medium))

                // 3. 中部：全局统一标准双语 AI 提示词编辑器 (BilingualPromptEditor)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = AppShapes.medium
                ) {
                    BilingualPromptEditor(
                        promptZh = promptZh,
                        promptEn = promptEn,
                        onPromptConfirmed = { newZh, newEn ->
                            promptZh = newZh
                            promptEn = newEn
                        },
                        onOptimizeRequested = { _, _ ->
                            coroutineScope.launch {
                                isOptimizing = true
                                isOptimizing = false
                            }
                        },
                        isOptimizing = isOptimizing,
                        showExplicitConfirmButton = false,
                        title = "以图生图/抠图引导文案 (以输入框实时内容为准)",
                        minFieldHeight = 80.dp,
                        modifier = Modifier.padding(spacing.medium)
                    )
                }

                Spacer(Modifier.height(spacing.small))

                // 4. 透明背景 (PNG) 与云端抠图选项组
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = AppShapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(spacing.small)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isTransparent,
                                onCheckedChange = { isTransparent = it },
                                enabled = !isGenerating
                            )
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text("生成透明背景 (PNG)", style = MaterialTheme.typography.bodySmall)
                        }
                        if (isTransparent) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp)) {
                                Checkbox(
                                    checked = prioritizeCloud,
                                    onCheckedChange = { prioritizeCloud = it },
                                    enabled = !isGenerating
                                )
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text("优先云端高精抠图", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(spacing.large))

                // 5. 底部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isGenerating,
                        shape = AppShapes.medium,
                        modifier = Modifier.tip(stringResource(Res.string.dialog_action_cancel))
                    ) {
                        Text(stringResource(Res.string.dialog_action_cancel))
                    }
                    Spacer(Modifier.width(spacing.small))
                    Button(
                        onClick = { onStartGen(promptZh, promptEn, isTransparent, prioritizeCloud) },
                        enabled = !isGenerating && (promptZh.isNotBlank() || promptEn.isNotBlank() || block.userPrompt.isNotBlank()),
                        shape = AppShapes.medium,
                        modifier = Modifier.tip(stringResource(Res.string.img2img_start_btn))
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text("正在以图生图中...")
                        } else {
                            Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(spacing.extraSmall))
                            Text(stringResource(Res.string.img2img_start_btn))
                        }
                    }
                }
            }
        }
    }
}
