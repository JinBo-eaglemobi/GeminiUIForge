package org.gemini.ui.forge.service.detection.onnx

import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.userHomePath
import java.io.File

/**
 * JVM 桌面端 ONNX 模型权重与系统环境桥接实现
 *
 * 统一委托至领域层 [OnnxModelService] 与平台工具 [getPlatform]，彻底杜绝重复手搓底层逻辑。
 */
actual object ModelDownloadManager {

    actual fun isModelReady(): Boolean {
        val file = File(OnnxModelService.getModelFilePath())
        return file.exists() && file.isFile && file.length() > 1024 * 1024
    }

    actual fun openModelDirectoryInSystemExplorer() {
        val dir = OnnxModelService.getModelDirPath()
        val dirFile = File(dir)
        if (!dirFile.exists()) {
            dirFile.mkdirs()
        }
        getPlatform().openInFileExplorer(dir)
    }
}
