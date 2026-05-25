package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * 资源项数据模型，对应单个资源配置定义。
 *
 * @property key 资源 Key 标识符（新格式 JSON 映射为 "name"）
 * @property type 资源类型描述
 * @property value 资源对应值（通常默认为空，可以为字符串或复杂的 JSON 节点）
 * @property description 该资源项的具体业务描述或代表的含义
 * @property child 级联子配置节点
 */
@Serializable
data class ResourceItem(
    @SerialName("name")
    val key: String,
    val type: String? = null,
    val value: JsonElement = JsonPrimitive(""),
    val description: String = "",
    val child: List<ResourceItem>? = null
)
