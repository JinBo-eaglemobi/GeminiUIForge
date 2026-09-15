package org.gemini.ui.forge.service

import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.*

actual fun captureSystemMetrics(): SystemMetricsSnapshot {
    val processMemory = ProcessMemoryMetrics(
        heapUsedBytes = 64 * 1024 * 1024L,
        heapCommittedBytes = 128 * 1024 * 1024L,
        heapMaxBytes = 256 * 1024 * 1024L,
        nonHeapUsedBytes = 0L
    )
    val systemMemory = SystemMemoryMetrics(
        totalPhysicalBytes = 6L * 1024 * 1024 * 1024,
        freePhysicalBytes = 3L * 1024 * 1024 * 1024,
        usedPhysicalBytes = 3L * 1024 * 1024 * 1024
    )
    val cpu = CpuMetrics(
        processCpuPercent = 0.5f,
        systemCpuPercent = 5.0f,
        availableProcessors = 6
    )
    return SystemMetricsSnapshot(
        processMemory = processMemory,
        systemMemory = systemMemory,
        cpu = cpu,
        activeThreads = 4,
        gcCount = 0L,
        gcTimeMs = 0L,
        timestamp = getCurrentTimeMillis()
    )
}

actual fun triggerSystemGc() {
    // iOS 原生运行时无直接 JVM gc
}
