package org.gemini.ui.forge.model

import kotlin.math.roundToInt

/**
 * 当前应用程序 (JVM 进程) 内存指标
 */
data class ProcessMemoryMetrics(
    /** 实际正在使用的堆内存大小 (字节) */
    val heapUsedBytes: Long,
    /** JVM 已向系统提交申请的堆内存 (字节) */
    val heapCommittedBytes: Long,
    /** JVM 启动参数配置的最大堆上限 (字节, -Xmx) */
    val heapMaxBytes: Long,
    /** 非堆/元空间内存占用 (字节) */
    val nonHeapUsedBytes: Long
) {
    /** 堆内存使用百分比 (0.0 ~ 100.0) */
    val heapUsagePercent: Float
        get() = if (heapMaxBytes > 0) ((heapUsedBytes.toDouble() / heapMaxBytes.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f) else 0f
}

/**
 * 宿主电脑物理内存指标
 */
data class SystemMemoryMetrics(
    /** 电脑总物理内存大小 (字节) */
    val totalPhysicalBytes: Long,
    /** 电脑当前可用空闲物理内存 (字节) */
    val freePhysicalBytes: Long,
    /** 电脑当前已占用物理内存 (字节) */
    val usedPhysicalBytes: Long
) {
    /** 整机物理内存使用百分比 (0.0 ~ 100.0) */
    val usagePercent: Float
        get() = if (totalPhysicalBytes > 0) ((usedPhysicalBytes.toDouble() / totalPhysicalBytes.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f) else 0f
}

/**
 * CPU 负载指标
 */
data class CpuMetrics(
    /** 当前应用程序进程占用的 CPU 百分比 (0.0 ~ 100.0) */
    val processCpuPercent: Float,
    /** 宿主电脑整机总 CPU 使用率百分比 (0.0 ~ 100.0) */
    val systemCpuPercent: Float,
    /** 宿主系统逻辑 CPU 核心数 */
    val availableProcessors: Int
)

/**
 * 系统与进程性能监控快照 (单帧全景数据)
 */
data class SystemMetricsSnapshot(
    val processMemory: ProcessMemoryMetrics,
    val systemMemory: SystemMemoryMetrics,
    val cpu: CpuMetrics,
    val activeThreads: Int,
    val gcCount: Long,
    val gcTimeMs: Long,
    val timestamp: Long = 0L
) {
    /**
     * 综合负载严重度评级 (0: 正常绿色, 1: 预警黄色, 2: 告警红色)
     */
    val severityLevel: Int
        get() {
            val maxLoad = maxOf(
                processMemory.heapUsagePercent,
                systemMemory.usagePercent,
                cpu.systemCpuPercent
            )
            return when {
                maxLoad >= 85f -> 2 // 红色告警
                maxLoad >= 70f -> 1 // 黄色预警
                else -> 0           // 绿色健康
            }
        }
}

/**
 * 字节友好格式化 (如 450 MB, 16.0 GB)
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> {
            val rounded = (gb * 10).roundToInt() / 10.0
            "${rounded} GB"
        }
        mb >= 1.0 -> {
            val rounded = mb.roundToInt()
            "${rounded} MB"
        }
        else -> {
            val rounded = kb.roundToInt()
            "${rounded} KB"
        }
    }
}
