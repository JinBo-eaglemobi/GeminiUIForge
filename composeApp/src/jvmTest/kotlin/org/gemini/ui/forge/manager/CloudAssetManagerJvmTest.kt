package org.gemini.ui.forge.manager

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * 针对 CloudAssetManager 的 JVM 平台集成测试。
 * 该测试使用真实的本地图片和网络请求。
 * 运行前需确保环境变量 GEMINI_API_KEY 已配置，且指定的测试图片路径存在。
 */
class CloudAssetManagerJvmTest {

    @Test
    fun testUploadListAndDeleteFlowWithRealImage() = runBlocking {
        // 2. 初始化真实服务配置
        val configManager = ConfigManager()
        val testApiKey = configManager.loadKey("GEMINI_API_KEY") ?: configManager.loadGlobalGeminiKey()

        // 1. 环境校验
        if (testApiKey.isNullOrBlank()) {
            println("⚠️ 跳过测试: 未在配置或全局环境变量中找到 GEMINI_API_KEY")
            return@runBlocking
        }

        val testImagePath = "D:\\Project\\Test\\effect_main.png"
        val testFile = File(testImagePath)
        
        if (!testFile.exists()) {
            println("⚠️ 跳过测试: 找不到本地测试图片 -> $testImagePath")
            return@runBlocking
        }

        val cloudAssetManager = CloudAssetManager(configManager)
        
        // 提取文件名用于测试标识
        val displayName = "jvm_test_${testFile.name}_${System.currentTimeMillis()}"
        val fileBytes = testFile.readBytes()

        var uploadedFileUri: String? = null
        var uploadedFileName: String? = null

        try {
            println("---- [开始集成测试] ----")
            
            // --- [阶段 1: 真实图片上传] ---
            println("🚀 正在上传真实图片: $testImagePath (${fileBytes.size / 1024} KB)")
            println("   使用的 displayName: $displayName")
            
            // 这个操作包含了轮询机制，如果图片较大，后台会自动等待从 PROCESSING 变为 ACTIVE
            uploadedFileUri = cloudAssetManager.getOrUploadFile(displayName, fileBytes, "image/png")
            
            assertNotNull(uploadedFileUri, "上传失败，返回的 URI 为空。请检查网络或日志详情。")
            assertTrue(uploadedFileUri.isNotEmpty(), "上传的 URI 格式不正确")
            println("✅ 上传成功，最终获取的 URI: $uploadedFileUri")

            // 提取文件在系统内部的唯一标识 (形如: files/abc-123)
            uploadedFileName = uploadedFileUri.substringAfterLast("v1beta/")
            assertTrue(uploadedFileName.startsWith("files/"), "提取的内部标识符异常: $uploadedFileName")

            // --- [阶段 2: 同步并验证列表] ---
            println("🔄 正在拉取最新的云端资产列表...")
            cloudAssetManager.syncFiles()
            
            val currentAssets = cloudAssetManager.assets.value
            assertTrue(currentAssets.isNotEmpty(), "同步后云端资产列表为空！")
            
            // 在列表中寻找我们刚刚上传的文件
            val foundAsset = currentAssets.find { it.name == uploadedFileName }
            assertNotNull(foundAsset, "在拉取的列表中无法找到刚上传的文件: $uploadedFileName")
            assertEquals(displayName, foundAsset.displayName, "文件名与上传时不一致")
            assertEquals("ACTIVE", foundAsset.state, "文件不处于 ACTIVE 状态，可能仍未处理完毕或处理失败")
            
            println("✅ 列表验证通过，找到了目标文件: [${foundAsset.name}] -> ${foundAsset.displayName} (${foundAsset.state})")

        } catch (e: Exception) {
            println("❌ 测试过程中发生意外异常: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            // --- [阶段 3: 清理战场] ---
            if (uploadedFileName != null) {
                println("🧹 正在清理上传的测试文件: $uploadedFileName")
                val isDeleted = cloudAssetManager.deleteFile(uploadedFileName)
                assertTrue(isDeleted, "删除文件 API 调用返回失败")
                println("✅ 删除命令执行成功")

                // 验证文件是否真的从列表中消失了
                println("🔄 再次拉取列表进行删除验证...")
                cloudAssetManager.syncFiles()
                
                val remainingAssets = cloudAssetManager.assets.value
                val stillExists = remainingAssets.any { it.name == uploadedFileName }
                assertFalse(stillExists, "致命错误：删除命令已执行，但在后续的列表中文件依然存在 (幻影文件)!")
                println("✅ 验证彻底完成，测试数据已无残留。")
            }
            println("---- [测试结束] ----")
        }
    }
}
