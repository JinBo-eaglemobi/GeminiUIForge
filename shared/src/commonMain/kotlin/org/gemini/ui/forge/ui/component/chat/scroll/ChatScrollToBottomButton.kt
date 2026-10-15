package org.gemini.ui.forge.ui.component.chat.scroll

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.component.tip

/**
 * 聊天会话悬浮「回到底部」胶囊按钮 (一文件一 Composable)
 *
 * 核心特性：
 * 1. 自动感知滚动深度，远离底部时平滑淡入浮现；
 * 2. 具备新消息/生成中文本未读指示红点；
 * 3. 悬浮在内容右下角，触控热区舒适，点击平滑直达最新消息。
 */
@Composable
fun ChatScrollToBottomButton(
    visible: Boolean,
    hasNewMessage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring()) + scaleIn(initialScale = 0.8f),
        exit = fadeOut(animationSpec = spring()) + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            FilledTonalIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .size(40.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .tip("回到底部")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "回到底部",
                    modifier = Modifier.size(22.dp)
                )
            }

            // 新消息未读小红点徽标
            if (hasNewMessage) {
                Box(
                    modifier = Modifier
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }
    }
}
