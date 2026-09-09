package org.gemini.ui.forge.utils

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.Serializable
import org.gemini.ui.forge.data.*
import kotlin.math.ceil
import kotlin.math.min

/** 下载结果：成功携带总字节数；失败携带错误描述 */
sealed interface DownloadResult {
    data class Success(val totalBytes: Long) : DownloadResult
    data class Failed(val message: String) : DownloadResult
}

/** 单个分块的执行状态（对齐 IDM 分块视图语义） */
enum class ChunkStatus {
    /** 待开始（排队中，尚未分配连接） */
    PENDING,
    /** 活跃下载中（连接已建立） */
    ACTIVE,
    /** 本块已下载完成 */
    DONE,
    /** 断点续传时该块此前已完成，本次直接跳过 */
    SKIPPED
}

/** 单个分块的实时状态快照 */
data class ChunkState(
    /** 分块序号（0 起，按文件顺序） */
    val index: Int,
    /** 本块已下载字节数 */
    val downloadedBytes: Long,
    /** 本块总字节数；-1 表示不定长（服务器未提供） */
    val totalBytes: Long,
    /** 本块状态 */
    val status: ChunkStatus
)

/** 实时下载进度（resume 后 downloadedBytes 已包含续传起点；含分块级明细） */
data class DownloadProgress(
    /** 已下载字节数（含断点续传起点） */
    val downloadedBytes: Long,
    /** 总字节数；-1 表示服务器未提供 */
    val totalBytes: Long,
    /** 全部连接聚合的平滑速度（字节/秒） */
    val speedBps: Long,
    /** 本次断点续传起点（0 表示全新下载） */
    val resumedFrom: Long,
    /** 当前活跃连接数（正在下载的分块数） */
    val activeConnections: Int,
    /** 各分块实时状态（按分块序号排列） */
    val chunks: List<ChunkState>
)

/**
 * 通用文件下载器（IDM 级能力，commonMain 全平台通用）：
 *
 * 1. **多连接并行分块下载**：默认 8 连接各拉取独立 Range 分段，充分利用带宽；
 *    小文件（<4MB）或服务器不支持 Range 时自动退化为单连接顺序下载；
 * 2. **分块级断点续传**：每个分块写独立 chunk 文件，meta 持久化记录已完成的分块索引；
 *    中断/取消后重续时跳过已完成分块，仅重传未完成部分；
 * 3. **资源变更保护**：续传前 HEAD 校验 ETag，资源已变更则自动清空重来；
 * 4. **进度聚合**：各连接字节增量经 Channel 汇聚到聚合协程，500ms 采样 + EMA 平滑速度，
 *    回调直接给出字节数 / 总大小 / 速度（UI 层无需再自行推算）；
 * 5. **完成收口**：全部分块就绪后按序拼接为最终文件，总大小校验后落位。
 *
 * 文件操作基于项目自有跨平台 expect 体系（data 包 internal 函数），
 * 流式写出使用 kotlinx-io sink（与 UpdateService 同款实证 API）。
 *
 * 回调线程说明：[onProgress] 在 IO 协程中调用，UI 侧使用请自行切换主线程。
 *
 * @param client 注入的 Ktor 客户端；默认使用内置配置（连接 15s / 请求 30 分钟 / 允许重定向）
 */
class FileDownloader(private val client: HttpClient = defaultClient()) {

    /**
     * 下载文件到目标路径（目标已存在时幂等直接返回成功）。
     *
     * @param url 下载直链
     * @param destination 目标文件路径（完成后落位）
     * @param destinationDir 目标文件所在目录（用于生成 .part 临时目录与拼接）
     * @param fileName 目标文件名（与 [destination] 一致，用于拼接命名）
     * @param concurrency 并行连接数（默认 8，与 IDM 默认一致；实际取值受文件大小约束）
     * @param onProgress 实时进度回调（IO 线程）
     */
    suspend fun download(
        url: String,
        destinationDir: String,
        fileName: String,
        concurrency: Int = DEFAULT_CONCURRENCY,
        onProgress: (DownloadProgress) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.Default) {
        val destAbsPath = "$destinationDir/$fileName"
        try {
            downloadInternal(url, destinationDir, fileName, destAbsPath, concurrency, onProgress)
        } catch (e: CancellationException) {
            // 协程取消必须原样上抛，保证调用方的取消语义
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载失败: $url", e)
            DownloadResult.Failed(e.message ?: e::class.simpleName ?: "下载异常")
        }
    }

    // ---------- 内部实现 ----------

    private suspend fun downloadInternal(
        url: String,
        destinationDir: String,
        fileName: String,
        destAbsPath: String,
        concurrency: Int,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadResult {
        // 0. 目标已存在：幂等返回
        if (isFileExistsInternal(destAbsPath)) {
            return DownloadResult.Success(-1L)
        }

        val partDir = "$destinationDir/$fileName.part"
        val metaPath = "$partDir/$META_FILE_NAME"

        // 1. 探测资源：Range 试探拿总大小 / ETag / 分块支持性
        val probe = client.prepareGet(url) {
            header(HttpHeaders.Range, "bytes=0-0")
            timeout { requestTimeoutMillis = PROBE_TIMEOUT_MS }
        }.execute()
        val etag = probe.headers[HttpHeaders.ETag]
        val contentRangeHeader = probe.headers[HttpHeaders.ContentRange]
        val totalBytes = contentRangeHeader?.substringAfterLast('/')?.toLongOrNull() ?: -1L
        val supportsRange = probe.status == HttpStatusCode.PartialContent && totalBytes > 0
        // 读掉并释放试探响应体（仅 1 字节），避免连接泄漏
        probe.bodyAsChannel().discard()
        AppLogger.i(TAG, "资源探测: 总大小=$totalBytes, ETag=$etag, 支持分块=$supportsRange")

        // 2. 生成分块计划（确定性算法：续传前后重算结果一致）
        val plan = if (supportsRange) {
            buildChunkPlan(totalBytes, concurrency)
        } else {
            listOf(0L to Long.MAX_VALUE)
        }

        // 3. 解析断点状态：meta 匹配且 ETag 未变则跳过已完成分块
        var completedChunks: Set<Int> = emptySet()
        if (isFileExistsInternal(partDir)) {
            val meta = loadMeta(metaPath)
            val resumable = meta != null && meta.url == url && meta.totalBytes == totalBytes &&
                    (etag == null || meta.etag == null || meta.etag == etag)
            if (resumable && meta.chunkCount == plan.size) {
                completedChunks = meta.completedChunks.toSet()
                AppLogger.i(TAG, "检测到未完成下载（${completedChunks.size}/${plan.size} 块已完成），继续续传")
            } else {
                AppLogger.w(TAG, "下载状态不可续传（资源变更或元数据不匹配），重新下载")
                deleteInternal(partDir, recursive = true)
            }
        }
        // 确保临时目录存在
        createParentDirsInternal("$partDir/placeholder")
        val resumedFrom = plan.indices.filter { it in completedChunks }.sumOf { chunkLength(plan[it], totalBytes) }

        // 4. 写入初始 meta（供中断后续传）
        saveMeta(metaPath, DownloadMeta(url, etag, totalBytes, plan.size, completedChunks.toList()))

        // 5. 并行下载各分块，结构化事件经 Channel 汇聚（分块开始/增量/完成）
        val eventChannel = Channel<ChunkEvent>(Channel.UNLIMITED)
        val metaMutex = Mutex()
        coroutineScope {
            // 聚合协程：消费分块事件，维护每块状态，计算平滑速度并广播完整快照
            val aggregator = launch {
                var downloaded = resumedFrom
                var lastStamp = 0L
                var lastBytes = resumedFrom
                var smoothSpeed = 0.0
                // 每块状态：跳过块直接标记 SKIPPED 并填入完整长度
                val chunkDownloaded = LongArray(plan.size)
                val chunkStatus = arrayOfNulls<ChunkStatus>(plan.size)
                plan.forEachIndexed { i, range ->
                    if (i in completedChunks) {
                        chunkStatus[i] = ChunkStatus.SKIPPED
                        chunkDownloaded[i] = chunkLength(range, totalBytes)
                    } else {
                        chunkStatus[i] = ChunkStatus.PENDING
                    }
                }
                fun broadcast(activeCount: Int) {
                    val chunkStates = plan.indices.map { i ->
                        ChunkState(
                            index = i,
                            downloadedBytes = chunkDownloaded[i],
                            totalBytes = if (plan[i].second == Long.MAX_VALUE) -1L else chunkLength(plan[i], totalBytes),
                            status = chunkStatus[i] ?: ChunkStatus.PENDING
                        )
                    }
                    onProgress(
                        DownloadProgress(
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes,
                            speedBps = smoothSpeed.toLong(),
                            resumedFrom = resumedFrom,
                            activeConnections = activeCount,
                            chunks = chunkStates
                        )
                    )
                }
                // 广播节流：纯字节增量事件按固定间隔广播（避免每秒数千次回调风暴拖垮 UI 调度），
                // 分块状态切换（Started/Finished）与下载结束强制立即广播
                var lastBroadcastStamp = 0L
                fun broadcastThrottled(force: Boolean) {
                    val now = org.gemini.ui.forge.getCurrentTimeMillis()
                    if (force || now - lastBroadcastStamp >= BROADCAST_INTERVAL_MS) {
                        lastBroadcastStamp = now
                        broadcast(chunkStatus.count { it == ChunkStatus.ACTIVE })
                    }
                }
                for (event in eventChannel) {
                    when (event) {
                        is ChunkEvent.Started -> chunkStatus[event.index] = ChunkStatus.ACTIVE
                        is ChunkEvent.Delta -> {
                            chunkDownloaded[event.index] += event.bytes
                            downloaded += event.bytes
                        }
                        is ChunkEvent.Finished -> chunkStatus[event.index] = ChunkStatus.DONE
                    }
                    val now = org.gemini.ui.forge.getCurrentTimeMillis()
                    if (event is ChunkEvent.Delta) {
                        if (lastStamp == 0L) {
                            lastStamp = now
                            lastBytes = downloaded
                        } else {
                            val dt = (now - lastStamp) / 1000.0
                            if (dt >= SPEED_SAMPLE_INTERVAL_SEC) {
                                val instant = (downloaded - lastBytes) / dt
                                if (instant > 0) {
                                    smoothSpeed = if (smoothSpeed <= 0.0) instant
                                    else smoothSpeed * SPEED_EMA_OLD + instant * SPEED_EMA_NEW
                                }
                                lastStamp = now
                                lastBytes = downloaded
                            }
                        }
                    }
                    broadcastThrottled(force = event !is ChunkEvent.Delta)
                }
                // 事件流结束（全部完成/失败）后强制广播最终快照，确保 UI 收尾一致
                broadcastThrottled(force = true)
            }

            // 分块并行下载（已完成分块直接跳过）
            val tasks = plan.mapIndexed { index, (start, end) ->
                async {
                    if (index in completedChunks) return@async null
                    val err = downloadChunk(url, partDir, index, start, end, eventChannel)
                    if (err == null) {
                        // 块完成：加锁持久化进度（中断后续传凭据）
                        metaMutex.withLock {
                            completedChunks = completedChunks + index
                            saveMeta(metaPath, DownloadMeta(url, etag, totalBytes, plan.size, completedChunks.toList()))
                        }
                    }
                    err
                }
            }
            val failures = tasks.awaitAll().filterNotNull()
            eventChannel.close()
            aggregator.join()
            if (failures.isNotEmpty()) {
                throw IllegalStateException("部分分块下载失败: ${failures.first()}")
            }
        }

        // 6. 按序拼接分块 → 校验总大小 → 落位 → 清理临时目录
        val assembled = assembleChunks(partDir, plan, destAbsPath, totalBytes)
        deleteInternal(partDir, recursive = true)
        if (assembled) {
            AppLogger.i(TAG, "下载完成: $destAbsPath (共 $totalBytes 字节)")
            return DownloadResult.Success(totalBytes)
        }
        return DownloadResult.Failed("分块拼接校验失败")
    }

    /** 下载单个分块到独立 chunk 文件，返回 null 表示成功 */
    private suspend fun downloadChunk(
        url: String,
        partDir: String,
        index: Int,
        rangeStart: Long,
        rangeEnd: Long,
        eventChannel: Channel<ChunkEvent>
    ): String? {
        val chunkAbsPath = "$partDir/$CHUNK_FILE_PREFIX$index"
        return try {
            eventChannel.trySend(ChunkEvent.Started(index))
            val response = client.prepareGet(url) {
                if (rangeEnd == Long.MAX_VALUE) {
                    header(HttpHeaders.Range, "bytes=$rangeStart-")
                } else {
                    header(HttpHeaders.Range, "bytes=$rangeStart-$rangeEnd")
                }
                timeout { requestTimeoutMillis = CHUNK_TIMEOUT_MS }
            }.execute()

            if (!response.status.isSuccess()) {
                return "分块 $index 响应异常: HTTP ${response.status.value}"
            }
            val channel = response.bodyAsChannel()
            val sink = SystemFileSystem.sink(Path(chunkAbsPath)).buffered()
            val buffer = ByteArray(IO_BUFFER_BYTES)
            try {
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                    eventChannel.trySend(ChunkEvent.Delta(index, read.toLong()))
                }
                sink.flush()
            } finally {
                sink.close()
            }
            eventChannel.trySend(ChunkEvent.Finished(index))
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "分块 $index 下载失败: ${e.message}"
        }
    }

    /** 按序拼接全部分块为最终文件并校验总大小；返回是否成功 */
    private suspend fun assembleChunks(partDir: String, plan: List<Pair<Long, Long>>, destAbsPath: String, totalBytes: Long): Boolean {
        val tmpPath = "$partDir/$ASSEMBLE_TMP_NAME"
        val sink = SystemFileSystem.sink(Path(tmpPath)).buffered()
        val buffer = ByteArray(IO_BUFFER_BYTES)
        var written = 0L
        try {
            for (index in plan.indices) {
                val chunkAbsPath = "$partDir/$CHUNK_FILE_PREFIX$index"
                if (!isFileExistsInternal(chunkAbsPath)) return false
                // 复用项目跨平台流式读取（Flow<ByteArray>）
                readStreamInternal(chunkAbsPath, IO_BUFFER_BYTES).collect { bytes ->
                    sink.write(bytes, 0, bytes.size)
                    written += bytes.size
                }
            }
            sink.flush()
        } finally {
            sink.close()
        }
        // 大小校验（未知总大小时仅校验非空）
        val sizeOk = if (totalBytes > 0) written == totalBytes else written > 0
        if (!sizeOk) {
            return false
        }
        // 拷贝落位（项目跨平台复制）
        return copyToInternal(tmpPath, destAbsPath)
    }

    // ---------- 元数据与分块工具 ----------

    /** 聚合协程消费的分块结构化事件（开始 / 字节增量 / 完成） */
    private sealed interface ChunkEvent {
        data class Started(val index: Int) : ChunkEvent
        data class Delta(val index: Int, val bytes: Long) : ChunkEvent
        data class Finished(val index: Int) : ChunkEvent
    }

    /** 断点元数据（JSON 持久化在 part 目录内，记录已完成的分块索引） */
    @Serializable
    private data class DownloadMeta(
        val url: String,
        val etag: String? = null,
        val totalBytes: Long,
        val chunkCount: Int,
        val completedChunks: List<Int> = emptyList()
    )

    private suspend fun loadMeta(metaPath: String): DownloadMeta? = try {
        if (!isFileExistsInternal(metaPath)) return null
        val bytes = readBytesInternal(metaPath) ?: return null
        looseJson.decodeFromString(DownloadMeta.serializer(), bytes.decodeToString())
    } catch (e: Exception) {
        AppLogger.w(TAG, "读取下载元数据失败: ${e.message}")
        null
    }

    private suspend fun saveMeta(metaPath: String, meta: DownloadMeta) {
        try {
            writeBytesInternal(metaPath, looseJson.encodeToString(DownloadMeta.serializer(), meta).encodeToByteArray())
        } catch (e: Exception) {
            AppLogger.w(TAG, "写入下载元数据失败: ${e.message}")
        }
    }

    /** 计算分块长度（未知总大小的单块场景返回 -1 表示不定长） */
    private fun chunkLength(range: Pair<Long, Long>, totalBytes: Long): Long {
        if (range.second == Long.MAX_VALUE) return if (totalBytes > 0) totalBytes else 0L
        return range.second - range.first + 1
    }

    /** 确定性分块计划：块数 = min(concurrency, ceil(总大小/最小分块))，均分边界 */
    private fun buildChunkPlan(totalBytes: Long, concurrency: Int): List<Pair<Long, Long>> {
        val chunkCount = min(concurrency.toLong(), ceil(totalBytes.toDouble() / MIN_CHUNK_BYTES).toLong()).toInt()
            .coerceAtLeast(1)
        val base = totalBytes / chunkCount
        val remainder = totalBytes % chunkCount
        val plan = mutableListOf<Pair<Long, Long>>()
        var start = 0L
        for (i in 0 until chunkCount) {
            val length = base + if (i < remainder) 1L else 0L
            val end = start + length - 1
            plan.add(start to end)
            start = end + 1
        }
        return plan
    }

    private companion object {
        const val TAG = "FileDownloader"
        /** 默认并行连接数（对齐 IDM 默认 8 连接） */
        const val DEFAULT_CONCURRENCY = 8
        /** 单块最小字节数（4MB）：小文件自动减少连接数 */
        const val MIN_CHUNK_BYTES = 4L * 1024 * 1024
        /** 下载缓冲区 16KB */
        const val IO_BUFFER_BYTES = 16 * 1024
        /** 探测请求超时 15 秒 */
        const val PROBE_TIMEOUT_MS = 15_000L
        /** 单块下载超时 30 分钟 */
        const val CHUNK_TIMEOUT_MS = 30 * 60_000L
        /** 速度采样最小间隔（秒） */
        const val SPEED_SAMPLE_INTERVAL_SEC = 0.5

        /** 进度广播节流间隔（毫秒）：字节增量事件最多按此频率回调，避免高频回调风暴 */
        const val BROADCAST_INTERVAL_MS = 100L
        /** 速度 EMA 平滑权重（旧值 0.4 / 新值 0.6） */
        const val SPEED_EMA_OLD = 0.4
        const val SPEED_EMA_NEW = 0.6
        const val CHUNK_FILE_PREFIX = "chunk-"
        const val META_FILE_NAME = "meta.json"
        const val ASSEMBLE_TMP_NAME = "assemble.tmp"
    }
}

/** FileDownloader 默认 HttpClient：连接 15s / 请求 30 分钟 / 允许重定向 */
private fun defaultClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 30 * 60_000L
        connectTimeoutMillis = 15_000L
    }
    install(HttpRedirect) {
        checkHttpMethod = false
    }
}
