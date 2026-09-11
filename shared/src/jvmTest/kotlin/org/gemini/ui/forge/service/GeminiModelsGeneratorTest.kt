package org.gemini.ui.forge.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.gemini.ui.forge.manager.ConfigManager
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gemini 模型枚举生成器（开发期工具，非常规单元测试）。
 *
 * 工作流程：调用 `models.list` 接口拉取当前 API Key 可用的全部模型 → 解析全部有用属性
 * （含版本、基座、Token 上限、采样参数与思考能力）→ 以标准模板重新生成并覆盖写入
 * `GeminiModel.kt` 枚举源文件。
 *
 * 【运行须知】
 * 1. 本测试会真实发起网络请求，且会物理覆盖 `src/commonMain/kotlin/org/gemini/ui/forge/model/GeminiModel.kt`；
 * 2. 请仅在需要刷新模型清单时手动按需执行；
 * 3. 生成的枚举构造参数带有默认值，保证既有引用代码零破坏。
 */
class GeminiModelsGeneratorTest2 {

    @Test
    fun generateGeminiModelEnum() = runBlocking {

        val configManager = ConfigManager()
        val testApiKey = configManager.loadKey("GEMINI_API_KEY") ?: configManager.loadGlobalGeminiKey()

        val client = HttpClient()
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$testApiKey"

        val response = client.get(url)
        val body = response.bodyAsText()

        assertEquals(200, response.status.value, "API 请求失败: $body")

        val json = Json { ignoreUnknownKeys = true }
        val element = json.parseToJsonElement(body)
        val models = element.jsonObject["models"]?.jsonArray

        assertTrue(!models.isNullOrEmpty(), "未找到任何模型数据")

        // 构建新的 GeminiModel.kt 文件内容
        val sb = StringBuilder()
        sb.appendLine("package org.gemini.ui.forge.model")
        sb.appendLine()
        sb.appendLine("/**")
        sb.appendLine(" * 自动生成的 Gemini 模型枚举类。")
        sb.appendLine(" * 由 [GeminiModelsGeneratorTest] 从服务器 `models.list` 接口拉取并生成，请勿手工编辑。")
        sb.appendLine(" *")
        sb.appendLine(" * @param modelName 模型唯一标识（API 调用时使用的名称，如 gemini-2.5-flash）")
        sb.appendLine(" * @param displayName 服务端返回的模型显示名称（英文）")
        sb.appendLine(" * @param description 服务端返回的模型功能描述（英文原文）")
        sb.appendLine(" * @param supportedMethods 该模型支持的生成方法集合（逗号分隔，如 generateContent, countTokens）")
        sb.appendLine(" * @param version 模型版本号（服务端元数据，如 2.5-flash-002）")
        sb.appendLine(" * @param baseModelId 基座模型标识（微调/衍生模型的来源基座；原生模型为空）")
        sb.appendLine(" * @param inputTokenLimit 单次请求允许的最大输入 Token 数（0 表示服务端未披露）")
        sb.appendLine(" * @param outputTokenLimit 单次响应允许生成的最大输出 Token 数（0 表示服务端未披露）")
        sb.appendLine(" * @param temperature 服务端默认采样温度（null 表示服务端未披露）")
        sb.appendLine(" * @param maxTemperature 服务端允许的最高采样温度（null 表示服务端未披露）")
        sb.appendLine(" * @param topP 服务端默认核采样概率阈值（null 表示服务端未披露）")
        sb.appendLine(" * @param topK 服务端默认 Top-K 采样候选数（null 表示服务端未披露）")
        sb.appendLine(" * @param supportsThinking 是否具备思考/推理能力（服务端官方 thinking 字段；字段缺失时启发式兜底）")
        sb.appendLine(" */")
        sb.appendLine("enum class GeminiModel(")
        sb.appendLine("    val modelName: String,")
        sb.appendLine("    val displayName: String,")
        sb.appendLine("    val description: String,")
        sb.appendLine("    val supportedMethods: String,")
        sb.appendLine("    val version: String = \"\",")
        sb.appendLine("    val baseModelId: String = \"\",")
        sb.appendLine("    val inputTokenLimit: Long = 0L,")
        sb.appendLine("    val outputTokenLimit: Long = 0L,")
        sb.appendLine("    val temperature: Float? = null,")
        sb.appendLine("    val maxTemperature: Float? = null,")
        sb.appendLine("    val topP: Float? = null,")
        sb.appendLine("    val topK: Int? = null,")
        sb.appendLine("    val supportsThinking: Boolean = false")
        sb.appendLine(") {")

        models.forEachIndexed { index, modelElement ->
            val m = modelElement.jsonObject
            val rawName = m["name"]?.jsonPrimitive?.content ?: ""
            // 去除 "models/" 前缀
            val modelName = rawName.removePrefix("models/")

            // 将类似 gemini-1.5-flash 转为 GEMINI_1_5_FLASH
            val enumName = modelName.replace("-", "_").replace(".", "_").uppercase()

            val displayName = m["displayName"]?.jsonPrimitive?.content ?: ""
            // 描述中的换行符转为空格、反斜杠与双引号转义，防止破坏代码结构
            val description = m["description"]?.jsonPrimitive?.content
                ?.replace("\\", "\\\\")
                ?.replace("\n", " ")
                ?.replace("\"", "\\\"") ?: ""
            val methods =
                m["supportedGenerationMethods"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content } ?: ""

            // —— 解析服务端附加元数据（缺失时使用安全默认值） ——
            val version = m["version"]?.jsonPrimitive?.content ?: ""
            val baseModelId = m["baseModelId"]?.jsonPrimitive?.content ?: ""
            val inputTokenLimit = m["inputTokenLimit"]?.jsonPrimitive?.longOrNull ?: 0L
            val outputTokenLimit = m["outputTokenLimit"]?.jsonPrimitive?.longOrNull ?: 0L
            val temperature = m["temperature"]?.jsonPrimitive?.floatOrNull
            val maxTemperature = m["maxTemperature"]?.jsonPrimitive?.floatOrNull
            val topP = m["topP"]?.jsonPrimitive?.floatOrNull
            val topK = m["topK"]?.jsonPrimitive?.intOrNull

            // 思考能力：优先直读服务端官方 thinking 布尔字段；
            // 仅当字段缺失（个别旧网关/模型不返回）时，才回退描述与名称关键词启发式兜底
            val supportsThinking = m["thinking"]?.jsonPrimitive?.booleanOrNull
                ?: (description.contains("thinking", ignoreCase = true) ||
                        description.contains("reasoning", ignoreCase = true) ||
                        modelName.contains("thinking", ignoreCase = true))

            // —— 组装单条目元数据注释（仅记录构造参数之外的增量信息，杜绝复制属性值） ——
            val metaParts = mutableListOf<String>()
            if (version.isNotBlank()) metaParts.add("版本: $version")
            if (baseModelId.isNotBlank()) metaParts.add("基座: $baseModelId")
            if (inputTokenLimit > 0 || outputTokenLimit > 0) {
                metaParts.add("Token: 输入 $inputTokenLimit / 输出 $outputTokenLimit")
            }
            temperature?.let { metaParts.add("温度 $it") }
            maxTemperature?.let { metaParts.add("最高温度 $it") }
            topP?.let { metaParts.add("TopP $it") }
            topK?.let { metaParts.add("TopK $it") }
            metaParts.add("思考: ${if (supportsThinking) "支持" else "不支持"}")

            val isLast = index == models.size - 1
            val terminator = if (isLast) ";" else ","

            sb.appendLine("    /** ${metaParts.joinToString(" · ")} */")
            sb.appendLine(
                "    $enumName(" +
                        "\"$modelName\", " +
                        "\"$displayName\", " +
                        "\"$description\", " +
                        "\"$methods\", " +
                        "version = \"${version.replace("\"", "\\\"")}\", " +
                        "baseModelId = \"${baseModelId.replace("\"", "\\\"")}\", " +
                        "inputTokenLimit = $inputTokenLimit, " +
                        "outputTokenLimit = $outputTokenLimit, " +
                        "temperature = ${temperature?.let { "${it}f" } ?: "null"}, " +
                        "maxTemperature = ${maxTemperature?.let { "${it}f" } ?: "null"}, " +
                        "topP = ${topP?.let { "${it}f" } ?: "null"}, " +
                        "topK = ${topK ?: "null"}, " +
                        "supportsThinking = $supportsThinking" +
                        ")$terminator"
            )
            if (!isLast) sb.appendLine()
        }

        sb.appendLine("}")

        println(sb)
//        return@runBlocking

        // 定位到共享模块公共源码目录下的 GeminiModel.kt 文件
        // Gradle 测试运行时的 user.dir 通常是子项目目录 (shared)
        val targetFile = File("src/commonMain/kotlin/org/gemini/ui/forge/model/GeminiModel.kt")

        // 写入文件
        targetFile.writeText(sb.toString())

        println("==================================================")
        println("成功生成并覆盖写入 ${models.size} 个模型到源文件:")
        println(targetFile.absolutePath)
        println("==================================================")

        client.close()
    }
}
