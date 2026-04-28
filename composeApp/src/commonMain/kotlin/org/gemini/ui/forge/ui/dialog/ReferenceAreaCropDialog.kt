package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.ui.component.ImageAreaSelector
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 设置区域参考图对话框
 */
@Composable
fun ReferenceAreaCropDialog(
    imageUri: TemplateFile?,
    pageWidth: Float,
    pageHeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (SerialRect) -> Unit
) {
    var selectedRect by remember { mutableStateOf<SerialRect?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("设置区域参考图", style = MaterialTheme.typography.headlineSmall)
                Text("框选当前模块在原图上的对应区域，生成时将仅以该区域作为参考图。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                ImageAreaSelector(
                    imageUri = imageUri,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black.copy(alpha = 0.05f))
                        .clip(RoundedCornerShape(8.dp)),
                    selectionColor = Color.Green,
                    onSelectionChange = { selectedRect = it }
                )

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { selectedRect?.let { onConfirm(it) } },
                        enabled = selectedRect != null,
                        shape = AppShapes.medium
                    ) { Text("保存局部参考") }
                }
            }
        }
    }
}
