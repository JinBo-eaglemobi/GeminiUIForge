package org.gemini.ui.forge.model.gameproject

/**
 * 克隆流程的实时进度事件。
 * UI 层（CloneProgressDialog）依据 step 高亮当前步骤，logLines 逐行滚动显示底层 git 输出。
 */
data class CloneProgress(
    /** 当前执行到的步骤（默认处于第一步之前，由流程起始事件覆盖） */
    val step: CloneStep = CloneStep.CREATE_FOLDER,
    /** 步骤的补充说明（如正在拉取的路径、扫描到的游戏数量） */
    val stepDetail: String? = null,
    /** 底层命令输出的原始日志行（增量追加） */
    val logLine: String? = null,
    /** 扫描阶段完成时携带的游戏名列表（供选择弹窗使用） */
    val games: List<String>? = null,
    /** 失败时的错误信息 */
    val failure: String? = null
)
