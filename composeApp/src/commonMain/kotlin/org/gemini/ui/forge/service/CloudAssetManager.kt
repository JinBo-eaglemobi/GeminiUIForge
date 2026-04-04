package org.gemini.ui.forge.service

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.gemini.ui.forge.domain.gemini.file.GeminiFile
import org.gemini.ui.forge.domain.gemini.file.GeminiFileListResponse
import org.gemini.ui.forge.domain.gemini.file.GeminiFileUploadResponse
import org.gemini.ui.forge.utils.AppLogger

/**
 * 云端资产管理器 (Cloud Asset Manager)
 * 负责与 Gemini File API 通信，管理上传的参考图片生命周期，
 * 提供智能去重、状态轮询和缓存同步功能。
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
                AppLogger.e("CloudAssetManager", "Failed to sync files: ${response.status} - ${response.bodyAsText()}")
            }
        } catch (e: Exception) {
            AppLogger.e("CloudAssetManager", "Error syncing files: ${e.message}", e)
        }
    }

    /**
     * 删除指定的云端文件。
     * @param fileName 文件的唯一标识符（例如 "files/xxx"）
     */
    suspend fun deleteFile(fileName: String): Boolean {
        return try {
            val apiKey = getApiKeyOrThrow()
            val response: HttpResponse = httpClient.delete(ApiConfig.getFileEndpoint(fileName, apiKey))
            if (response.status.isSuccess()) {
                // 删除成功后，从本地缓存中移除
                _assets.update { currentList ->
                    currentList.filterNot { it.name == fileName }
                }
                AppLogger.d("CloudAssetManager", "Successfully deleted file: $fileName")
                true
            } else {
                AppLogger.e("CloudAssetManager", "Failed to delete file $fileName: ${response.status} - ${response.bodyAsText()}")
                false
            }
        } catch (e: Exception) {
            AppLogger.e("CloudAssetManager", "Error deleting file $fileName: ${e.message}", e)
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
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("CloudAssetManager", "Error getting file detail: ${e.message}", e)
            null
        }
    }

    /**
     * 智能上传文件。
     * 1. 检查缓存中是否已有同名且 ACTIVE 的文件。
     * 2. 如果没有，则上传字节流，并提供实时进度反馈。
     * 3. 上传后如果状态为 PROCESSING，则启动轮询直到其变为 ACTIVE 或 FAILED。
     * 
     * @param displayName 文件的显示名称，用于防重校验
     * @param fileBytes 文件的字节数组
     * @param mimeType 文件的类型，如 "image/png"
     * @param onProgress 进度回调 (进度百分比 0.0-1.0, 状态描述文本)
     * @return 返回上传成功且状态为 ACTIVE 的文件的 URI；如果失败则抛出异常或返回 null
     */
    suspend fun getOrUploadFile(
        displayName: String, 
        fileBytes: ByteArray, 
        mimeType: String = "image/png",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): String? {
        // 1. 智能去重：检查本地缓存中是否已有同名且状态为 ACTIVE 的文件
        val existingActiveFile = _assets.value.find { 
            it.displayName == displayName && it.state == "ACTIVE" 
        }
        if (existingActiveFile?.uri != null) {
            AppLogger.d("CloudAssetManager", "Reusing existing active file: $displayName -> ${existingActiveFile.uri}")
            onProgress(1.0f, "已复用现有资源")
            return existingActiveFile.uri
        }

        // 2. 执行上传
        AppLogger.d("CloudAssetManager", "Uploading new file: $displayName (${fileBytes.size} bytes)")
        val apiKey = getApiKeyOrThrow()
        
        try {
            onProgress(0.05f, "正在初始化上传...")
            // --- 阶段 1：初始化上传会话并发送元数据 ---
            val initResponse: HttpResponse = httpClient.post(ApiConfig.getUploadFileEndpoint(apiKey)) {
                header("X-Goog-Upload-Protocol", "resumable")
                header("X-Goog-Upload-Command", "start")
                header("X-Goog-Upload-Header-Content-Length", fileBytes.size.toString())
                header("X-Goog-Upload-Header-Content-Type", mimeType)
                contentType(ContentType.Application.Json)
                // 发送 JSON 格式的元数据，通知服务器我们即将上传的文件的显示名称
                setBody("{\"file\": {\"displayName\": \"$displayName\"}}")
            }

            if (!initResponse.status.isSuccess()) {
                AppLogger.e("CloudAssetManager", "Upload initialization failed: ${initResponse.status} - ${initResponse.bodyAsText()}")
                return null
            }

            // 从响应头中提取专用的数据上传 URL
            val uploadUrl = initResponse.headers["x-goog-upload-url"]
            if (uploadUrl.isNullOrBlank()) {
                AppLogger.e("CloudAssetManager", "Upload initialization succeeded but x-goog-upload-url header is missing.")
                return null
            }

            AppLogger.d("CloudAssetManager", "Upload session initialized. Upload URL obtained.")

            // --- 阶段 2：发送纯二进制文件数据，集成进度监听 ---
            val uploadResponse: HttpResponse = httpClient.post(uploadUrl) {
                header("Content-Length", fileBytes.size.toString())
                header("X-Goog-Upload-Offset", "0")
                header("X-Goog-Upload-Command", "upload, finalize")
                setBody(fileBytes)

                // 实时监听字节传输进度
                onUpload { bytesSent, totalBytes ->
                    val total = totalBytes ?: 0L
                    if (total > 0L) {
                        // 上传阶段分配进度条的 0.1 到 0.9 范围
                        val uploadProgress = (bytesSent.toDouble() / total.toDouble()).toFloat()
                        val totalProgress = 0.1f + (uploadProgress * 0.8f)
                        onProgress(totalProgress, "正在传输数据 (${(uploadProgress * 100).toInt()}%)...")
                    }
                }
            }

            if (!uploadResponse.status.isSuccess()) {
                AppLogger.e("CloudAssetManager", "File upload failed: ${uploadResponse.status} - ${uploadResponse.bodyAsText()}")
                return null
            }

            val uploadResponseObj = uploadResponse.body<GeminiFileUploadResponse>()
            var uploadedFile = uploadResponseObj.file
            AppLogger.d("CloudAssetManager", "Upload response received. File name: ${uploadedFile.name}, State: ${uploadedFile.state}")

            // 3. 轮询等待处理完成 (PROCESSING -> ACTIVE)
            val maxRetries = 15 // 最多等 30 秒 (15 * 2s)
            var retries = 0
            while (uploadedFile.state == "PROCESSING" && retries < maxRetries) {
                val waitTime = (retries + 1) * 2
                onProgress(0.9f + (retries.toFloat() / maxRetries) * 0.1f, "服务器正在处理中 (${waitTime}s)...")
                delay(2000L) // 等待 2 秒
                
                val updatedFile = getFileDetail(uploadedFile.name)
                if (updatedFile != null) {
                    uploadedFile = updatedFile
                }
                retries++
            }

            if (uploadedFile.state == "FAILED") {
                AppLogger.e("CloudAssetManager", "File processing failed on server side.")
                return null
            }

            if (uploadedFile.state != "ACTIVE") {
                AppLogger.e("CloudAssetManager", "File processing timeout. Final state: ${uploadedFile.state}")
                return null
            }

            // 更新缓存
            _assets.update { current -> current + uploadedFile }
            
            AppLogger.d("CloudAssetManager", "File is ACTIVE and ready to use. URI: ${uploadedFile.uri}")
            onProgress(1.0f, "同步已就绪")
            return uploadedFile.uri

        } catch (e: Exception) {
            AppLogger.e("CloudAssetManager", "Error during file upload: ${e.message}", e)
            return null
        }
    }
}
