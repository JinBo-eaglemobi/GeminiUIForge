package org.gemini.ui.forge.accessibility

import androidx.compose.ui.semantics.SemanticsNode
import java.awt.Window
import java.lang.reflect.Method
import javax.accessibility.Accessible

actual object PlatformSemanticsBridge {

    actual fun findRootSemanticsNodes(): List<SemanticsNode> {
        val windows = collectActiveWindows()
        if (windows.isEmpty()) return emptyList()

        val map = LinkedHashMap<Int, SemanticsNode>()
        for (w in windows) {
            if (w is Accessible) {
                collectRootSemanticsNodes(w, 0, map)
            }
        }
        return map.values.toList()
    }

    private fun collectActiveWindows(): List<Window> {
        val all = mutableListOf<Window>()
        try {
            for (w in Window.getWindows()) {
                if (w.isShowing) {
                    all.add(w)
                }
            }
        } catch (_: Throwable) {
            // 忽略读取窗口异常
        }
        return all
    }

    private fun collectRootSemanticsNodes(
        accessible: Accessible,
        depth: Int,
        map: LinkedHashMap<Int, SemanticsNode>
    ) {
        if (depth > 8) return
        val node = getSemanticsNodeOrNull(accessible)
        if (node != null) {
            var root: SemanticsNode = node
            while (!root.isRoot) {
                val parent = root.parent ?: break
                root = parent
            }
            map.putIfAbsent(root.id, root)
            return
        }

        val context = accessible.accessibleContext ?: return
        val count = context.accessibleChildrenCount
        for (i in 0 until count) {
            val child = context.getAccessibleChild(i) ?: continue
            collectRootSemanticsNodes(child, depth + 1, map)
        }
    }

    private fun getSemanticsNodeOrNull(accessible: Accessible): SemanticsNode? {
        return try {
            val method: Method = accessible.javaClass.getMethod("getSemanticsNode")
            method.invoke(accessible) as? SemanticsNode
        } catch (_: Throwable) {
            null
        }
    }
}
