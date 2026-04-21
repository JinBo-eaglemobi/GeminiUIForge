package org.gemini.ui.forge.service

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 连通性测试：验证是否可以成功请求 Vertex AI (Imagen)。
 * 注意：运行此测试需要配置有效的 API Key。
 */
class AIGenerationServiceConnectivityTest {

    suspend fun getApiKey() = ConfigManager().run { loadKey("GEMINI_API_KEY") ?: loadGlobalGeminiKey() }!!

    @Test
    fun testImagenConnectivity() = runTest {
        // ... (保持原有代码不变)
        val apiKey = getApiKey()

        // 实例化真实依赖 (因为 ConfigManager 是 expect 类，在 JVM 测试环境下会有对应实现)
        val configManager = ConfigManager()
        val cloudAssetManager = CloudAssetManager(configManager)
        val service = AIGenerationService(cloudAssetManager)

        println("开始测试 Google AI Studio (Imagen) 请求...")

        try {
            val results = service.generateImages(
                blockType = "Button",
                userPrompt = "A futuristic glowing button, sci-fi style, high quality",
                count = 1,
                apiKey = apiKey,
                onLog = { println("[LOG] $it") }
            )

            assertTrue(results.isNotEmpty(), "应该至少返回一张生成的图片")
            println("✅ 测试成功！成功获取到 ${results.size} 张图片数据。")
            println("首张图片数据预览 (Base64 前50位): ${results[0].take(50)}...")
        } catch (e: Exception) {
            println("❌ 测试失败：捕获到异常")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testVertexAIConnectivity() = runTest {
        val apiKey = getApiKey()
        val configManager = ConfigManager()
        val cloudAssetManager = CloudAssetManager(configManager)
        val service = AIGenerationService(cloudAssetManager)

        println("\n🚀 开始测试 Vertex AI 专用 Endpoint 请求...")

        try {
            val results = service.generateImages(
                blockType = "Button",
                userPrompt = "A futuristic glowing button, vertex ai style, high quality",
                count = 1,
                apiKey = apiKey,
                isVertexAI = true, // 启用 Vertex AI 模式
                onLog = { println("[Vertex Log] $it") }
            )

            assertTrue(results.isNotEmpty(), "Vertex AI 模式应该也返回生成的图片")
            println("✅ Vertex AI 测试成功！成功获取到 ${results.size} 张图片数据。")
        } catch (e: Exception) {
            println("❌ Vertex AI 测试失败：请检查 API Key 权限或区域设置")
            e.printStackTrace()
            throw e
        }
    }
}
