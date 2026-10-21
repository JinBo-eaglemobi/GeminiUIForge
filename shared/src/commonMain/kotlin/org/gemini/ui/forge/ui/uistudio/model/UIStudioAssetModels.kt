package org.gemini.ui.forge.ui.uistudio.model

import kotlinx.serialization.Serializable
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession

/**
 * UI Studio 单个素材资产实体。
 *
 * @property id 素材唯一标识 ID
 * @property name 素材显示名称
 * @property relativePath 相对数据存储目录的相对路径（如 "templates/{proj}/assets/{folder}/{id}.png"）
 * @property width 素材物理宽度（像素）
 * @property height 素材物理高度（像素）
 * @property category 业务分类标签（如 "button", "symbol", "icon" 等）
 * @property folderId 所属文件夹 ID，为 null 时表示归属于根目录
 * @property createdAt 创建时间戳
 */
@Serializable
data class UIAssetItem(
    val id: String,
    val name: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val category: String = "default",
    val folderId: String? = null,
    val createdAt: Long = 0L
)

/**
 * UI Studio 素材文件夹实体。
 *
 * @property id 文件夹唯一标识 ID
 * @property name 文件夹显示名称
 * @property parentFolderId 父级文件夹 ID，为 null 时表示顶层文件夹
 */
@Serializable
data class UIAssetFolder(
    val id: String,
    val name: String,
    val parentFolderId: String? = null
)

/**
 * UI Studio 项目素材库整体数据契约。
 *
 * @property folders 自定义文件夹列表
 * @property items 已导入或切片生成的全部素材列表
 */
@Serializable
data class UIAssetLibrary(
    val folders: List<UIAssetFolder> = emptyList(),
    val items: List<UIAssetItem> = emptyList()
) {
    /**
     * 获取指定文件夹下的直接子素材列表（folderId 为 null 时表示根目录素材）。
     */
    fun getItemsInFolder(folderId: String?): List<UIAssetItem> {
        return items.filter { it.folderId == folderId }
    }

    /**
     * 获取指定父文件夹下的直接子文件夹列表。
     */
    fun getSubFolders(parentFolderId: String? = null): List<UIAssetFolder> {
        return folders.filter { it.parentFolderId == parentFolderId }
    }
}

/**
 * UI Studio 针对当前项目独立配置的 Agent 架构模型。
 *
 * @property apiFlavor 底层通信协议架构（默认采用新一代链式交互 API INTERACTIONS）
 * @property systemPrompt 专属于当前项目的角色定义与排版规范指令（为空时自动从外部资源 prompts/ui_studio_agent_prompt.md 动态读取）
 * @property modelName 默认使用的模型名称（默认采用最新旗舰 Gemini 3.8 Flash）
 * @property temperature 采样温度
 */
@Serializable
data class ProjectAgentConfig(
    val apiFlavor: ApiFlavor = ApiFlavor.INTERACTIONS,
    val systemPrompt: String = "",
    val modelName: String = GeminiModel.GEMINI_3_8_FLASH.modelName,
    val temperature: Float = 0.7f
)

