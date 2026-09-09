package org.gemini.ui.forge.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * 全局共享的宽容 JSON 反序列化实例。
 * 
 * 配置项说明：
 * - [ignoreUnknownKeys] = true : 忽略 JSON 中未定义的未知属性，防止解析崩溃
 * - [coerceInputValues] = true : 强转输入的不匹配值（如将不匹配的空值强制转为默认值）
 * - [allowTrailingComma] = true : 允许 JSON 文件中带有非标准尾部逗号（常备兼容）
 */
@OptIn(ExperimentalSerializationApi::class)
val looseJson = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    allowTrailingComma = true
}
