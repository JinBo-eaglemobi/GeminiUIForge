package org.gemini.ui.forge.service.detection.onnx

/**
 * 跨平台端侧 ONNX 模型权重管理与系统环境桥接器 (KMP Expect)
 *
 * 在通用源码集 (commonMain) 中声明规范，由各平台源码集 (jvmMain, androidMain, jsMain, iosMain) 分别实现。
 */
expect object ModelDownloadManager {
    /** 检查本地端侧 ONNX 权重文件是否已就绪 */
    fun isModelReady(): Boolean

    /** 打开系统原生文件管理器并定位到模型目录 */
    fun openModelDirectoryInSystemExplorer()
}
