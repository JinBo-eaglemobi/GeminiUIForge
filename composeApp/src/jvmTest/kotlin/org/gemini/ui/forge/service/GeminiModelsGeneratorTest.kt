package org.gemini.ui.forge.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class GeminiModelsGeneratorTest {

    @Test
    fun generateGeminiModelEnum() = runBlocking {

        val configManager = ConfigManager()
        val testApiKey = configManager.loadKey("GEMINI_API_KEY") ?: configManager.loadGlobalGeminiKey()

        val client = HttpClient()
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$testApiKey"

        println("=== 正在请求 Gemini 可用模型列表 ===")
        
        val response = client.get(url)
        val body = response.bodyAsText()

        assertTrue(response.status.value == 200, "API 请求失败: $body")

        val json = Json { ignoreUnknownKeys = true }
        val element = json.parseToJsonElement(body)
        val models = element.jsonObject["models"]?.jsonArray

        assertTrue(models != null && models.isNotEmpty(), "未找到任何模型数据")

        // 构建新的 GeminiModel.kt 文件内容
        val sb = StringBuilder()
        sb.appendLine("package org.gemini.ui.forge")
        sb.appendLine()
        sb.appendLine("/**")
        sb.appendLine(" * 自动生成的 Gemini 模型枚举类")
        sb.appendLine(" * 包含了当前 API Key 支持的所有可用模型。")
        sb.appendLine(" */")
        sb.appendLine("enum class GeminiModel(")
        sb.appendLine("    val modelName: String,")
        sb.appendLine("    val displayName: String,")
        sb.appendLine("    val description: String,")
        sb.appendLine("    val supportedMethods: String")
        sb.appendLine(") {")

        models.forEachIndexed { index, modelElement ->
            val m = modelElement.jsonObject
            val rawName = m["name"]?.jsonPrimitive?.content ?: ""
            // 去除 "models/" 前缀
            val modelName = rawName.removePrefix("models/")
            
            // 将类似 gemini-1.5-flash 转为 GEMINI_1_5_FLASH
            val enumName = modelName.replace("-", "_").replace(".", "_").uppercase()
            
            val displayName = m["displayName"]?.jsonPrimitive?.content ?: ""
            // 将描述中的换行符去掉，防止破坏代码结构，并将双引号转义
            val description = m["description"]?.jsonPrimitive?.content?.replace("\n", " ")?.replace("\"", "\\\"") ?: ""
            val methods = m["supportedGenerationMethods"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content } ?: ""

            sb.appendLine("    /**")
            sb.appendLine("     * 显示名称: $displayName")
            sb.appendLine("     * 功能描述: $description")
            sb.appendLine("     * 支持的方法: $methods")
            sb.appendLine("     */")
            
            val isLast = index == models.size - 1
            val terminator = if (isLast) ";" else ","
            
            sb.appendLine("    $enumName(\"$modelName\", \"$displayName\", \"$description\", \"$methods\")$terminator")
            if (!isLast) sb.appendLine()
        }

        sb.appendLine("}")

        // 定位到公共源码目录下的 GeminiModel.kt 文件
        // Gradle 测试运行时的 user.dir 通常是子项目目录 (composeApp)
        val targetFile = File("src/commonMain/kotlin/org/gemini/ui/forge/GeminiModel.kt")
        
        // 写入文件
        targetFile.writeText(sb.toString())
        
        println("==================================================")
        println("成功生成并覆盖写入 ${models.size} 个模型到源文件:")
        println(targetFile.absolutePath)
        println("==================================================")
        
        client.close()
    }
}
