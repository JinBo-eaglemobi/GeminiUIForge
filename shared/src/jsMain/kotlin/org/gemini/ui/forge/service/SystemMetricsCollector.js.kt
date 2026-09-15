package org.gemini.ui.forge.service

import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.*

actual fun captureSystemMetrics(): SystemMetricsSnapshot {
    val processMemory = ProcessMemoryMetrics(
        heapUsedBytes = 128 * 1024 * 1024L,
        heapCommittedBytes = 256 * 1024 * 1024L,
        heapMaxBytes = 512 * 1024 * 1024L,
        nonHeapUsedBytes = 0L
    )
    val systemMemory = SystemMemoryMetrics(
        totalPhysicalBytes = 8L * 1024 * 1024 * 1024,
        freePhysicalBytes = 4L * 1024 * 1024 * 1024,
        usedPhysicalBytes = 4L * 1024 * 1024 * 1024
    )
    val cpu = CpuMetrics(
        processCpuPercent = 1.5f,
        systemCpuPercent = 8.0f,
        availableProcessors = 4
    )
    return SystemMetricsSnapshot(
        processMemory = processMemory,
        systemMemory = systemMemory,
        cpu = cpu,
        activeThreads = 1,
        gcCount = 0L,
        gcTimeMs = 0L,
        timestamp = getCurrentTimeMillis()
    )
}

actual fun triggerSystemGc() {
    // 浏览器 JS 引擎不支持手动调用 System.gc()
}
