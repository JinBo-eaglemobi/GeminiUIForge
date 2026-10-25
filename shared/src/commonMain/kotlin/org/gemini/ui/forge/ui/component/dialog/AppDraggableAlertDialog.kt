package org.gemini.ui.forge.ui.component.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 通用可拖拽现代化警告/确认对话框。
 *
 * 100% 对齐 Material 3 [androidx.compose.material3.AlertDialog] 标准参数签名，
 * 内置 [AppDraggableDialog] 视口安全防丢夹具、标题栏拖拽抓手与双击居中复位能力，
 * 可直接平替系统原生的 AlertDialog。
 *
 * @param onDismissRequest 关闭对话框请求
 * @param confirmButton 确认操作按钮
 * @param modifier 弹窗修饰符
 * @param dismissButton 取消/关闭按钮插槽
 * @param icon 顶部图标插槽
 * @param title 标题插槽
 * @param text 正文内容插槽
 * @param shape 弹窗外框圆角
 * @param containerColor 背景色
 * @param tonalElevation 拟物海拔高度
 * @param properties 弹窗平台属性
 */
@Composable
fun AppDraggableAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: Dp = 6.dp,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
) {
    val draggableState = rememberDraggableDialogState()

    AppDraggableDialog(
        onDismissRequest = onDismissRequest,
        state = draggableState,
        properties = properties
    ) {
        Surface(
            modifier = modifier.widthIn(min = 320.dp),
            shape = shape,
            color = containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 1. 标题栏区域（挂载拖拽抓手与双击居中复位）
                if (title != null || icon != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .draggableDialogHandle(draggableState),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (icon != null) {
                                Box(modifier = Modifier.padding(end = 12.dp)) {
                                    icon()
                                }
                            }
                            if (title != null) {
                                ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
                                    title()
                                }
                            }
                        }

                        // 右上角轻量快速关闭按钮
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 2. 正文内容区域
                if (text != null) {
                    ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                        text()
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 3. 底部操作按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}
