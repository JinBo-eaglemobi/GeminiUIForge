package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * 资源项数据模型，对应单个资源配置定义。
 *
 * @property key 资源 Key 标识符（如 "backButtonIcon"）
 * @property value 资源对应值（通常默认为空，可以为字符串或复杂的 JSON 节点）
 * @property description 该资源项的具体业务描述或代表的含义
 */
@Serializable
data class ResourceItem(
    val key: String,
    val value: JsonElement = JsonPrimitive(""),
    val description: String = ""
)
