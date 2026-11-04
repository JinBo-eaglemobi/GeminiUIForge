package org.gemini.ui.forge.ui.uistudio.verify.model

/**
 * AI 原图去背 Task 单个大步骤的数据模型。
 *
 * @property stepIndex 步骤序号 (1-based)
 * @property title 步骤主标题（如 "读取并解析待处理原图"）
 * @property status 当前执行状态（等待中/执行中/已完成）
 * @property detail 动态状态细节或日志输出（如 "正在高精度去背推理: [12/31]"）
 * @property durationSeconds 该大步骤自身耗时秒数（进行中为实时跳动耗时，完成后为最终耗时）
 * @property progressRatio 子进度比例 (0.0f ~ 1.0f)，用于呈现细粒度进度条，为 null 时不显示
 */
data class MattingStepItem(
    val stepIndex: Int,
    val title: String,
    val status: MattingStepStatus = MattingStepStatus.PENDING,
    val detail: String = "",
    val durationSeconds: Float = 0f,
    val progressRatio: Float? = null
)
