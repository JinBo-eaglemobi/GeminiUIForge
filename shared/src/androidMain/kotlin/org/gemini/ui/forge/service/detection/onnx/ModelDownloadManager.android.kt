package org.gemini.ui.forge.service.detection.onnx

/**
 * Android 端端侧模型桥接实现 (兜底降级)
 */
actual object ModelDownloadManager {
    actual fun isModelReady(): Boolean = false
    actual fun openModelDirectoryInSystemExplorer() {}
}
