package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.SystemMetricsSnapshot

/**
 * 跨平台系统与进程指标采集器接口
 */
expect fun captureSystemMetrics(): SystemMetricsSnapshot

/**
 * 跨平台触发垃圾回收 (GC)
 */
expect fun triggerSystemGc()
