package org.gemini.ui.forge.accessibility

import androidx.compose.ui.semantics.SemanticsNode

actual object PlatformSemanticsBridge {
    actual fun findRootSemanticsNodes(): List<SemanticsNode> {
        // iOS 移动端根语义节点支持（预留接口，当前阶段安全返回空列表）
        return emptyList()
    }
}
