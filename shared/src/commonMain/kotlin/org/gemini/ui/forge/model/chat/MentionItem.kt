package org.gemini.ui.forge.model.chat

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 上下文引用类型分类
 */
enum class MentionCategory(val titleZh: String, val titleEn: String) {
    VIEW("视图界面", "Views"),
    BLOCK("图元图层", "Layer Blocks"),
    ASSET("切片素材", "Assets"),
    DOC("参考文档", "Documents")
}

/**
 * 单个可引用的工程上下文实体对象
 */
data class MentionItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val category: MentionCategory,
    val icon: ImageVector? = null,
    val previewImagePath: String? = null,
    val payload: MentionPayload
)

/**
 * 引用载荷多态封装，支撑大模型上下文注入与精准排版元数据提取
 */
sealed interface MentionPayload {
    /** 视图上下文 */
    data class ViewPayload(
        val viewId: String,
        val viewName: String,
        val width: Int,
        val height: Int,
        val blockCount: Int
    ) : MentionPayload

    /** 图元图层上下文 */
    data class BlockPayload(
        val blockId: String,
        val blockName: String,
        val type: String,
        val bounds: String,
        val isContainer: Boolean
    ) : MentionPayload

    /** 切片素材上下文 */
    data class AssetPayload(
        val assetId: String,
        val fileName: String,
        val filePath: String,
        val width: Int = 0,
        val height: Int = 0
    ) : MentionPayload

    /** 本地参考文档上下文 */
    data class DocPayload(
        val fileName: String,
        val absolutePath: String,
        val content: String? = null
    ) : MentionPayload
}
