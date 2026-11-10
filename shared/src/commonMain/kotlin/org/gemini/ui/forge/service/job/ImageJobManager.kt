package org.gemini.ui.forge.service.job

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.readLocalFileBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * 异步生图任务中枢 (Image Job Manager)
 *
 * 全局单例管理 AI 异步文生图作业的提交、后台协程调度、实时进度追踪与结果获取。
 */
object ImageJobManager {

    private const val TAG = "ImageJobManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    private val jobs = mutableMapOf<String, ImageJobRecord>()

    /**
     * 提交一个新的异步生图任务
     *
     * @return 返回生成的全局唯一 jobId
     */
    fun submitJob(
        prompt: String,
        projectName: String,
        aspectRatio: String,
        targetWidth: Float,
        targetHeight: Float,
        isPng: Boolean,
        targetModel: GeminiModel,
        effectiveModelName: String,
        apiKey: String,
        customSubPath: String,
        customFileName: String?,
        addToView: Boolean,
        storage: LocalFileStorage = LocalFileStorage()
    ): String {
        val randomSuffix = Random.nextInt(100000, 999999)
        val jobId = "img_job_${getCurrentTimeMillis()}_$randomSuffix"

        val initialRecord = ImageJobRecord(
            jobId = jobId,
            prompt = prompt,
            projectName = projectName,
            status = ImageJobStatus.RUNNING,
            progress = 0.05f,
            statusMessage = "任务已提交，准备启动生图模型...",
            width = targetWidth,
            height = targetHeight
        )

        scope.launch {
            mutex.withLock {
                cleanupOldJobsLocked()
                jobs[jobId] = initialRecord
            }

            executeJobInternal(
                jobId = jobId,
                prompt = prompt,
                projectName = projectName,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                isPng = isPng,
                targetModel = targetModel,
                effectiveModelName = effectiveModelName,
                apiKey = apiKey,
                customSubPath = customSubPath,
                customFileName = customFileName,
                addToView = addToView,
                storage = storage
            )
        }

        return jobId
    }

    /**
     * 获取指定生图任务的当前最新记录
     */
    suspend fun getJob(jobId: String): ImageJobRecord? {
        return mutex.withLock { jobs[jobId] }
    }

    /**
     * 同步等待指定生图任务到达终态（SUCCESS / FAILED）
     *
     * 遵循三级可配置规范：调用端入参可覆盖 + 出厂默认值 + 物理安全夹具（超时上限 300s），
     * 供 [org.gemini.ui.forge.service.tool.shared.GenerateImageTool] 的 waitSeconds 同步语义与
     * UI Studio Agent 生图轮直调链路使用。
     *
     * @param jobId 任务唯一标识
     * @param timeoutSeconds 最长等待秒数（出厂默认 120s，物理夹具钳制在 5~300s 区间）
     * @param pollIntervalMs 轮询间隔毫秒数
     * @return 终态任务记录（SUCCESS 或 FAILED）
     * @throws IllegalStateException 等待超时或任务记录丢失时抛出
     */
    suspend fun awaitJobCompleted(
        jobId: String,
        timeoutSeconds: Int = 120,
        pollIntervalMs: Long = 500L
    ): ImageJobRecord {
        // 物理安全夹具：防止外部误配置引发无限等待死锁
        val clampedTimeoutSeconds = timeoutSeconds.coerceIn(5, 300)
        val deadline = getCurrentTimeMillis() + clampedTimeoutSeconds * 1000L

        while (getCurrentTimeMillis() < deadline) {
            val record = getJob(jobId)
            if (record == null) {
                // 任务记录可能被清理策略回收，视为异常终态
                throw IllegalStateException("生图任务记录不存在或已被清理: $jobId")
            }
            if (record.status == ImageJobStatus.SUCCESS || record.status == ImageJobStatus.FAILED) {
                return record
            }
            delay(pollIntervalMs)
        }
        throw IllegalStateException("等待生图任务完成超时（${clampedTimeoutSeconds}s）: $jobId")
    }

    private suspend fun updateJob(jobId: String, transform: (ImageJobRecord) -> ImageJobRecord) {
        mutex.withLock {
            val existing = jobs[jobId] ?: return@withLock
            jobs[jobId] = transform(existing)
        }
    }

    private suspend fun executeJobInternal(
        jobId: String,
        prompt: String,
        projectName: String,
        targetWidth: Float,
        targetHeight: Float,
        isPng: Boolean,
        targetModel: GeminiModel,
        effectiveModelName: String,
        apiKey: String,
        customSubPath: String,
        customFileName: String?,
        addToView: Boolean,
        storage: LocalFileStorage
    ) {
        val configManager = ConfigManager()
        val cloudAssetManager = CloudAssetManager(configManager)
        val aiService = AIGenerationService(storage, cloudAssetManager, configManager)
        val mattingService = LocalMattingService(storage)

        updateJob(jobId) {
            it.copy(
                status = ImageJobStatus.RUNNING,
                progress = 0.15f,
                statusMessage = "正在初始化生图请求，调用模型: ${targetModel.displayName}..."
            )
        }

        var rawResultUri: String? = null
        try {
            updateJob(jobId) {
                it.copy(progress = 0.35f, statusMessage = "正在通过 AI 模型生成图像...")
            }

            aiService.generateImages(
                model = targetModel,
                blockType = "IMAGE",
                userPrompt = prompt,
                apiKey = apiKey,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                isPng = isPng,
                generationCount = 1,
                onLog = { logMsg ->
                    scope.launch {
                        updateJob(jobId) { it.copy(progress = 0.5f, statusMessage = logMsg) }
                    }
                },
                onImageGenerated = { rawResultUri = it }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "文生图后台执行失败, jobId=$jobId", e)
            updateJob(jobId) {
                it.copy(
                    status = ImageJobStatus.FAILED,
                    errorMessage = "AI 模型生图失败: ${e.message}",
                    completedAt = getCurrentTimeMillis()
                )
            }
            return
        }

        val finalRaw = rawResultUri
        if (finalRaw.isNullOrBlank()) {
            updateJob(jobId) {
                it.copy(
                    status = ImageJobStatus.FAILED,
                    errorMessage = "AI 生图服务未返回有效图像数据",
                    completedAt = getCurrentTimeMillis()
                )
            }
            return
        }

        @OptIn(ExperimentalEncodingApi::class)
        val initialBytes = if (finalRaw.startsWith("data:image")) {
            val pureB64 = if (finalRaw.contains(",")) finalRaw.substringAfter(",") else finalRaw
            Base64.decode(pureB64)
        } else {
            readLocalFileBytes(finalRaw)
        }

        if (initialBytes == null) {
            updateJob(jobId) {
                it.copy(
                    status = ImageJobStatus.FAILED,
                    errorMessage = "无法解析生成的图像字节数据",
                    completedAt = getCurrentTimeMillis()
                )
            }
            return
        }

        val processedBytes = if (isPng) {
            updateJob(jobId) {
                it.copy(progress = 0.75f, statusMessage = "正在执行本地 AI 透明抠图去背...")
            }
            try {
                mattingService.removeBackground(initialBytes) ?: initialBytes
            } catch (e: Throwable) {
                AppLogger.w(TAG, "透明抠图去背失败，保留原始图片, jobId=$jobId", e)
                initialBytes
            }
        } else {
            initialBytes
        }

        val ext = if (isPng) "png" else "jpg"
        var savedRelativePath: String? = null

        // 若调用方显式指定了 customFileName，则持久化落盘至 assets/image/
        if (!customFileName.isNullOrBlank()) {
            updateJob(jobId) {
                it.copy(progress = 0.9f, statusMessage = "正在保存图片至项目素材库...")
            }
            val cleanFileName = if (customFileName.endsWith(".$ext", ignoreCase = true)) customFileName else "$customFileName.$ext"
            val targetRelDir = if (customSubPath.isBlank()) {
                "templates/$projectName/assets/image"
            } else {
                "templates/$projectName/assets/image/$customSubPath"
            }
            val targetFullPath = "$targetRelDir/$cleanFileName"

            savedRelativePath = try {
                storage.saveBytesToFile(targetFullPath, processedBytes)
            } catch (e: Exception) {
                AppLogger.e(TAG, "保存资产至素材库失败: $targetFullPath", e)
                null
            }

            if (savedRelativePath != null) {
                try {
                    McpUiActionPipeline.getActiveUiStudioViewModel()?.assetLibrary?.refreshAssetLibrary()
                } catch (e: Throwable) {
                    AppLogger.w(TAG, "刷新素材库失败", e)
                }
            }
        }

        // 若指定了 addToView
        if (addToView && savedRelativePath != null) {
            try {
                val studioVm = McpUiActionPipeline.getActiveUiStudioViewModel()
                if (studioVm != null) {
                    val blockName = customFileName?.ifBlank { "ai_img_${getCurrentTimeMillis()}" } ?: "ai_img_${getCurrentTimeMillis()}"
                    studioVm.assetLibrary.addChatImageToCurrentView(
                        imageBytes = processedBytes,
                        fileName = blockName,
                        width = targetWidth,
                        height = targetHeight,
                        subPath = customSubPath,
                        isPng = isPng
                    )
                }
            } catch (e: Throwable) {
                AppLogger.w(TAG, "添加图元到当前页面失败", e)
            }
        }

        @OptIn(ExperimentalEncodingApi::class)
        val returnBase64 = Base64.encode(processedBytes)
        val mimeType = if (isPng) "image/png" else "image/jpeg"
        val dataUri = "data:$mimeType;base64,$returnBase64"

        updateJob(jobId) {
            it.copy(
                status = ImageJobStatus.SUCCESS,
                progress = 1.0f,
                statusMessage = "生图成功完成",
                base64Data = returnBase64,
                mimeType = mimeType,
                imageUri = dataUri,
                savedRelativePath = savedRelativePath,
                completedAt = getCurrentTimeMillis()
            )
        }
        AppLogger.i(TAG, "文生图任务成功完成, jobId=$jobId")
    }

    private fun cleanupOldJobsLocked() {
        if (jobs.size > 100) {
            val expiredKeys = jobs.entries
                .sortedBy { it.value.createdAt }
                .take(30)
                .map { it.key }
            expiredKeys.forEach { jobs.remove(it) }
        }
    }
}
