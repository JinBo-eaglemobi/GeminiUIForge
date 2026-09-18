package org.gemini.ui.forge.service.detection.onnx

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.data.isFileExistsInternal
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.userHomePath
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.getLocalFileSize

/**
 * 跨平台端侧 ONNX 自定义模型插槽管理服务
 *
 * 开放标准模型接入通道：允许开发者将针对特定游戏训练导出的 `ui_detector.onnx` 放入本地目录；
 * 未放入时，系统由传统 CV 引擎进行 100% 可用兜底，绝不通过虚假网络链接产生不可预期的异常。
 */
object OnnxModelService {

    const val MODEL_FILENAME = "ui_detector.onnx"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _isModelReadyFlow = MutableStateFlow(false)
    val isModelReadyFlow: StateFlow<Boolean> = _isModelReadyFlow.asStateFlow()

    init {
        scope.launch {
            checkStatus()
        }
    }

    /** 获取模型存放目录绝对路径 (如 ~/.geminiuiforge/models) */
    fun getModelDirPath(): String {
        return "$userHomePath/.geminiuiforge/models"
    }

    /** 获取目标模型文件绝对路径 */
    fun getModelFilePath(): String {
        return "${getModelDirPath()}/$MODEL_FILENAME"
    }

    /**
     * 检查当前本地自定义模型文件是否存在且合法 (> 1MB)
     */
    suspend fun checkStatus(): Boolean {
        val targetPath = getModelFilePath()
        val exists = isFileExistsInternal(targetPath)
        val ready = if (exists) {
            val size = getLocalFileSize(targetPath)
            size > 1024 * 1024
        } else {
            false
        }
        _isModelReadyFlow.value = ready
        return ready
    }

    /**
     * 跨平台在系统原生文件管理器中打开模型目录，方便用户直接离线放入专属训练的 ui_detector.onnx
     */
    fun openModelDirectoryInSystemExplorer() {
        val dir = getModelDirPath()
        getPlatform().openInFileExplorer(dir)
        AppLogger.i("OnnxModelService", "已调度系统文件管理器打开模型目录: $dir")
        scope.launch {
            delay(1000)
            checkStatus()
        }
    }
}
