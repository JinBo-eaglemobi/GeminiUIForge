package org.gemini.ui.forge.service.detection

import org.gemini.ui.forge.state.ui.ProjectState

/**
 * 参考大图离线识别生成完整模板工程策略接口
 */
interface ITemplateDetector {
    val mode: DetectionEngineMode
    val displayName: String

    /**
     * 对输入的参考图片执行离线分析，构建多层级模块树并组装为 ProjectState
     *
     * @param imageBytes 参考原图的二进制字节
     * @param templateName 模板工程名称
     * @param onProgress 进度回调 (0% ~ 100% 状态通知)
     * @return 构建成功的 ProjectState 实体
     */
    suspend fun detectTemplate(
        imageBytes: ByteArray,
        templateName: String,
        onProgress: (String) -> Unit = {}
    ): ProjectState
}
