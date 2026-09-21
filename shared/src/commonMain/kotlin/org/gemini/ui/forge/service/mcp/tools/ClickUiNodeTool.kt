package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gemini.ui.forge.AppWindowHolder
import org.gemini.ui.forge.accessibility.ComposeAccessibilityGateway
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry

/**
 * 模拟真实 UI 节点点击工具
 * 触发当前界面的按钮、Tab、菜单或关闭操作，驱动真实的 UI 状态机
 *
 * 三级点击通道 (按优先级)：
 * 1. 原生语义动作直调 (ComposeAccessibilityGateway)：支持数字 id / testTag / 按钮文本(如 "指令说明手册") / contentDescription，
 *    直接原生调用 Compose SemanticsActions.OnClick，零代码侵入且免疫最小化与遮挡；
 * 2. 显式携带 x/y 屏幕绝对像素坐标 → 直接坐标注入点击；
 * 3. 兜底走 UiRoadmapRegistry 手写注册节点 (btn_* 系列)。
 */
class ClickUiNodeTool : McpToolDefinition {
    override val name: String = "click_ui_node"
    override val description: String =
        "真实模拟人工点击当前界面中的指定交互节点，触发真实的事件流转与状态机。支持三种方式：① nodeId=原生语义选择器 (推荐，支持数字ID、testTag、按钮可见文本如'指令说明手册'或描述)；② 携带 x/y 屏幕绝对像素坐标直接点击；③ 兜底手写注册节点 (btn_back, btn_save, btn_open_mcp_console 等)。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("nodeId", buildJsonObject {
                put("type", "string")
                put("description", "目标交互节点标识：原生语义选择器 (支持数字ID、testTag、按钮上的文字如'指令说明手册'、描述，或 get_semantic_tree 查询) 或手写注册节点 (btn_back, btn_save 等)")
            })
            put("x", buildJsonObject {
                put("type", "number")
                put("description", "可选：屏幕绝对像素 X 坐标 (与 y 一起使用，直接坐标点击，主窗与弹窗通吃)")
            })
            put("y", buildJsonObject {
                put("type", "number")
                put("description", "可选：屏幕绝对像素 Y 坐标 (与 x 一起使用，直接坐标点击，主窗与弹窗通吃)")
            })
        })
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("nodeId"))
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val nodeId = arguments["nodeId"]?.jsonPrimitive?.content
            ?: return McpToolResult.error("缺少必需参数 nodeId")
        val x = arguments["x"]?.jsonPrimitive?.doubleOrNull
        val y = arguments["y"]?.jsonPrimitive?.doubleOrNull

        // 通道 1：Compose 原生语义动作直调 (零侵入、全自动、全平台)
        val clickSuccess = ComposeAccessibilityGateway.click(nodeId)
        if (clickSuccess) {
            return McpToolResult.text("✅ 已成功触发原生语义节点 [$nodeId] 的 OnClick 点击动作 (零侵入原生事件分发)")
        }

        // 如果动作触发未直接生效，尝试获取原生语义节点中心坐标物理注入
        val bounds = ComposeAccessibilityGateway.getNodeBounds(nodeId)
        if (bounds != null) {
            val cx = bounds.left + bounds.width / 2f
            val cy = bounds.top + bounds.height / 2f
            val ok = AppWindowHolder.tapAt(cx, cy)
            if (ok) {
                return McpToolResult.text("✅ 已在原生语义节点 [$nodeId] 窗口位置 (${cx.toInt()},${cy.toInt()}) 注入点击")
            }
        }

        // 通道 2：显式屏幕绝对像素坐标点击
        if (x != null && y != null) {
            val ok = AppWindowHolder.tapAt(x.toFloat(), y.toFloat())
            return if (ok) {
                McpToolResult.text("✅ 已在窗口坐标 (${x.toInt()},${y.toInt()}) 注入真实点击")
            } else {
                McpToolResult.error("坐标点击注入失败: (${x},$y) 超出窗口范围或窗口未就绪")
            }
        }

        // 通道 3：兜底手写注册节点 (btn_* 系列)
        val success = UiRoadmapRegistry.clickNode(nodeId)
        return if (success) {
            McpToolResult.text("成功点击 UI 节点: $nodeId")
        } else {
            McpToolResult.error("点击 UI 节点失败或当前界面未找到匹配节点: $nodeId (可调用 get_semantic_tree 审查当前界面全部可见语义节点)")
        }
    }
}
