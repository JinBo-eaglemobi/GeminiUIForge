package org.gemini.ui.forge.ui.feature.workspace.property.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.ui.ImageScaleConfig
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.calculateOpaqueContentScaleConfig
import org.gemini.ui.forge.utils.fetchImageBytes
import org.jetbrains.compose.resources.stringResource

/**
 * 图片不透明核心主体自适应对齐与微调卡片 (OpaqueAlignCard)
 *
 * 用于已绑定图片资产的模块，提供一键主体识别对齐（发光/透明留白自然溢出）及几何微调。
 *
 * @param block 当前选中的图元模块
 * @param onUpdateScaleConfig 缩放配置更新回调
 * @param modifier 修饰符
 */
@Composable
fun OpaqueAlignCard(
    block: UIBlock,
    onUpdateScaleConfig: (ImageScaleConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val scaleConfig = block.scaleConfig
    val coroutineScope = rememberCoroutineScope()
    var isAnalyzing by remember { mutableStateOf(false) }

    // 字符串资源
    val titleText = stringResource(Res.string.opaque_align_card_title)
    val descText = stringResource(Res.string.opaque_align_card_desc)
    val btnAutoText = stringResource(Res.string.opaque_align_btn_auto)
    val btnAutoTipText = stringResource(Res.string.opaque_align_btn_auto_tip)
    val btnResetText = stringResource(Res.string.opaque_align_btn_reset)
    val lockRatioText = stringResource(Res.string.opaque_align_lock_ratio)
    val successToastPattern = stringResource(Res.string.opaque_align_toast_success)
    val failToastText = stringResource(Res.string.opaque_align_toast_failed)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (scaleConfig.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = AppShapes.medium
            ),
        shape = AppShapes.medium,
        color = if (scaleConfig.enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. 顶部 Header 行：开关与标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (scaleConfig.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = descText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                Switch(
                    checked = scaleConfig.enabled,
                    onCheckedChange = { isChecked ->
                        onUpdateScaleConfig(scaleConfig.copy(enabled = isChecked))
                    },
                    modifier = Modifier.tip("开启或关闭不透明核心主体自适应对齐")
                )
            }

            // 2. 开启时展开的微调与操作面板
            if (scaleConfig.enabled) {
                HorizontalDivider(
                    Modifier,
                    thickness = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // 一键自动识别按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val uri = block.currentImageUri?.getAbsolutePath() ?: return@Button
                            isAnalyzing = true
                            coroutineScope.launch {
                                val bytes = withContext(Dispatchers.Default) { fetchImageBytes(uri) }
                                if (bytes != null && bytes.isNotEmpty()) {
                                    val newConfig = withContext(Dispatchers.Default) {
                                        calculateOpaqueContentScaleConfig(
                                            imageBytes = bytes,
                                            targetWidth = block.bounds.width,
                                            targetHeight = block.bounds.height,
                                            lockAspectRatio = scaleConfig.lockAspectRatio
                                        )
                                    }
                                    if (newConfig != null) {
                                        onUpdateScaleConfig(newConfig)
                                        val box = newConfig.opaqueBounds
                                        val w = box?.width?.toInt() ?: 0
                                        val h = box?.height?.toInt() ?: 0
                                        Toast.show(
                                            message = successToastPattern.replace("%1\$d", w.toString()).replace("%2\$d", h.toString()),
                                            type = ToastType.SUCCESS
                                        )
                                    } else {
                                        Toast.show(failToastText, type = ToastType.INFO)
                                    }
                                } else {
                                    Toast.show("读取图片资产失败", type = ToastType.ERROR)
                                }
                                isAnalyzing = false
                            }
                        },
                        enabled = !isAnalyzing && block.currentImageUri != null,
                        modifier = Modifier.weight(1f).height(36.dp).tip(btnAutoTipText),
                        shape = AppShapes.small,
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(btnAutoText, style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            onUpdateScaleConfig(ImageScaleConfig(enabled = true))
                        },
                        modifier = Modifier.height(36.dp).tip("重置缩放比例为 1.0 与原点对齐"),
                        shape = AppShapes.small,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(btnResetText, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // 缩放控制滑杆 (0.2x ~ 3.0x)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "缩放比例 (Scale):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${((scaleConfig.scaleX * 100).toInt()) / 100f}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = scaleConfig.scaleX.coerceIn(0.2f, 3.0f),
                        onValueChange = { newScale ->
                            val updated = if (scaleConfig.lockAspectRatio) {
                                scaleConfig.copy(scaleX = newScale, scaleY = newScale)
                            } else {
                                scaleConfig.copy(scaleX = newScale)
                            }
                            onUpdateScaleConfig(updated)
                        },
                        valueRange = 0.2f..3.0f,
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )
                }

                // X / Y 偏置控制
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Offset X
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Offset X:", style = MaterialTheme.typography.labelSmall)
                            Text("${scaleConfig.offsetX.toInt()}px", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = scaleConfig.offsetX.coerceIn(-200f, 200f),
                            onValueChange = { onUpdateScaleConfig(scaleConfig.copy(offsetX = it)) },
                            valueRange = -200f..200f,
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }

                    // Offset Y
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Offset Y:", style = MaterialTheme.typography.labelSmall)
                            Text("${scaleConfig.offsetY.toInt()}px", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = scaleConfig.offsetY.coerceIn(-200f, 200f),
                            onValueChange = { onUpdateScaleConfig(scaleConfig.copy(offsetY = it)) },
                            valueRange = -200f..200f,
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }
                }

                // 底部等比锁定与识别信息行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.tip("调整缩放时强制保持宽高同比例")
                    ) {
                        Checkbox(
                            checked = scaleConfig.lockAspectRatio,
                            onCheckedChange = { locked ->
                                onUpdateScaleConfig(scaleConfig.copy(lockAspectRatio = locked))
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(lockRatioText, style = MaterialTheme.typography.labelSmall)
                    }

                    if (scaleConfig.opaqueBounds != null) {
                        val b = scaleConfig.opaqueBounds
                        Text(
                            text = "主体: ${b.width.toInt()}x${b.height.toInt()}px",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
