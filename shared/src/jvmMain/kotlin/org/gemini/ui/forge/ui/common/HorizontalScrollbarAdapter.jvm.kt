package org.gemini.ui.forge.ui.common

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
actual fun HorizontalScrollbarAdapter(
    modifier: Modifier,
    scrollState: ScrollState
) {
    HorizontalScrollbar(
        modifier = modifier,
        adapter = rememberScrollbarAdapter(scrollState),
        style = defaultScrollbarStyle().copy(
            unhoverColor = Color.White.copy(alpha = 0.3f),
            hoverColor = Color.White.copy(alpha = 0.6f),
            thickness = 8.dp
        )
    )
}

@Composable
actual fun HorizontalScrollbarAdapter(
    modifier: Modifier,
    scrollState: LazyListState
) {
    HorizontalScrollbar(
        modifier = modifier,
        adapter = rememberScrollbarAdapter(scrollState),
        style = defaultScrollbarStyle().copy(
            unhoverColor = Color.White.copy(alpha = 0.3f),
            hoverColor = Color.White.copy(alpha = 0.6f),
            thickness = 8.dp
        )
    )
}
