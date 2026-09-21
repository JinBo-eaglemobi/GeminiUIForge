package org.gemini.ui.forge.accessibility

import androidx.compose.ui.semantics.SemanticsNode

/**
 * 跨平台根语义节点桥接入口。
 * 负责从各平台当前宿主窗口/顶层视图中获取活动状态的 Compose 根语义节点列表。
 */
expect object PlatformSemanticsBridge {
    fun findRootSemanticsNodes(): List<SemanticsNode>
}
