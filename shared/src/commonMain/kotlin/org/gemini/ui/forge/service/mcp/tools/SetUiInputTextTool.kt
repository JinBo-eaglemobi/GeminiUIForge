package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gemini.ui.forge.accessibility.ComposeAccessibilityGateway
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry

/**
 * 真实 UI 输入框文本直填工具
 * 直接将目标文本填入指定输入框的内部 State，无需逐字打字动画，高效且真实触发 onValueChange
 */
class SetUiInputTextTool : McpToolDefinition {
    override val name: String = "set_ui_input_text"
    override val description: String =
        "模拟人工输入，直接将文本填入当前界面的指定输入框。支持通过数字 ID、testTag、或路线图标识 (如 input_template_name, input_prompt_zh 等) 定位，触发原生的 SetText 与 onValueChange 状态联动。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("nodeId", buildJsonObject {
                put("type", "string")
                put("description", "目标输入框节点的语义选择器 (支持数字ID、testTag，或手写节点标识如 input_template_name 等)")
            })
            put("text", buildJsonObject {
                put("type", "string")
                put("description", "要填入的完整文本字符串")
            })
        })
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("nodeId"))
            add(kotlinx.serialization.json.JsonPrimitive("text"))
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val nodeId = arguments["nodeId"]?.jsonPrimitive?.content ?: return McpToolResult.error("缺少必需参数 nodeId")
        val text = arguments["text"]?.jsonPrimitive?.content ?: return McpToolResult.error("缺少必需参数 text")

        // 通道 1：原生 Compose SemanticsActions.SetText 直接注入
        val gatewaySuccess = ComposeAccessibilityGateway.setText(nodeId, text)
        if (gatewaySuccess) {
            return McpToolResult.text("✅ 已成功通过原生语义通道向输入框 [$nodeId] 填入文本 (长度: ${text.length})")
        }

        // 通道 2：UiRoadmapRegistry 手写注册通道兜底
        val success = UiRoadmapRegistry.setInputText(nodeId, text)
        return if (success) {
            McpToolResult.text("成功向输入框 [$nodeId] 填入文本 (长度: ${text.length})")
        } else {
            McpToolResult.error("填入文本失败，当前界面未挂载或不支持该输入节点: $nodeId (可调用 get_semantic_tree 查询)")
        }
    }
}
