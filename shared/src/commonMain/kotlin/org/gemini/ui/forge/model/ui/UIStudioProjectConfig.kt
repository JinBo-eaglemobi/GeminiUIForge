package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable

/**
 * UI Studio 项目级公用配置文件数据模型 (template.json)
 *
 * 专职承载整个工程的全局公用元信息与页面文件索引清单。
 * 彻底剥离一切界面专属的私有字段（如单个界面的特定宽高、画布背景色、图元 blocks 等，全部 100% 独立保存在对应的 pages/{pageId}.json 中）。
 *
 * @property projectId 工程全局唯一标识符
 * @property projectName 工程名称
 * @property formatVersion 配置文件格式版本（当前规范为 "2.0"）
 * @property createdAt 项目创建时间戳（毫秒）
 * @property updatedAt 项目最后修改时间戳（毫秒）
 * @property defaultCanvasWidth 项目默认设计基准分辨率宽度（如 1080f）
 * @property defaultCanvasHeight 项目默认设计基准分辨率高度（如 1920f）
 * @property activeViewId 最后一次打开/聚焦的活跃页面 ID
 * @property pageIndex 项目包含的所有页面轻量索引清单
 */
@Serializable
data class UIStudioProjectConfig(
    val projectId: String,
    val projectName: String,
    val formatVersion: String = "2.0",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val defaultCanvasWidth: Float = 1080f,
    val defaultCanvasHeight: Float = 1920f,
    val activeViewId: String? = null,
    val pageIndex: List<UIStudioPageIndexItem> = emptyList()
)
