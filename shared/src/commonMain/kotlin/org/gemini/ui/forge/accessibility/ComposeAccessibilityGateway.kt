package org.gemini.ui.forge.accessibility

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry

/**
 * 全平台通用 Compose 原生语义树与动作直调网关。
 * 纯原生、零侵入、全自动识别任意 Compose 组件并分发原生动作。
 */
object ComposeAccessibilityGateway {

    /**
     * 获取所有顶层根语义节点
     */
    fun getRootNodes(): List<SemanticsNode> {
        return PlatformSemanticsBridge.findRootSemanticsNodes()
    }

    /**
     * 扁平化收集当前界面中所有活跃的语义节点
     */
    fun getAllNodes(): List<SemanticsNode> {
        val result = mutableListOf<SemanticsNode>()
        val roots = getRootNodes()
        for (root in roots) {
            collectNodesRecursive(root, result)
        }
        return result
    }

    private fun collectNodesRecursive(node: SemanticsNode, outList: MutableList<SemanticsNode>) {
        outList.add(node)
        for (child in node.children) {
            collectNodesRecursive(child, outList)
        }
    }

    /**
     * 智能多维度匹配目标节点：
     * 1. 数字 ID 精确匹配 (如 "42")
     * 2. 原生 Modifier.testTag 精确匹配
     * 3. 按钮/文本可见文字完全匹配
     * 4. 无障碍描述完全匹配
     * 5. 可见文字模糊包含匹配
     * 6. 无障碍描述模糊包含匹配
     */
    fun findNode(selector: String): SemanticsNode? {
        val allNodes = getAllNodes()
        val query = selector.trim()
        if (query.isEmpty()) return null

        // 1. 数字 ID 优先
        val numericId = query.toIntOrNull()
        if (numericId != null) {
            allNodes.find { it.id == numericId }?.let { return it }
        }

        // 2. testTag 精确匹配
        allNodes.find { it.config.getOrNull(SemanticsProperties.TestTag) == query }?.let { return it }

        // 3. 可见文字完全匹配
        allNodes.find {
            it.config.getOrNull(SemanticsProperties.Text)?.any { span -> span.text == query } == true
        }?.let { return it }

        // 4. 无障碍描述完全匹配
        allNodes.find {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { desc -> desc == query } == true
        }?.let { return it }

        // 5. 可见文字包含匹配
        allNodes.find {
            it.config.getOrNull(SemanticsProperties.Text)?.any { span -> span.text.contains(query, ignoreCase = true) } == true
        }?.let { return it }

        // 6. 无障碍描述包含匹配
        allNodes.find {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { desc -> desc.contains(query, ignoreCase = true) } == true
        }?.let { return it }

        return null
    }

    /**
     * 原生触发点击动作 (OnClick)
     */
    suspend fun click(selector: String): Boolean {
        val node = findNode(selector)
        if (node != null) {
            val onClickAction = node.config.getOrNull(SemanticsActions.OnClick)
            if (onClickAction != null) {
                return try {
                    onClickAction.action?.invoke() ?: false
                    true
                } catch (e: Exception) {
                    println("⚠️ Compose 原生点击分发异常: ${e.message}")
                    false
                }
            }
        }

        // 兜底：尝试分发给路线图全局动作处理器（如 btn_back_home 等）
        return UiRoadmapRegistry.clickNode(selector)
    }

    /**
     * 原生触发文本替换动作 (SetText)
     */
    suspend fun setText(selector: String, text: String): Boolean {
        val node = findNode(selector)
        if (node != null) {
            val setTextAction = node.config.getOrNull(SemanticsActions.SetText)
            if (setTextAction != null) {
                return try {
                    setTextAction.action?.invoke(AnnotatedString(text)) ?: false
                    true
                } catch (e: Exception) {
                    println("⚠️ Compose 原生文本填入异常: ${e.message}")
                    false
                }
            }
        }

        // 兜底：尝试分发给路线图全局输入处理器
        return UiRoadmapRegistry.setInputText(selector, text)
    }

    /**
     * 获取指定选择器对应的组件在窗口中的真实绝对物理矩形
     */
    fun getNodeBounds(selector: String): Rect? {
        val node = findNode(selector) ?: return null
        return node.boundsInWindow
    }

    /**
     * 导出全量 Compose 官方原生语义树 JSON 结构
     */
    fun getSemanticTreeJson(): String {
        val roots = getRootNodes()
        if (roots.isEmpty()) {
            return "[]"
        }

        val jsonArray = buildJsonArray {
            for (root in roots) {
                add(serializeNodeToJson(root))
            }
        }
        return jsonArray.toString()
    }

    private fun serializeNodeToJson(node: SemanticsNode): JsonObject {
        return buildJsonObject {
            put("id", node.id)

            node.config.getOrNull(SemanticsProperties.Role)?.let {
                put("role", it.toString())
            }

            val texts = node.config.getOrNull(SemanticsProperties.Text)
            if (!texts.isNullOrEmpty()) {
                putJsonArray("text") {
                    texts.forEach { add(it.text) }
                }
            }

            node.config.getOrNull(SemanticsProperties.EditableText)?.let {
                put("editableText", it.text)
            }

            val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription)
            if (!descriptions.isNullOrEmpty()) {
                putJsonArray("contentDescription") {
                    descriptions.forEach { add(it) }
                }
            }

            node.config.getOrNull(SemanticsProperties.TestTag)?.let {
                put("testTag", it)
            }

            val bounds = node.boundsInWindow
            putJsonObject("bounds") {
                put("left", bounds.left)
                put("top", bounds.top)
                put("right", bounds.right)
                put("bottom", bounds.bottom)
                put("width", bounds.width)
                put("height", bounds.height)
            }

            val actions = mutableListOf<String>()
            if (node.config.getOrNull(SemanticsActions.OnClick) != null) actions.add("onClick")
            if (node.config.getOrNull(SemanticsActions.OnLongClick) != null) actions.add("onLongClick")
            if (node.config.getOrNull(SemanticsActions.SetText) != null) actions.add("SetText")
            if (node.config.getOrNull(SemanticsActions.ScrollBy) != null) actions.add("ScrollBy")
            if (node.config.getOrNull(SemanticsActions.ScrollToIndex) != null) actions.add("ScrollToIndex")

            putJsonArray("actions") {
                actions.forEach { add(it) }
            }

            if (node.children.isNotEmpty()) {
                putJsonArray("children") {
                    node.children.forEach { child ->
                        add(serializeNodeToJson(child))
                    }
                }
            }
        }
    }
}
