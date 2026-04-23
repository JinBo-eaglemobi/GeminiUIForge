package org.gemini.ui.forge.service

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gemini.ui.forge.utils.calculateMd5
import org.gemini.ui.forge.data.remote.ApiConfig
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.model.api.gemini.file.GeminiFile
import org.gemini.ui.forge.model.api.gemini.file.GeminiFileListResponse
import org.gemini.ui.forge.model.api.gemini.file.GeminiFileUploadResponse
import org.gemini.ui.forge.utils.AppLogger

/**
 * 云端资产管理器 (Cloud Asset Manager)
 * 负责与 Gemini File API 通信，管理上传的参考图片生命周期，
 * 提供基于 MD5 指纹的智能去重、状态轮询和缓存同步功能。
 */
class CloudAssetManager(private val configManager: ConfigManager) {

    private val httpClient = NetworkClient.shared

    // 缓存当前的云端文件列表，UI 可以 observe 这个 StateFlow
    private val _assets = MutableStateFlow<List<GeminiFile>>(emptyList())
    val assets: StateFlow<List<GeminiFile>> = _assets.asStateFlow()

    /**
     * 获取有效的 API Key，如果未配置则抛出异常。
     */
    private suspend fun getApiKeyOrThrow(): String {
        return configManager.loadKey("GEMINI_API_KEY") 
            ?: configManager.loadGlobalGeminiKey() 
            ?: throw IllegalStateException("未找到 Gemini API Key，请先配置。")
    }

    /**
     * 从云端同步最新的文件列表，并更新本地 StateFlow 缓存。
     */
    suspend fun syncFiles() {
        try {
            val apiKey = getApiKeyOrThrow()
            val response: HttpResponse = httpClient.get(ApiConfig.getListFilesEndpoint(apiKey))
            if (response.status.isSuccess()) {
                val listResponse = response.body<GeminiFileListResponse>()
                _assets.update { listResponse.files ?: emptyList() }
                AppLogger.d("CloudAssetManager", "Successfully synced ${_assets.value.size} files from cloud.")
            } else {
                AppLogger.e("CloudAssetManager", "Failed to sync files: ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.e("CloudAssetManager", "Error syncing files: ${e.message}", e)
        }
    }

    /**
     * 删除指定的云端文件。
     */
    suspend fun deleteFile(fileName: String): Boolean {
        return try {
            val apiKey = getApiKeyOrThrow()
            val response: HttpResponse = httpClient.delete(ApiConfig.getFileEndpoint(fileName, apiKey))
            if (response.status.isSuccess()) {
                _assets.update { currentList ->
                    currentList.filterNot { it.name == fileName }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取指定文件的最新详情状态。
     */
    suspend fun getFileDetail(fileName: String): GeminiFile? {
        return try {
            val apiKey = getApiKeyOrThrow()
            val response: HttpResponse = httpClient.get(ApiConfig.getFileEndpoint(fileName, apiKey))
            if (response.status.isSuccess()) {
                response.body<GeminiFile>()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 智能上传文件。
     * 1. 如果本地资产列表为空，先尝试从云端同步一次。
     * 2. 计算文件 MD5 指纹。
     * 3. 检查云端是否已有具备相同指纹且处于 ACTIVE 状态的文件。
     * 4. 如果没有，则执行可续传上传。
     * 
     * @param displayName 文件的显示名称
     * @param fileBytes 文件的字节数组
     * @param mimeType 文件的类型
     * @param onProgress 进度回调 (进度 0.0-1.0, 描述文本)
     * @return 文件的云端 URI
     */
    suspend fun getOrUploadFile(
        displayName: String, 
        fileBytes: ByteArray, 
        mimeType: String = "image/png",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): String? {
        // 1. 自动预同步：如果本地没有任何资产记录，先拉取一次云端状态，确保去重判断基于最新数据
        if (_assets.value.isEmpty()) {
            onProgress(0.01f, "正在同步云端列表...")
            syncFiles()
        }

        // 2. 计算内容指纹 (MD5) 用于智能匹配
        val fingerprint = fileBytes.calculateMd5()
        
        // 3. 智能匹配：在现有列表中寻找相同指纹且就绪的文件
        val existingMatch = _assets.value.find { 
            (it.displayName?.contains(fingerprint) == true) && it.state == "ACTIVE" 
        }
        
        if (existingMatch?.uri != null) {
            AppLogger.d("CloudAssetManager", "Smart Match: Reusing cloud asset with fingerprint $fingerprint")
            onProgress(1.0f, "内容匹配成功，已复用云端资产")
            return existingMatch.uri
        }

        // 4. 执行上传：将指纹作为 displayName 的一部分发送，方便下次匹配
        val uniqueDisplayName = "forge_${fingerprint}_$displayName"
        AppLogger.d("CloudAssetManager", "No cloud match. Uploading new file. Fingerprint: $fingerprint")
        val apiKey = getApiKeyOrThrow()
        
        try {
            onProgress(0.05f, "准备初始化上传...")
            // 阶段 1: 初始化会话
            val initResponse: HttpResponse = httpClient.post(ApiConfig.getUploadFileEndpoint(apiKey)) {
                header("X-Goog-Upload-Protocol", "resumable")
                header("X-Goog-Upload-Command", "start")
                header("X-Goog-Upload-Header-Content-Length", fileBytes.size.toString())
                header("X-Goog-Upload-Header-Content-Type", mimeType)
                contentType(ContentType.Application.Json)
                setBody("{\"file\": {\"displayName\": \"$uniqueDisplayName\"}}")
            }

            if (!initResponse.status.isSuccess()) return null
            val uploadUrl = initResponse.headers["x-goog-upload-url"] ?: return null

            // 阶段 2: 发送数据并监听进度
            val uploadResponse: HttpResponse = httpClient.post(uploadUrl) {
                header("Content-Length", fileBytes.size.toString())
                header("X-Goog-Upload-Offset", "0")
                header("X-Goog-Upload-Command", "upload, finalize")
                setBody(fileBytes)
                
                onUpload { bytesSent, totalBytes ->
                    val total = totalBytes ?: 0L
                    if (total > 0L) {
                        val p = (bytesSent.toDouble() / total.toDouble()).toFloat()
                        onProgress(0.1f + (p * 0.8f), "正在同步数据 (${(p * 100).toInt()}%)...")
                    }
                }
            }

            if (!uploadResponse.status.isSuccess()) return null

            val uploadResponseObj = uploadResponse.body<GeminiFileUploadResponse>()
            var uploadedFile = uploadResponseObj.file

            // 3. 轮询等待 ACTIVE
            val maxRetries = 15
            var retries = 0
            while (uploadedFile.state == "PROCESSING" && retries < maxRetries) {
                val waitSec = (retries + 1) * 2
                onProgress(0.9f + (retries.toFloat() / maxRetries) * 0.09f, "云端处理中 (${waitSec}s)...")
                delay(2000L)
                val updated = getFileDetail(uploadedFile.name) ?: break
                uploadedFile = updated
                retries++
            }

            if (uploadedFile.state != "ACTIVE") return null

            // 更新并返回
            _assets.update { current -> current + uploadedFile }
            onProgress(1.0f, "同步已就绪")
            return uploadedFile.uri

        } catch (e: Exception) {
            AppLogger.e("CloudAssetManager", "Upload error: ${e.message}", e)
            return null
        }
    }

    /**
     * [通用组件] 将图片字节数组包装为适用于 Gemini API 的 JSON Part。
     * 自动尝试上传云端，如果成功则返回 `fileData` 格式；失败或降级则返回 `inlineData` (Base64) 格式。
     */
    suspend fun buildGeminiImagePart(
        displayName: String,
        bytes: ByteArray,
        mimeType: String,
        onLog: (String) -> Unit = {}
    ): JsonObject {
        val fileUri = getOrUploadFile(displayName, bytes, mimeType) { _, status -> onLog("CloudAsset: $status") }
        return if (fileUri != null) {
            onLog("✅ 云端同步成功")
            buildJsonObject {
                put("fileData", buildJsonObject {
                    put("mimeType", mimeType)
                    put("fileUri", fileUri)
                })
            }
        } else {
            onLog("⚠️ 触发 Base64 降级补偿")
            buildJsonObject {
                put("inlineData", buildJsonObject {
                    put("mimeType", mimeType)
                    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                    put("data", kotlin.io.encoding.Base64.Default.encode(bytes))
                })
            }
        }
    }

    /**
     * [通用组件] 将图片字节数组包装为适用于 Imagen API 的 JSON 对象。
     * 自动尝试上传云端，如果成功则返回 `gcsUri` 格式；失败或降级则返回 `bytesBase64Encoded` 格式。
     */
    suspend fun buildImagenImagePart(
        displayName: String,
        bytes: ByteArray,
        mimeType: String,
        onLog: (String) -> Unit = {}
    ): JsonObject {
        val fileUri = getOrUploadFile(displayName, bytes, mimeType) { _, status -> onLog("CloudAsset: $status") }
        return if (fileUri != null) {
            onLog("✅ 云端同步成功 (GCS)")
            buildJsonObject {
                put("gcsUri", fileUri)
            }
        } else {
            onLog("⚠️ 触发 Base64 降级补偿")
            buildJsonObject {
                @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                put("bytesBase64Encoded", kotlin.io.encoding.Base64.Default.encode(bytes))
            }
        }
    }
}
