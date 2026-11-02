package org.gemini.ui.forge.service.tool.ai.exclusive

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import kotlin.math.round

/**
 * 高精度 UI 人机工程学几何计算器
 */
class CalculateGeometryTool : ToolDefinition {

    override val name: String = "calculate_geometry"

    override val description: String =
        "执行严格的 8pt 空间网格对齐、嵌套同心圆角曲率半径计算 (R_outer = R_inner + Padding)、纵横比外切/内嵌等纯数学几何计算。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("operation", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("8pt_snap")
                    add("concentric_radius")
                    add("aspect_ratio_fit")
                })
                put("description", "运算类型：8pt_snap (对齐到最近的8倍数), concentric_radius (同心嵌套圆角曲率计算), aspect_ratio_fit (纵横比外切/内嵌计算)")
            })
            put("parameters", buildJsonObject {
                put("type", "object")
                put(
                    "description",
                    "运算参数字典。根据 operation 不同传入对应子字段：\n" +
                    "- '8pt_snap' 期望: { 'value': number }（需对齐的原始浮点数值）\n" +
                    "- 'concentric_radius' 期望: { 'inner_radius': number, 'padding': number }（基于内圆角与内边距推导外圆角）\n" +
                    "- 'aspect_ratio_fit' 期望: { 'target_width': number, 'target_height': number, 'max_width': number, 'max_height': number }（在最大外框约束下计算保比缩放尺寸）"
                )
            })
        })
        put("required", buildJsonArray { add("operation"); add("parameters") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val op = arguments["operation"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("缺少必填参数 operation")
        val params = arguments["parameters"]?.jsonObject ?: buildJsonObject {}

        return when (op) {
            "concentric_radius" -> {
                val innerR = params["inner_radius"]?.jsonPrimitive?.floatOrNull ?: 8f
                val padding = params["padding"]?.jsonPrimitive?.floatOrNull ?: 8f
                val outerR = innerR + padding
                val res = buildJsonObject {
                    put("outer_radius", outerR)
                    put("inner_radius", innerR)
                    put("padding", padding)
                    put("formula", "$outerR = $innerR + $padding")
                }
                ToolResult.success(res.toString())
            }
            "8pt_snap" -> {
                val value = params["value"]?.jsonPrimitive?.floatOrNull ?: 0f
                val snapped = round(value / 8f) * 8f
                val res = buildJsonObject {
                    put("original", value)
                    put("snapped_8pt", snapped)
                }
                ToolResult.success(res.toString())
            }
            "aspect_ratio_fit" -> {
                val targetW = params["target_width"]?.jsonPrimitive?.floatOrNull ?: 100f
                val targetH = params["target_height"]?.jsonPrimitive?.floatOrNull ?: 100f
                val maxW = params["max_width"]?.jsonPrimitive?.floatOrNull ?: 800f
                val maxH = params["max_height"]?.jsonPrimitive?.floatOrNull ?: 600f
                val scale = minOf(maxW / targetW, maxH / targetH)
                val fitW = targetW * scale
                val fitH = targetH * scale
                val res = buildJsonObject {
                    put("fit_width", fitW)
                    put("fit_height", fitH)
                    put("scale_factor", scale)
                }
                ToolResult.success(res.toString())
            }
            else -> ToolResult.error("未支持的几何运算: $op")
        }
    }
}
