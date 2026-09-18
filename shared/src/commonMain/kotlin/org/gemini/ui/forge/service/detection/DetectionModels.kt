package org.gemini.ui.forge.service.detection

import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType

/**
 * 顶级识图与模板生成大类板块
 */
enum class DetectionCategory(val displayName: String, val description: String) {
    /** 云端多模态大模型识别生成 (带 API Key、双语 Prompt 生成、艺术风格深度解析) */
    ONLINE_AI("在线 AI 生成", "基于 Google Gemini 云端多模态大模型，支持精准语义、提示词与风格深度解析"),

    /** 纯离线端侧极速识别 (免 Key、免网络、零 Token 消耗、毫秒级极速响应) */
    OFFLINE_FAST("离线极速识别", "纯本地离线算法，免 API Key、零网络依赖、毫秒级一键切分几何模块")
}

/**
 * 离线检测与对齐引擎模式
 */
enum class DetectionEngineMode(val displayName: String, val shortName: String, val description: String) {
    /** 经典物理边缘吸附 (基于 Skia 像素差分、梯度山脊与不动点锁定) */
    BASELINE_SNAPPER("经典微观吸附", "微观吸附", "纯像素矩阵梯度山脊微调，专精 0 误差像素级严丝合缝对齐"),

    /** 传统 CV 几何轮廓拓扑 (基于多尺度形态学与闭合边界几何拟合，零模型依赖) */
    CLASSIC_CV("传统 CV 几何轮廓", "传统 CV", "零外部模型依赖，基于多尺度形态学切分与空间拓扑建树，开箱即用"),

    /** 端侧轻量 AI 目标检测 (基于 ONNX Runtime JVM 与端侧神经网络) */
    ONNX_AI("端侧 AI 目标检测", "端侧 AI", "基于本地轻量神经网络，自动识别常见 UI 控件并回归语义包围盒")
}

/**
 * 离线检测出的独立组件元数据
 */
data class DetectedComponent(
    val name: String,
    val type: UIBlockType,
    val bounds: SerialRect,
    val confidence: Float = 1.0f
)
