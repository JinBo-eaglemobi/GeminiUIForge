package org.gemini.ui.forge.service

import com.sun.management.OperatingSystemMXBean
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.*
import java.lang.management.ManagementFactory

actual fun captureSystemMetrics(): SystemMetricsSnapshot {
    // 1. JVM 运行时与堆内存指标
    val runtime = Runtime.getRuntime()
    val heapTotal = runtime.totalMemory()
    val heapFree = runtime.freeMemory()
    val heapUsed = (heapTotal - heapFree).coerceAtLeast(0L)
    val heapMax = runtime.maxMemory()

    val memoryMXBean = ManagementFactory.getMemoryMXBean()
    val nonHeapUsed = memoryMXBean.nonHeapMemoryUsage.used

    val processMemory = ProcessMemoryMetrics(
        heapUsedBytes = heapUsed,
        heapCommittedBytes = heapTotal,
        heapMaxBytes = heapMax,
        nonHeapUsedBytes = nonHeapUsed
    )

    // 2. 宿主系统物理内存与 CPU 负载指标
    val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean
    val totalPhysical = osBean?.totalMemorySize ?: 0L
    val freePhysical = osBean?.freeMemorySize ?: 0L
    val usedPhysical = (totalPhysical - freePhysical).coerceAtLeast(0L)

    val systemMemory = SystemMemoryMetrics(
        totalPhysicalBytes = totalPhysical,
        freePhysicalBytes = freePhysical,
        usedPhysicalBytes = usedPhysical
    )

    // CPU Load：系统初始采样前可能返回负数 (-1.0)，规整为 0.0
    val rawProcessCpu = osBean?.processCpuLoad ?: -1.0
    val rawSystemCpu = osBean?.cpuLoad ?: -1.0
    val processCpuPercent = if (rawProcessCpu >= 0) (rawProcessCpu * 100.0).toFloat().coerceIn(0f, 100f) else 0f
    val systemCpuPercent = if (rawSystemCpu >= 0) (rawSystemCpu * 100.0).toFloat().coerceIn(0f, 100f) else 0f
    val processors = osBean?.availableProcessors ?: runtime.availableProcessors()

    val cpu = CpuMetrics(
        processCpuPercent = processCpuPercent,
        systemCpuPercent = systemCpuPercent,
        availableProcessors = processors
    )

    // 3. 线程数与 GC 统计
    val threadCount = ManagementFactory.getThreadMXBean().threadCount
    var totalGcCount = 0L
    var totalGcTimeMs = 0L
    for (gcBean in ManagementFactory.getGarbageCollectorMXBeans()) {
        val count = gcBean.collectionCount
        if (count > 0) totalGcCount += count
        val time = gcBean.collectionTime
        if (time > 0) totalGcTimeMs += time
    }

    return SystemMetricsSnapshot(
        processMemory = processMemory,
        systemMemory = systemMemory,
        cpu = cpu,
        activeThreads = threadCount,
        gcCount = totalGcCount,
        gcTimeMs = totalGcTimeMs,
        timestamp = getCurrentTimeMillis()
    )
}

actual fun triggerSystemGc() {
    System.gc()
}
