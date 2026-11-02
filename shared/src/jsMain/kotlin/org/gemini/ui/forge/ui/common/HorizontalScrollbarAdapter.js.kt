package org.gemini.ui.forge.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun HorizontalScrollbarAdapter(
    modifier: Modifier,
    scrollState: ScrollState
) {
    // Web (JS) 端不显示桌面样式的滚动条
}

@Composable
actual fun HorizontalScrollbarAdapter(
    modifier: Modifier,
    scrollState: LazyListState
) {
    // Web (JS) 端不显示桌面样式的滚动条
}
