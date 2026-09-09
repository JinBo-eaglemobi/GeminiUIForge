package org.gemini.ui.forge

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.toFontFamily
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/**
 * 跨平台字体资源预加载屏障组件。
 *
 * 作用：在 Web (JS/Wasm) 或桌面端字体需要异步预热的场景下，
 * 在字体完成解析并注入 FontFamilyResolver 之前提供过渡拦截，
 * 杜绝字符初次渲染时的方块豆腐块乱码或字体加载完成后的界面剧烈回流跳动。
 *
 * @param fontResource 需预加载的目标字体（若为 null 则直接短路渲染内容）
 * @param loadingPlaceholder 字体加载中展示的占位组件（如 Loading 转圈或 Logo，默认空）
 * @param content 字体就绪或容错降级后渲染的真实业务内容树
 */
@Composable
fun WithFontResourcesLoaded(
    fontResource: FontResource? = null,
    loadingPlaceholder: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    // 快速短路：未指定字体资源时零开销直接呈现内容
    if (fontResource == null) {
        content()
        return
    }

    var fontLoaded by remember(fontResource) { mutableStateOf(false) }
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val font = Font(fontResource)

    LaunchedEffect(fontFamilyResolver, fontResource) {
        try {
            fontFamilyResolver.preload(font.toFontFamily())
        } catch (e: Throwable) {
            // 异常防御：若字体加载异常，捕获并打印，确保兜底降级至系统默认字体
            println("WithFontResourcesLoaded: 字体预加载失败，自动降级系统默认字体: ${e.message}")
        } finally {
            fontLoaded = true
        }
    }

    if (fontLoaded) {
        content()
    } else {
        loadingPlaceholder()
    }
}