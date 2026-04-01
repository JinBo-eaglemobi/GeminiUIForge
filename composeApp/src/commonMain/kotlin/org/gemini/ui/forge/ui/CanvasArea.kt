package org.gemini.ui.forge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

@Composable
fun CanvasArea(
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    onBlockClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val logicalWidth = 1080f
        val logicalHeight = 1920f
        
        val scaleX = maxWidth.value / logicalWidth
        val scaleY = maxHeight.value / logicalHeight
        val scale = min(scaleX, scaleY) * 0.9f
        
        val offsetX = (maxWidth.value - logicalWidth * scale) / 2
        val offsetY = (maxHeight.value - logicalHeight * scale) / 2

        Box(modifier = Modifier.fillMaxSize()) {
            blocks.forEach { block ->
                val isSelected = block.id == selectedBlockId
                
                Box(
                    modifier = Modifier
                        .offset(
                            x = (offsetX + block.bounds.left * scale).dp,
                            y = (offsetY + block.bounds.top * scale).dp
                        )
                        .size(
                            width = (block.bounds.width * scale).dp,
                            height = (block.bounds.height * scale).dp
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onBlockClicked(block.id) },
                    contentAlignment = Alignment.Center
                ) {
                    if (block.currentImageUri != null) {
                        // 尝试解码显示图片
                        val bitmap = block.currentImageUri.decodeBase64ToBitmap()
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(text = "Error", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Text(
                            text = stringResource(block.type.getDisplayNameRes()),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}