package org.gemini.ui.forge.service.detection.onnx

/**
 * Web 端 (JS) 端侧模型桥接实现 (兜底降级)
 */
actual object ModelDownloadManager {
    actual fun isModelReady(): Boolean = false
    actual fun openModelDirectoryInSystemExplorer() {}
}
