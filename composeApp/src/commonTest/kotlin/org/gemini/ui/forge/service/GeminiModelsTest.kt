package org.gemini.ui.forge.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test

class GeminiModelsTest {

    @Test
    fun listAvailableGeminiModels() = runTest {
        val configManager = ConfigManager()
        val testApiKey = configManager.loadKey("GEMINI_API_KEY") ?: configManager.loadGlobalGeminiKey()
        val client = HttpClient()
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$testApiKey"

        println("=== 正在请求 Gemini 可用模型列表 ===")
        
        try {
            val response = client.get(url)
            val body = response.bodyAsText()
            
            if (response.status.value == 200) {
                val json = Json { prettyPrint = true }
                val element = json.parseToJsonElement(body)
                val models = element.jsonObject["models"]?.jsonArray
                
                println("找到 ${models?.size ?: 0} 个可用模型:\n")
                
                models?.forEach { modelElement ->
                    val m = modelElement.jsonObject
                    val name = m["name"]?.jsonPrimitive?.content ?: "Unknown"
                    val displayName = m["displayName"]?.jsonPrimitive?.content ?: ""
                    val description = m["description"]?.jsonPrimitive?.content ?: ""
                    val methods = m["supportedGenerationMethods"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content } ?: ""

                    println("--------------------------------------------------")
                    println("ID: $name")
                    println("名称: $displayName")
                    println("功能描述: $description")
                    println("支持的方法: $methods")
                }
                println("--------------------------------------------------")
            } else {
                println("请求失败! 状态码: ${response.status}")
                println("错误详情: $body")
            }
        } catch (e: Exception) {
            println("发生异常: ${e.message}")
        } finally {
            client.close()
        }
    }
}
