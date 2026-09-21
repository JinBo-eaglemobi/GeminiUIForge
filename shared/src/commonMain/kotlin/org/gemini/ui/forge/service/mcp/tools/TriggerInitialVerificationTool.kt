package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline

/**
 * 首次生成后自动触发程序化自愈与综合校验弹窗工具
 * 驱动工作区弹出校准与自愈检查结果，供用户和 AI 进行全量核验
 */
class TriggerInitialVerificationTool : McpToolDefinition {
    override val name: String = "trigger_initial_verification"
    override val description: String = "在首次生成模板或重大更新后，在 UI 工作区触发自动校验与微观吸附自愈流程，自动将不符合生图条件的复合容器标记为纯容器 (isPureContainer)，并弹出校验反馈弹窗。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val vm = McpUiActionPipeline.getActiveViewModel()
            ?: return McpToolResult.error("当前未处于模板编辑工作区或工作区尚未初始化，无法触发校验流程")

        return try {
            vm.calibrateSelectedBlock()
            McpToolResult.text("成功在 UI 工作区触发首次程序化校验与自愈对齐！不符合独立生图条件的复合模块已自动识别并标为纯容器。")
        } catch (e: Exception) {
            McpToolResult.error("触发校验流程异常: ${e.message}")
        }
    }
}
