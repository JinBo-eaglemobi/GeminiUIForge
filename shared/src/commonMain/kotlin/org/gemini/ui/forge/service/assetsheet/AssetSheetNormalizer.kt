package org.gemini.ui.forge.service.assetsheet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import org.gemini.ui.forge.model.assetsheet.AssetSheetParseResult
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.GeminiClient
import org.gemini.ui.forge.service.ai.GeminiChatEngine
import org.gemini.ui.forge.service.ai.GeminiChatRequest
import org.gemini.ui.forge.service.ai.GeminiGenerationConfig

/**
 * 异构图集规范检测与双通道自我同步引擎。
 *
 * 承担外部异构坐标 JSON（如不同标注工具导出的切片配置、多层分类结构、非标属性名）
 * 到系统标准 [NormalizedAssetItem] 与 [SerialRect] 坐标体系的无损清洗、坐标自愈与语义类型推断。
 * 具备离线规则快速解析与 AI 智能转译双通道架构。
 */
object AssetSheetNormalizer {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 离线快速解析与规范化入口。
     *
     * @param jsonText 外部输入的任意 JSON 文本
     * @param fallbackWidth 缺失画布声明时的兜底宽度（默认 1280f）
     * @param fallbackHeight 缺失画布声明时的兜底高度（默认 720f）
     * @return 标准化图元解析结果 [AssetSheetParseResult]
     */
    fun normalizeJsonOffline(
        jsonText: String,
        fallbackWidth: Float = 1280f,
        fallbackHeight: Float = 720f
    ): AssetSheetParseResult {
        val warnings = mutableListOf<String>()
        val parsedElement = try {
            jsonParser.parseToJsonElement(jsonText.trim())
        } catch (e: Exception) {
            return AssetSheetParseResult(
                canvasWidth = fallbackWidth,
                canvasHeight = fallbackHeight,
                items = emptyList(),
                detectedFormat = "INVALID_JSON",
                warnings = listOf("JSON 语法解析错误: ${e.message}")
            )
        }

        var canvasWidth = fallbackWidth
        var canvasHeight = fallbackHeight
        var detectedFormat = "UNKNOWN"
        val rawItemObjects = mutableListOf<Pair<String, JsonObject>>() // category to JsonObject

        when (parsedElement) {
            is JsonObject -> {
                // 1. 尝试提取画布规格（支持对象或 2 位数字数组）
                val detectedCanvas = extractCanvasSize(parsedElement)
                if (detectedCanvas != null) {
                    canvasWidth = detectedCanvas.first
                    canvasHeight = detectedCanvas.second
                    detectedFormat = "CANVAS_WRAPPED"
                }

                // 2. 检查是否有已知图元集合键（assets, items, elements, sprites 等）
                val possibleCollectionKeys = listOf(
                    "assets", "items", "blocks", "elements", "sprites",
                    "layers", "objects", "slices", "nodes", "components"
                )
                var foundCollectionKey: String? = null
                for (key in possibleCollectionKeys) {
                    if (parsedElement.containsKey(key)) {
                        val subEl = parsedElement[key]
                        if (subEl is JsonArray) {
                            foundCollectionKey = key
                            detectedFormat = if (detectedCanvas != null) "GOD_CUP_SPEC" else "STANDARD_LIST"
                            extractFromArray(subEl, rawItemObjects, warnings)
                            break
                        } else if (subEl is JsonObject) {
                            foundCollectionKey = key
                            detectedFormat = "CONTAINER_MAP"
                            extractFromMap(subEl, rawItemObjects, warnings)
                            break
                        }
                    }
                }

                // 3. 检查分类分组模式（如 categories: [ { name: "...", items: [...] } ]）
                if (foundCollectionKey == null && parsedElement.containsKey("categories")) {
                    val categoriesEl = parsedElement["categories"]
                    if (categoriesEl is JsonArray) {
                        detectedFormat = "CATEGORIZED_GROUPS"
                        categoriesEl.forEach { catEl ->
                            val catObj = catEl.jsonObjectSafe()
                            if (catObj != null) {
                                val catName = catObj.findString("name", "category", "title") ?: "Uncategorized"
                                val innerArray = catObj.findArray("items", "assets", "elements", "objects")
                                if (innerArray != null) {
                                    extractFromArray(innerArray, rawItemObjects, warnings, defaultCategory = catName)
                                }
                            }
                        }
                    }
                }

                // 4. 零预设自适应：检查根 JsonObject 是否本身就是一个字典映射表
                // 过滤掉元数据键（canvas, meta, resolution, config, version 等）
                if (rawItemObjects.isEmpty()) {
                    val metaKeys = setOf("canvas", "resolution", "meta", "metadata", "config", "version", "size", "viewport", "screen")
                    val candidateEntries = parsedElement.filterKeys { it.lowercase() !in metaKeys }

                    if (candidateEntries.isNotEmpty()) {
                        val allArrays = candidateEntries.values.all { it is JsonArray }
                        val allObjects = candidateEntries.values.all { it is JsonObject }

                        if (allArrays) {
                            detectedFormat = "ROOT_MAP_4_NUMBERS"
                            extractFromMap(JsonObject(candidateEntries), rawItemObjects, warnings)
                        } else if (allObjects) {
                            detectedFormat = "ROOT_MAP_OBJECTS"
                            extractFromMap(JsonObject(candidateEntries), rawItemObjects, warnings)
                        } else {
                            // 混合或尝试提取第一个包含数组的属性
                            for ((key, value) in candidateEntries) {
                                if (value is JsonArray && value.isNotEmpty()) {
                                    detectedFormat = "INFERRED_ARRAY_$key"
                                    extractFromArray(value, rawItemObjects, warnings)
                                    warnings.add("未能匹配标准键名，已自适应提取属性: $key")
                                    break
                                }
                            }
                        }
                    }
                }
            }

            is JsonArray -> {
                extractFromArray(parsedElement, rawItemObjects, warnings)
                detectedFormat = if (parsedElement.firstOrNull() is JsonArray) "ARRAY_OF_4_NUMBERS" else "FLAT_ARRAY"
            }

            else -> {
                return AssetSheetParseResult(
                    canvasWidth = fallbackWidth,
                    canvasHeight = fallbackHeight,
                    items = emptyList(),
                    detectedFormat = "PRIMITIVE_ROOT",
                    warnings = listOf("JSON 根节点既非对象也非数组，无法提取图元")
                )
            }
        }

        // 统一归一化提取图元
        val normalizedItems = mutableListOf<NormalizedAssetItem>()
        rawItemObjects.forEachIndexed { index, (groupCategory, itemObj) ->
            val id = itemObj.findString("id", "key", "tag", "code") ?: "item_${index + 1}"
            val name = itemObj.findString("name", "label", "title", "id") ?: "Block_${index + 1}"
            val category = itemObj.findString("category", "group", "folder") ?: groupCategory
            val desc = itemObj.findString("description", "desc", "prompt", "userPromptZh", "info")

            val bounds = parseBounds(itemObj, warnings, id)
            val type = inferUIBlockType(name, category, desc)
            val isPure = (type == UIBlockType.CONTAINER) || (itemObj.findBoolean("isPureContainer", "isPure") ?: false)

            normalizedItems.add(
                NormalizedAssetItem(
                    id = id,
                    name = name,
                    category = category,
                    type = type,
                    bounds = bounds,
                    description = desc,
                    isPureContainer = isPure
                )
            )
        }

        // 若图元范围超出默认画布，动态扩展画布尺寸
        if (normalizedItems.isNotEmpty()) {
            val maxRight = normalizedItems.maxOf { it.bounds.left + it.bounds.width }
            val maxBottom = normalizedItems.maxOf { it.bounds.top + it.bounds.height }
            if (maxRight > canvasWidth) {
                canvasWidth = maxRight
                warnings.add("图元最大坐标超出声明宽度，画布宽度已自适应修正为: $canvasWidth")
            }
            if (maxBottom > canvasHeight) {
                canvasHeight = maxBottom
                warnings.add("图元最大坐标超出声明高度，画布高度已自适应修正为: $canvasHeight")
            }
        }

        return AssetSheetParseResult(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            items = normalizedItems,
            detectedFormat = detectedFormat,
            warnings = warnings
        )
    }

    /**
     * AI 智能语义转译兜底通道。
     *
     * 当外部输入非标严重、难以通过正则表达式或离线规则清洗时，调用 Gemini 执行语义重构。
     */
    suspend fun normalizeJsonWithAi(
        jsonText: String,
        apiKey: String,
        baseUrl: String? = null
    ): AssetSheetParseResult {
        val promptManager = org.gemini.ui.forge.manager.PromptManager(org.gemini.ui.forge.utils.LocalFileStorage())
        val systemPrompt = try {
            promptManager.getPrompt(org.gemini.ui.forge.model.prompt.PromptKey.NORMALIZE_ASSET_SHEET)
        } catch (_: Exception) {
            promptManager.getDefaultResourcePrompt(org.gemini.ui.forge.model.prompt.PromptKey.NORMALIZE_ASSET_SHEET)
        }
        val userPromptTemplate = try {
            promptManager.getPrompt(org.gemini.ui.forge.model.prompt.PromptKey.NORMALIZE_ASSET_SHEET_USER)
        } catch (_: Exception) {
            promptManager.getDefaultResourcePrompt(org.gemini.ui.forge.model.prompt.PromptKey.NORMALIZE_ASSET_SHEET_USER)
        }
        val effectivePrompt = if (userPromptTemplate.contains("%s")) {
            userPromptTemplate.replace("%s", jsonText)
        } else {
            "$userPromptTemplate\n$jsonText"
        }

        val chatEngine = GeminiChatEngine()
        val request = GeminiChatRequest(
            apiKey = apiKey,
            model = org.gemini.ui.forge.model.GeminiModel.GEMINI_2_5_FLASH,
            prompt = effectivePrompt,
            systemInstruction = systemPrompt,
            generationConfig = GeminiGenerationConfig(
                temperature = 0.1
            )
        )
        val chatResult = chatEngine.executeChat(request)
        val response = chatResult.text

        val cleanJson = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val result = normalizeJsonOffline(cleanJson)
        return result.copy(
            detectedFormat = "AI_TRANSCRIPTION_${result.detectedFormat}",
            warnings = result.warnings + "已通过 Gemini 视觉语义转译引擎成功标准化"
        )
    }

    /**
     * 智能多形态坐标解析与自愈计算。
     */
    private fun parseBounds(
        obj: JsonObject,
        warnings: MutableList<String>,
        itemId: String
    ): SerialRect {
        // 形态 A：标准 (x, y, w, h)
        var x = obj.findFloat("x", "left", "x1")
        var y = obj.findFloat("y", "top", "y1")
        var w = obj.findFloat("w", "width")
        var h = obj.findFloat("h", "height")

        val x2 = obj.findFloat("x2", "right")
        val y2 = obj.findFloat("y2", "bottom")

        // 容错 1：缺少 w 时通过 x2 - x 补齐
        if (w == null || w <= 0f) {
            if (x != null && x2 != null && x2 > x) {
                w = x2 - x
            }
        }

        // 容错 2：缺少 h 时通过 y2 - y 补齐
        if (h == null || h <= 0f) {
            if (y != null && y2 != null && y2 > y) {
                h = y2 - y
            }
        }

        // 形态 B：检查 bounds 或 rect 字段
        if (x == null || y == null || w == null || h == null) {
            val boundsObj = obj["bounds"]?.jsonObjectSafe() ?: obj["rect"]?.jsonObjectSafe()
            if (boundsObj != null) {
                x = x ?: boundsObj.findFloat("x", "left")
                y = y ?: boundsObj.findFloat("y", "top")
                w = w ?: boundsObj.findFloat("w", "width")
                h = h ?: boundsObj.findFloat("h", "height")
            } else {
                val boundsArray = obj["bounds"]?.jsonArraySafe() ?: obj["rect"]?.jsonArraySafe()
                if (boundsArray != null && boundsArray.size >= 4) {
                    x = x ?: boundsArray[0].jsonPrimitiveSafe()?.floatOrNull
                    y = y ?: boundsArray[1].jsonPrimitiveSafe()?.floatOrNull
                    w = w ?: boundsArray[2].jsonPrimitiveSafe()?.floatOrNull
                    h = h ?: boundsArray[3].jsonPrimitiveSafe()?.floatOrNull
                }
            }
        }

        val finalX = x ?: 0f
        val finalY = y ?: 0f
        val finalW = if (w != null && w > 0f) w else 64f
        val finalH = if (h != null && h > 0f) h else 64f

        if (w == null || h == null) {
            warnings.add("图元 $itemId 坐标字段不全，已自动应用安全默认尺寸: [$finalX, $finalY, $finalW, $finalH]")
        }

        return SerialRect(
            left = finalX,
            top = finalY,
            right = finalX + finalW,
            bottom = finalY + finalH
        )
    }

    /**
     * 启发式映射推导系统标准 [UIBlockType]。
     */
    fun inferUIBlockType(name: String, category: String?, description: String?): UIBlockType {
        val combined = "${name.lowercase()} ${category?.lowercase().orEmpty()} ${description?.lowercase().orEmpty()}"

        return when {
            // 1. 优先识别主按键 Spin
            combined.contains("spin") -> UIBlockType.SPIN_BUTTON

            // 2. 交互按键
            combined.contains("button") || combined.contains("btn") ||
                combined.contains("toggle") || combined.contains("switch") ||
                combined.contains("tab") || combined.contains("nav_") -> UIBlockType.BUTTON

            // 3. 特殊符号、队徽与卡牌标志
            combined.contains("symbol") || combined.contains("scatter") ||
                combined.contains("wild") || combined.contains("trophy") ||
                combined.contains("badge") || combined.contains("club") ||
                combined.contains("card") || combined.contains("icon") -> UIBlockType.SYMBOL

            // 4. 背景与底板横幅
            combined.contains("background") || combined.contains("bg") ||
                combined.contains("backdrop") -> UIBlockType.BACKGROUND

            // 5. 顶部/底部导航容器
            combined.contains("header") || combined.contains("top_bar") ||
                combined.contains("top navigation") -> UIBlockType.HEADER
            combined.contains("footer") || combined.contains("bottom_bar") -> UIBlockType.FOOTER

            // 6. 进度条与滑块
            combined.contains("progress") || combined.contains("loading_bar") -> UIBlockType.PROGRESS_BAR
            combined.contains("slider") -> UIBlockType.SLIDER

            // 7. 文本
            combined.contains("text") || combined.contains("label") ||
                combined.contains("title") || combined.contains("amount") -> UIBlockType.TEXT

            // 8. 纯容器占位
            combined.contains("container") || combined.contains("layout") ||
                combined.contains("group") || combined.contains("wrapper") -> UIBlockType.CONTAINER

            // 9. 转轴
            combined.contains("reel") || combined.contains("slot_matrix") -> UIBlockType.REEL

            // 10. 默认为独立图片/装饰物
            else -> UIBlockType.IMAGE
        }
    }

    // --- JSON 扩展辅助函数 ---

    private fun extractCanvasSize(root: JsonObject): Pair<Float, Float>? {
        val keys = listOf("canvas", "resolution", "size", "viewport", "screen")
        for (k in keys) {
            val el = root[k] ?: continue
            when (el) {
                is JsonObject -> {
                    val w = el.findFloat("width", "w", "canvasWidth")
                    val h = el.findFloat("height", "h", "canvasHeight")
                    if (w != null && h != null && w > 0f && h > 0f) {
                        return w to h
                    }
                }
                is JsonArray -> {
                    if (el.size >= 2) {
                        val w = el[0].jsonPrimitiveSafe()?.floatOrNull
                        val h = el[1].jsonPrimitiveSafe()?.floatOrNull
                        if (w != null && h != null && w > 0f && h > 0f) {
                            return w to h
                        }
                    }
                }
                else -> {}
            }
        }
        return null
    }

    private fun extractFromArray(
        array: JsonArray,
        output: MutableList<Pair<String, JsonObject>>,
        warnings: MutableList<String>,
        defaultCategory: String = "Default"
    ) {
        array.forEachIndexed { index, itemEl ->
            when (itemEl) {
                is JsonObject -> {
                    output.add(defaultCategory to itemEl)
                }
                is JsonArray -> {
                    // 纯 4 位数组元素：如 [31, 26, 80, 84]
                    if (itemEl.size >= 4) {
                        val syntheticObj = buildJsonObject {
                            put("id", "item_${index + 1}")
                            put("name", "Item_${index + 1}")
                            put("bounds", itemEl)
                        }
                        output.add(defaultCategory to syntheticObj)
                    }
                }
                else -> {}
            }
        }
    }

    private fun extractFromMap(
        map: JsonObject,
        output: MutableList<Pair<String, JsonObject>>,
        warnings: MutableList<String>,
        defaultCategory: String = "Default"
    ) {
        for ((key, value) in map) {
            when (value) {
                is JsonArray -> {
                    // 键映射 4 位数组：如 "btn_spin": [500, 600, 120, 120]
                    if (value.size >= 4) {
                        val syntheticObj = buildJsonObject {
                            put("id", key)
                            put("name", key)
                            put("bounds", value)
                        }
                        output.add(defaultCategory to syntheticObj)
                    }
                }
                is JsonObject -> {
                    // 键映射图元对象：如 "btn_spin": { "x": 500, ... }
                    val mergedObj = buildJsonObject {
                        put("id", value.findString("id") ?: key)
                        put("name", value.findString("name") ?: key)
                        for ((subK, subV) in value) {
                            put(subK, subV)
                        }
                    }
                    output.add(defaultCategory to mergedObj)
                }
                else -> {}
            }
        }
    }

    private fun JsonObject.findFloat(vararg keys: String): Float? {
        for (k in keys) {
            val prim = this[k]?.jsonPrimitiveSafe()
            if (prim != null) {
                val f = prim.floatOrNull
                if (f != null) return f
            }
        }
        return null
    }

    private fun JsonObject.findString(vararg keys: String): String? {
        for (k in keys) {
            val prim = this[k]?.jsonPrimitiveSafe()
            if (prim != null) {
                val s = prim.contentOrNull
                if (!s.isNullOrBlank()) return s
            }
        }
        return null
    }

    private fun JsonObject.findBoolean(vararg keys: String): Boolean? {
        for (k in keys) {
            val prim = this[k]?.jsonPrimitiveSafe()
            if (prim != null) {
                if (prim.content.equals("true", ignoreCase = true)) return true
                if (prim.content.equals("false", ignoreCase = true)) return false
            }
        }
        return null
    }

    private fun JsonObject.findArray(vararg keys: String): JsonArray? {
        for (k in keys) {
            val el = this[k]
            if (el is JsonArray) return el
        }
        return null
    }

    private fun JsonElement.jsonObjectSafe(): JsonObject? = this as? JsonObject
    private fun JsonElement.jsonArraySafe(): JsonArray? = this as? JsonArray
    private fun JsonElement.jsonPrimitiveSafe() = (this as? kotlinx.serialization.json.JsonPrimitive)
}
