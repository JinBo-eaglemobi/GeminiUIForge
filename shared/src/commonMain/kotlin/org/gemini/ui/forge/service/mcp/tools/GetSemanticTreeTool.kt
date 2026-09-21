package org.gemini.ui.forge.service.mcp.tools

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gemini.ui.forge.accessibility.ComposeAccessibilityGateway
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult

/**
 * 原生 Compose 语义树与可交互节点审查工具 (Zero-Code Accessibility & Semantic Dispatcher)
 *
 * 100% 对齐官方 hot-reload-runtime-jvm 语义树算法：
 * 全自动、零代码侵入遍历主窗与所有打开的 Dialog/Popup，递归导出完整的 Compose 语义树 (SemanticsNode)，
 * 包含节点数字 id、role (Button/Checkbox/Tab/Image 等)、text 可见文字、editableText、
 * contentDescription、testTag、bounds 窗口物理绝对坐标、以及可触发的 actions (OnClick, SetText, Scroll 等)。
 */
class GetSemanticTreeTool : McpToolDefinition {

    override val name: String = "get_semantic_tree"

    override val description: String =
        "获取当前应用界面的官方原生 Compose 语义树 JSON (全自动、零代码侵入)。包含所有主窗与弹窗内的按钮、输入框、文本、图元模块等交互节点的数字 ID、角色、可见文字、testTag、窗口像素坐标与支持动作 (OnClick, SetText等)。配合 click_ui_node 与 set_ui_input_text 实现高精度真实交互。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "可选：关键词过滤。仅返回包含该文字、testTag 或角色类型的节点列表概要；缺省时返回完整语义树 JSON")
            })
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val query = arguments["query"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

        if (query != null) {
            // 关键词快速过滤模式
            val allNodes = ComposeAccessibilityGateway.getAllNodes()
            val matched = allNodes.filter { node ->
                val texts = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: ""
                val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: ""
                val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
                texts.contains(query, ignoreCase = true) || desc.contains(query, ignoreCase = true) || tag.contains(query, ignoreCase = true) || node.id.toString() == query
            }

            if (matched.isEmpty()) {
                return McpToolResult.text("未在当前 Compose 语义树中找到匹配 [$query] 的节点 (总扫描节点数: ${allNodes.size})")
            }

            val report = buildString {
                appendLine("找到 ${matched.size} 个匹配 [$query] 的 Compose 语义节点 (总节点数: ${allNodes.size})：")
                appendLine("ID | 角色 | 文本/描述/Tag | 可用动作 | 窗口坐标 [L, T, R, B]")
                appendLine("---")
                matched.forEach { node ->
                    val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.takeIf { it.isNotBlank() }
                    val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()?.takeIf { it.isNotBlank() }
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.isNotBlank() }
                    val role = node.config.getOrNull(SemanticsProperties.Role)?.toString() ?: "Node"
                    val label = listOfNotNull(text, desc, tag).firstOrNull() ?: "-"

                    val actions = mutableListOf<String>()
                    if (node.config.contains(SemanticsActions.OnClick)) actions.add("OnClick")
                    if (node.config.contains(SemanticsActions.SetText)) actions.add("SetText")
                    if (node.config.contains(SemanticsActions.OnLongClick)) actions.add("OnLongClick")

                    val b = node.boundsInWindow
                    appendLine("${node.id} | $role | $label | ${actions.joinToString(", ")} | [${b.left.toInt()}, ${b.top.toInt()}, ${b.right.toInt()}, ${b.bottom.toInt()}]")
                }
            }
            return McpToolResult.text(report)
        }

        // 全量 JSON 树模式
        val treeJson = ComposeAccessibilityGateway.getSemanticTreeJson()
        return McpToolResult.text(treeJson)
    }
}
