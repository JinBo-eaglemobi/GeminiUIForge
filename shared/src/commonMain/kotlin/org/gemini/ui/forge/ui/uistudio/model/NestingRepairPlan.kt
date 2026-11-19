package org.gemini.ui.forge.ui.uistudio.model

/**
 * 内嵌子页面一键修复预案数据模型。
 *
 * 用于在弹出修复确认对话框时，向用户清晰展示当前工程中检测到的所有违规内联组件清单，
 * 以及即将为每个违规项自动生成的新独立页面 ID 预览。
 *
 * @property errors 待修复的违规记录列表（仅限 INLINE_PAGE_NESTED 违规项）
 * @property timestamp 生成该预案的时间戳
 */
data class NestingRepairPlan(
    val errors: List<UIStudioDataError>,
    val timestamp: Long = org.gemini.ui.forge.getCurrentTimeMillis()
)
