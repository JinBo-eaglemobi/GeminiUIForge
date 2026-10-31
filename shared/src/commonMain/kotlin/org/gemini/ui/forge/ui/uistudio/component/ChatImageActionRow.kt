package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * AI 聊天消息图片下方按需操作行
 *
 * 遵循一文件一 Composable 与设计系统规范，紧随生成的图片下方呈现，
 * 提供「存入素材库...」与「添加到当前页面」两个操作入口。
 */
@Composable
fun ChatImageActionRow(
    imageUri: String,
    onSaveToAsset: (imageUri: String) -> Unit,
    onAddToPage: (imageUri: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            Text(
                text = stringResource(Res.string.chat_image_ready_hint),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 存入素材库...
                OutlinedButton(
                    onClick = { onSaveToAsset(imageUri) },
                    contentPadding = PaddingValues(horizontal = spacing.small, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.chat_image_apply_asset),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                    )
                }

                // 2. 添加到当前页面
                Button(
                    onClick = { onAddToPage(imageUri) },
                    contentPadding = PaddingValues(horizontal = spacing.small, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.chat_image_add_to_page),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatImageActionRow(
    message: VisualChatMessage,
    onSaveToAsset: (imageUri: String) -> Unit,
    onAddToPage: (imageUri: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uri = message.generatedImageUri ?: return
    ChatImageActionRow(
        imageUri = uri,
        onSaveToAsset = onSaveToAsset,
        onAddToPage = onAddToPage,
        modifier = modifier
    )
}
