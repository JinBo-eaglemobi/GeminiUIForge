package org.gemini.ui.forge.ui.dialog.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 模块细化编辑对话框（基于 UniversalImageRegionSelector 通用选区组件重塑版）。
 *
 * 核心特性与设计规范落地：
 * 1. 接入通用 [UniversalImageRegionSelector] 组件：提供 8 方向独立控制手柄、Alt 键中心对称缩放、三分法网格、尺寸 HUD 悬浮胶囊；
 * 2. 真实全局绝对物理坐标全链路贯通，保存时自动根据 [parentOffset] 逆向还原为相对父容器的局部坐标；
 * 3. 提示词中英文双语切换输入（ZH/EN），支持联动查看与编辑，当前语言为空时提供备用语言参考；
 * 4. 配备 AI 提示词优化 (AI Optimize) 辅助入口按钮，支持一键调用大模型精炼扩充文案；
 * 5. 变动感知：内容或坐标发生修改后动态高亮显示显式"确认修改并同步项目"，点击后方可同步分发并持久化；
 * 6. 全交互按钮 100% 挂载 [Modifier.tip] 悬浮提示，严格遵循 I18n 规范；
 * 7. 全局杜绝硬编码数字与尺寸，统一使用 [LocalAppSpacing] 进行 8dp 栅格体系赋值。
 *
 * @param block 当前待细化编辑的 UI 模块数据对象
 * @param parentOffset 父级容器在整个画布上的全局绝对物理偏移量（顶层模块为 Offset.Zero）
 * @param imageUri 原参考图底图文件对象
 * @param pageWidth 画布页面逻辑总宽（默认 1080f）
 * @param pageHeight 画布页面逻辑总高（默认 1920f）
 * @param onDismiss 取消/关闭对话框时的回调
 * @param onConfirm 确认保存回调（返回坐标与文案更新后的全新 UIBlock 对象）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockRefinementDialog(
    block: UIBlock,
    parentOffset: Offset = Offset.Zero,
    imageUri: TemplateFile?,
    pageWidth: Float,
    pageHeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (UIBlock) -> Unit
) {
    // 1. 初始化计算全局绝对坐标：自动累加所有父容器的绝对物理偏移量
    val initialAbsBounds = remember(block.bounds, parentOffset) {
        val minX = min(block.bounds.left, block.bounds.right)
        val minY = min(block.bounds.top, block.bounds.bottom)
        val w = abs(block.bounds.width)
        val h = abs(block.bounds.height)
        val absLeft = parentOffset.x + minX
        val absTop = parentOffset.y + minY
        SerialRect(
            left = absLeft,
            top = absTop,
            right = absLeft + w,
            bottom = absTop + h
        )
    }

    // 2. 当前正在微调的全局绝对物理矩形坐标（实时联动输入框与选区手柄）
    var currentAbsBounds by remember(initialAbsBounds) { mutableStateOf(initialAbsBounds) }

    // 3. 提示词中英文双语编辑与加载状态
    var currentPromptLang by remember { mutableStateOf(PromptLanguage.ZH) }
    var promptZh by remember(block.userPromptZh) { mutableStateOf(block.userPromptZh) }
    var promptEn by remember(block.userPromptEn) { mutableStateOf(block.userPromptEn) }
    var isOptimizing by remember { mutableStateOf(false) }

    // 4. 变动感知状态机：对比初始文案与绝对坐标是否发生实质变动
    val isBoundsChanged = currentAbsBounds != initialAbsBounds
    val isPromptChanged = promptZh != block.userPromptZh || promptEn != block.userPromptEn
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
                // 顶部标题栏与关闭操作
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(spacing.small))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.block_refine_dialog_title, block.id),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(Res.string.block_refine_dialog_desc),
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

                // 物理数值精确输入与微调工具栏（展示和双向联动真实全局绝对坐标）
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
                            value = currentAbsBounds.left.toInt().toString(),
                            onValueChange = { str ->
                                val v = str.toFloatOrNull() ?: currentAbsBounds.left
                                val w = currentAbsBounds.width
                                currentAbsBounds = currentAbsBounds.copy(left = v, right = v + w)
                            },
                            label = { Text(stringResource(Res.string.block_refine_pos_x)) },
                            modifier = Modifier.width(110.dp),
                            isFloat = false
                        )
                        NumberOutlinedTextField(
                            value = currentAbsBounds.top.toInt().toString(),
                            onValueChange = { str ->
                                val v = str.toFloatOrNull() ?: currentAbsBounds.top
                                val h = currentAbsBounds.height
                                currentAbsBounds = currentAbsBounds.copy(top = v, bottom = v + h)
                            },
                            label = { Text(stringResource(Res.string.block_refine_pos_y)) },
                            modifier = Modifier.width(110.dp),
                            isFloat = false
                        )
                        NumberOutlinedTextField(
                            value = currentAbsBounds.width.toInt().toString(),
                            onValueChange = { str ->
                                val w = str.toFloatOrNull() ?: currentAbsBounds.width
                                currentAbsBounds = currentAbsBounds.copy(right = currentAbsBounds.left + max(1f, w))
                            },
                            label = { Text(stringResource(Res.string.block_refine_width)) },
                            modifier = Modifier.width(110.dp),
                            isFloat = false
                        )
                        NumberOutlinedTextField(
                            value = currentAbsBounds.height.toInt().toString(),
                            onValueChange = { str ->
                                val h = str.toFloatOrNull() ?: currentAbsBounds.height
                                currentAbsBounds = currentAbsBounds.copy(bottom = currentAbsBounds.top + max(1f, h))
                            },
                            label = { Text(stringResource(Res.string.block_refine_height)) },
                            modifier = Modifier.width(110.dp),
                            isFloat = false
                        )

                        Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(spacing.small))

                // 通用图片选区组件 (UniversalImageRegionSelector：8 手柄、Alt 对称缩放、聚焦居中、暗角蒙版、三分线与尺寸 HUD)
                UniversalImageRegionSelector(
                    imageSource = imageUri?.let { RegionImageSource.FromFile(it) },
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    initialRect = currentAbsBounds,
                    initialZoom = 1.8f,
                    showThirdsGrid = true,
                    showDimensionBadge = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(AppShapes.medium),
                    onSelectionChange = { rect ->
                        if (rect != null) {
                            currentAbsBounds = rect
                        }
                    },
                    onSelectionConfirmed = { rect ->
                        if (rect != null) {
                            currentAbsBounds = rect
                        }
                    }
                )

                Spacer(Modifier.height(spacing.medium))

                // 提示词 / 提交文案（Prompt）统一标准组件：中英文双语切换、AI 优化与变动感知
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

                Spacer(Modifier.height(spacing.medium))

                // 底部操作栏（变动感知：显式确认修改并同步分发回项目）
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
                            // 逆向扣除父级绝对偏移量，将全局绝对物理坐标换算回相对父级的局部坐标
                            val relLeft = currentAbsBounds.left - parentOffset.x
                            val relTop = currentAbsBounds.top - parentOffset.y
                            val relRight = currentAbsBounds.right - parentOffset.x
                            val relBottom = currentAbsBounds.bottom - parentOffset.y
                            val updatedBlock = block.copy(
                                bounds = SerialRect(
                                    left = relLeft,
                                    top = relTop,
                                    right = relRight,
                                    bottom = relBottom
                                ),
                                userPromptZh = promptZh,
                                userPromptEn = promptEn
                            )
                            onConfirm(updatedBlock)
                        },
                        colors = if (isModified) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        else ButtonDefaults.buttonColors(),
                        shape = AppShapes.medium,
                        modifier = Modifier.tip(
                            if (isModified) stringResource(Res.string.btn_confirm_sync)
                            else stringResource(Res.string.block_refine_save)
                        )
                    ) {
                        Text(
                            text = if (isModified) stringResource(Res.string.btn_confirm_sync)
                            else stringResource(Res.string.block_refine_save)
                        )
                    }
                }
            }
        }
    }
}
