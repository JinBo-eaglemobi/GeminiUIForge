package org.gemini.ui.forge.ui.component.chat.mention

import org.gemini.ui.forge.model.ui.UIStudioBlock

/**
 * 悬停预览能力注入契约：由调用端（如 UIProjectAiStudioDialog）提供工程数据与视图图元解析器，
 * 通用聊天组件库保持纯净（不反向依赖 UIStudioViewModel）。
 * 为 null 时（触屏/移动端或非 PC 平台）悬停预览整体禁用——PC 端特例开关。
 *
 * @property resolveAssetPathByHash 图元 assetHash → 素材相对路径解析器
 * @property resolveViewBlocks 视图 id → 该视图顶层图元列表（从当前工程即时查询）
 * @property resolveBlockById 按图元 id 深查图元树（用于 BLOCK 容器子层微缩排版）
 */
data class MentionHoverPreviewConfig(
    val resolveAssetPathByHash: (String?) -> String? = { null },
    val resolveViewBlocks: (String) -> List<UIStudioBlock>? = { null },
    val resolveBlockById: (String) -> UIStudioBlock? = { null }
)
