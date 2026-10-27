package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.chat.MentionCategory
import org.gemini.ui.forge.model.chat.MentionItem
import org.gemini.ui.forge.model.chat.MentionPayload

/** 预览浮层固定宽度与最大高度标准规范 (遵循 8pt 设计系统与 UI Spec) */
val MENTION_HOVER_PREVIEW_WIDTH = 264.dp
val MENTION_HOVER_PREVIEW_MAX_HEIGHT = 320.dp

/**
 * @ 联想菜单 PC 端悬停预览浮层主体卡片 (一文件一 Composable)
 */
@Composable
fun MentionHoverPreviewCard(
    item: MentionItem,
    config: MentionHoverPreviewConfig,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .width(MENTION_HOVER_PREVIEW_WIDTH)
            .heightIn(max = MENTION_HOVER_PREVIEW_MAX_HEIGHT)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            when (item.category) {
                MentionCategory.ASSET -> {
                    MentionHoverAssetPreview(item = item)
                }
                MentionCategory.VIEW -> {
                    val viewPayload = item.payload as? MentionPayload.ViewPayload
                    val blocks = viewPayload?.let { config.resolveViewBlocks(it.viewId) }.orEmpty()
                    MentionHoverCanvasPreview(
                        title = item.title,
                        subtitle = item.subtitle,
                        blocks = blocks,
                        canvasWidth = (viewPayload?.width ?: 1920).toFloat(),
                        canvasHeight = (viewPayload?.height ?: 1080).toFloat(),
                        config = config
                    )
                }
                MentionCategory.BLOCK -> {
                    val blockPayload = item.payload as? MentionPayload.BlockPayload
                    val block = blockPayload?.let { config.resolveBlockById(it.blockId) }
                    if (block != null && (block.isPureContainer || block.children.isNotEmpty())) {
                        MentionHoverCanvasPreview(
                            title = item.title,
                            subtitle = item.subtitle,
                            blocks = block.children,
                            canvasWidth = block.bounds.width,
                            canvasHeight = block.bounds.height,
                            config = config
                        )
                    } else if (blockPayload != null) {
                        val assetPath = config.resolveAssetPathByHash(block?.assetHash)
                        MentionHoverSlicePreview(
                            item = item,
                            assetPath = assetPath,
                            payload = blockPayload
                        )
                    }
                }
                MentionCategory.DOC -> {
                    val docPayload = item.payload as? MentionPayload.DocPayload
                    if (docPayload != null) {
                        MentionHoverDocPreview(doc = docPayload)
                    }
                }
            }
        }
    }
}
