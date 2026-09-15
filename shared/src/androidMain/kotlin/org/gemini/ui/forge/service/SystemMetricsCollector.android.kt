package org.gemini.ui.forge.service

import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.*

actual fun captureSystemMetrics(): SystemMetricsSnapshot {
    val runtime = Runtime.getRuntime()
    val heapTotal = runtime.totalMemory()
    val heapFree = runtime.freeMemory()
    val heapUsed = (heapTotal - heapFree).coerceAtLeast(0L)
    val heapMax = runtime.maxMemory()

    val processMemory = ProcessMemoryMetrics(
        heapUsedBytes = heapUsed,
        heapCommittedBytes = heapTotal,
        heapMaxBytes = heapMax,
        nonHeapUsedBytes = 0L
    )

    val systemMemory = SystemMemoryMetrics(
        totalPhysicalBytes = heapMax * 2,
        freePhysicalBytes = heapFree,
        usedPhysicalBytes = heapUsed
    )

    val cpu = CpuMetrics(
        processCpuPercent = 0f,
        systemCpuPercent = 0f,
        availableProcessors = runtime.availableProcessors()
    )

    return SystemMetricsSnapshot(
        processMemory = processMemory,
        systemMemory = systemMemory,
        cpu = cpu,
        activeThreads = Thread.activeCount(),
        gcCount = 0L,
        gcTimeMs = 0L,
        timestamp = getCurrentTimeMillis()
    )
}

actual fun triggerSystemGc() {
    System.gc()
}
