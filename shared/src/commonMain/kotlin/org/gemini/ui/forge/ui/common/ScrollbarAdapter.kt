package org.gemini.ui.forge.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VerticalScrollbarAdapter(
    modifier: Modifier = Modifier,
    scrollState: ScrollState
)

@Composable
expect fun VerticalScrollbarAdapter(
    modifier: Modifier = Modifier,
    scrollState: LazyListState
)
