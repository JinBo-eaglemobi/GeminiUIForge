package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry

/**
 * 获取当前应用 UI 结构图与活动弹窗感知工具
 * 供 AI 在执行 UI 流转前感知当前界面位置、前台活动弹窗与未保存风险
 */
class GetUiRoadmapTool : McpToolDefinition {
    override val name: String = "get_ui_roadmap"
    override val description: String = "获取应用全局 UI 结构图/路线图，包括当前所在界面、前台活动弹窗、未保存状态及各界面跳转行为与弹窗风险说明。AI 必须根据路线图感知状态，先关闭/处理前台弹窗，再执行页面交互。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val roadmap = UiRoadmapRegistry.getFullRoadmap()
        val roadmapJson = json.encodeToString(roadmap)
        return McpToolResult.text(roadmapJson)
    }
}
