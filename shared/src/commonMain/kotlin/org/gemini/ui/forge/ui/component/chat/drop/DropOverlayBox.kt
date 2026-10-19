package org.gemini.ui.forge.ui.component.chat.drop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 拖拽文件悬停时的高亮呼吸边框与提示遮罩组件 (一文件一 Composable)
 *
 * 遵循 UI 规范与人机工程学：
 * 1. 贴合输入区域圆角与轮廓，四周呼吸内缩 2dp；
 * 2. 7:1 高对比度主题高亮边框；
 * 3. 动态引导文案与文件夹图标。
 */
@Composable
fun DropOverlayBox(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val primaryColor = MaterialTheme.colorScheme.primary

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(primaryColor.copy(alpha = 0.12f))
                .border(
                    width = 2.dp,
                    color = primaryColor,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DriveFolderUpload,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = primaryColor
                )
                Spacer(Modifier.height(spacing.small))
                Text(
                    text = "释放文件以添加为工程参考文档 / 附件",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "支持 Markdown 规范文档 (.md)、文本、表格或切片图片",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
