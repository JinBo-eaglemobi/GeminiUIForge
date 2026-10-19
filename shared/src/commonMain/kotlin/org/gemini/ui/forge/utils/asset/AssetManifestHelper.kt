package org.gemini.ui.forge.utils.asset

import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem

/**
 * UI Studio 切片素材库清单极简索引生成助手。
 *
 * 核心目标：
 * 用最精简紧凑的 Token 消耗（每条仅 ~15 Token），向大模型提供整个素材库的完备索引，
 * 包含：文件名、真实物理宽高像素、相对归属路径。
 */
object AssetManifestHelper {

    /**
     * 将素材列表序列化为高密度紧凑清单文本。
     *
     * 格式示例：
     * - Back_Button.png [80x80] (ui/back)
     * - Balance_Bar.png [480x88] (hud)
     */
    fun buildCompactManifest(assets: List<UIAssetItem>): String {
        if (assets.isEmpty()) return "暂无可用切片素材"

        return buildString {
            assets.forEach { item ->
                append("- ")
                append(item.name)
                append(" [")
                append(item.width)
                append("x")
                append(item.height)
                append("]")
                if (item.relativePath.isNotBlank()) {
                    val dir = item.relativePath.substringBeforeLast('/', "").substringBeforeLast('\\', "")
                    if (dir.isNotBlank()) {
                        append(" (")
                        append(dir)
                        append(")")
                    }
                }
                append("\n")
            }
        }.trimEnd()
    }
}
