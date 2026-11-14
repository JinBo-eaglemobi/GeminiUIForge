package org.gemini.ui.forge.ui.uistudio.model

/**
 * UI Studio 单项数据异常诊断记录
 *
 * @param pageId 发生异常的宿主页面 ID
 * @param pageName 宿主页面展示名称
 * @param blockId 发生异常的图元 ID
 * @param blockName 图元展示名称（若有）
 * @param targetPageId 图元引用的目标页面 ID
 * @param errorType 错误类型分类
 * @param message 面向开发者的详尽位置与错误诊断说明
 */
data class UIStudioDataError(
    val pageId: String,
    val pageName: String,
    val blockId: String,
    val blockName: String? = null,
    val targetPageId: String? = null,
    val errorType: UIStudioDataErrorType,
    val message: String
)
