package org.gemini.ui.forge.utils

import kotlinx.serialization.json.*

/**
 * 递归展开 JSON 中嵌套的 JSON 字符串（仅供 UI 界面 Pretty Print 渲染展示，不修改底层原始报文数据）
 */
fun unwrapJsonStringsForDisplay(element: JsonElement): JsonElement {
    return when (element) {
        is JsonPrimitive -> {
            if (element.isString) {
                val str = element.content.trim()
                if ((str.startsWith("{") && str.endsWith("}")) || (str.startsWith("[") && str.endsWith("]"))) {
                    try {
                        val parsed = looseJson.parseToJsonElement(str)
                        unwrapJsonStringsForDisplay(parsed)
                    } catch (_: Exception) {
                        element
                    }
                } else {
                    element
                }
            } else {
                element
            }
        }
        is JsonObject -> {
            JsonObject(element.mapValues { (_, value) -> unwrapJsonStringsForDisplay(value) })
        }
        is JsonArray -> {
            JsonArray(element.map { unwrapJsonStringsForDisplay(it) })
        }
    }
}
