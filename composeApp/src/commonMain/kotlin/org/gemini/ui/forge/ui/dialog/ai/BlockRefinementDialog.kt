package org.gemini.ui.forge.ui.dialog.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.dialog.ai.component.BilingualPromptEditor
import org.gemini.ui.forge.ui.component.selector.RegionImageSource
import org.gemini.ui.forge.ui.component.selector.UniversalImageRegionSelector
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.ui.component.ToastType
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 模块细化与参考区域通用编辑对话框
 *
 * @param block 当前待细化编辑/设置参考图的 UI 模块数据对象
 * @param isReferenceAreaOnly 是否仅为设置参考区域模式（为 true 时动态切换标题并隐藏提示词编辑区）
 * @param parentOffset 父级容器在整个画布上的全局绝对物理偏移量（顶层模块为 Offset.Zero）
 * @param imageUri 原参考图底图文件对象
 * @param pageWidth 画布页面逻辑总宽（默认 1080f）
 * @param pageHeight 画布页面逻辑总高（默认 1920f）
 * @param onDismiss 取消/关闭对话框时的回调
 * @param onConfirm 确认保存回调（返回坐标与文案更新后的全新 UIBlock 对象）
 */
@Composable
fun BlockRefinementDialog(
    block: UIBlock,
    isReferenceAreaOnly: Boolean = false,
    parentOffset: Offset = Offset.Zero,
    imageUri: TemplateFile?,
    pageWidth: Float,
    pageHeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (UIBlock) -> Unit
) {
    // 1. 无参直接获取全局绝对坐标（模块属性自推导）
    val initialAbsBounds = remember(block) {
        block.absoluteBounds
    }

    // 2. 当前正在微调的全局绝对物理矩形坐标（支持可空，清除后允许重新划选）
    var currentAbsBounds by remember(initialAbsBounds) { mutableStateOf<SerialRect?>(initialAbsBounds) }

    // 3. 提示词中英文双语编辑与加载状态
    var promptZh by remember(block.userPromptZh) { mutableStateOf(block.userPromptZh) }
    var promptEn by remember(block.userPromptEn) { mutableStateOf(block.userPromptEn) }
    var isOptimizing by remember { mutableStateOf(false) }

    // 4. 变动感知状态机
    val isBoundsChanged = currentAbsBounds != initialAbsBounds
    val isPromptChanged = !isReferenceAreaOnly && (promptZh != block.userPromptZh || promptEn != block.userPromptEn)
    val isModified = isBoundsChanged || isPromptChanged

    val coroutineScope = rememberCoroutineScope()
    val spacing = LocalAppSpacing.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.94f),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
                // 顶部标题栏与关闭操作（根据模式动态改变标题）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isReferenceAreaOnly) Icons.Default.CropRotate else Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(spacing.small))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isReferenceAreaOnly) "设置模块参考区域 - ${block.id}" else stringResource(Res.string.block_refine_dialog_title, block.id),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isReferenceAreaOnly) "在底图上框选截取该模块专用的局部参考底图（确认后自动持久化保存）" else stringResource(Res.string.block_refine_dialog_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.tip(stringResource(Res.string.btn_close_dialog))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.btn_close_dialog))
                    }
                }

                Spacer(Modifier.height(spacing.small))

                // 物理数值精确输入与微调工具栏（高度 52dp 严格锁定对齐，杜绝错位变形）
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = AppShapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        NumberOutlinedTextField(
                            value = currentAbsBounds?.left?.toInt()?.toString() ?: "0",
                            onValueChange = { str ->
                                val cur = currentAbsBounds ?: SerialRect(0f, 0f, 100f, 100f)
                                val v = str.toFloatOrNull() ?: cur.left
                                val w = cur.width
                                currentAbsBounds = cur.copy(left = v, right = v + w)
                            },
                            label = { Text(stringResource(Res.string.block_refine_pos_x)) },
                            modifier = Modifier.width(110.dp).height(52.dp),
                            isFloat = false
                        )
                        NumberOutlinedTextField(
                            value = currentAbsBounds?.top?.toInt()?.toString() ?: "0",
                            onValueChange = { str ->
                                val cur = currentAbsBounds ?: SerialRect(0f, 0f, 100f, 100f)
                                val v = str.toFloatOrNull() ?: cur.top
                                val h = cur.height
                                currentAbsBounds = cur.copy(top = v, bottom = v + h)
                            },
                            label = { Text(stringResource(Res.string.block_refine_pos_y)) },
                            modifier = Modifier.width(110.dp).height(52.dp),
                            isFloat = false
                        )
                        NumberOutlinedTextField(
                            value = currentAbsBounds?.width?.toInt()?.toString() ?: "0",
                            onValueChange = { str ->
                                val cur = currentAbsBounds ?: SerialRect(0f, 0f, 100f, 100f)
                                val w = str.toFloatOrNull() ?: cur.width
                                currentAbsBounds = cur.copy(right = cur.left + max(1f, w))
                            },
                            label = { Text(stringResource(Res.string.block_refine_width)) },
                            modifier = Modifier.width(110.dp).height(52.dp),
                            isFloat = false
                        )
                        NumberOutlinedTextField(
                            value = currentAbsBounds?.height?.toInt()?.toString() ?: "0",
                            onValueChange = { str ->
                                val cur = currentAbsBounds ?: SerialRect(0f, 0f, 100f, 100f)
                                val h = str.toFloatOrNull() ?: cur.height
                                currentAbsBounds = cur.copy(bottom = cur.top + max(1f, h))
                            },
                            label = { Text(stringResource(Res.string.block_refine_height)) },
                            modifier = Modifier.width(110.dp).height(52.dp),
                            isFloat = false
                        )

                        Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(spacing.small))

                // 通用图片选区组件 (UniversalImageRegionSelector：全量 Px 绝对对齐、光标定点缩放、缩放 HUD 控制岛)
                UniversalImageRegionSelector(
                    imageSource = imageUri?.let { RegionImageSource.FromFile(it) },
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    initialRect = currentAbsBounds,
                    initialZoom = 1.0f,
                    showThirdsGrid = true,
                    showDimensionBadge = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(AppShapes.medium),
                    onSelectionChange = { rect ->
                        currentAbsBounds = rect
                    },
                    onSelectionConfirmed = { rect ->
                        currentAbsBounds = rect
                    }
                )

                // 提示词 / 提交文案（Prompt）统一标准组件：仅在细化模式下展示，参考区域模式下彻底隐藏
                if (!isReferenceAreaOnly) {
                    Spacer(Modifier.height(spacing.medium))
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
                            modifier = Modifier.padding(spacing.medium)
                        )
                    }
                }

                Spacer(Modifier.height(spacing.medium))

                // 底部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = AppShapes.medium,
                        modifier = Modifier.tip(stringResource(Res.string.dialog_action_cancel))
                    ) {
                        Text(stringResource(Res.string.dialog_action_cancel))
                    }
                    Spacer(Modifier.width(spacing.small))
                    Button(
                        onClick = {
                            val bounds = currentAbsBounds
                            if (bounds == null) {
                                Toast.show("请先在底图上框选模块范围", ToastType.INFO)
                                return@Button
                            }
                            // 坐标解耦与逆向换算处理
                            val finalBounds = if (isReferenceAreaOnly) {
                                // ★ 设置参考区域模式：裁剪整张页面原图必须使用全景绝对逻辑矩形，严禁逆向扣除 parentOffset
                                bounds
                            } else {
                                // ★ 常规模块微调模式：由模块内建无参推导逆向换算回直接父级的局部相对坐标
                                block.toLocalBounds(bounds)
                            }
                            val updatedBlock = block.copy(
                                bounds = finalBounds,
                                userPromptZh = promptZh,
                                userPromptEn = promptEn
                            )
                            onConfirm(updatedBlock)
                        },
                        colors = if (isModified) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        else ButtonDefaults.buttonColors(),
                        shape = AppShapes.medium,
                        modifier = Modifier.tip(
                            if (isReferenceAreaOnly) "保存当前框选的局部参考图"
                            else if (isModified) stringResource(Res.string.btn_confirm_sync)
                            else stringResource(Res.string.block_refine_save)
                        )
                    ) {
                        Text(
                            text = if (isReferenceAreaOnly) "保存参考区域"
                            else if (isModified) stringResource(Res.string.btn_confirm_sync)
                            else stringResource(Res.string.block_refine_save)
                        )
                    }
                }
            }
        }
    }
}
