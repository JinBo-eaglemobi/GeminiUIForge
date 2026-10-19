package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 当前聊天或宿主视口可用可视高度的 CompositionLocal。
 *
 * 用于让内层的 Markdown 代码块能够动态感知外层真实视口空间，
 * 实现“撑满当前可视高度”的响应式布局，避免死板硬编码常数。
 */
val LocalChatViewportHeight = compositionLocalOf<Dp> { 720.dp }
