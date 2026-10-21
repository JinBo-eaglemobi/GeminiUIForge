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
     * 素材语义角色分类枚举
     */
    enum class AssetSemanticCategory {
        BACKGROUND,
        SPIN_BUTTON,
        BUTTON,
        REEL_SYMBOL,
        HUD_FRAME,
        TITLE_LOGO,
        OVERLAY_FX,
        OTHER
    }

    /**
     * 根据素材文件名与路径特征，对切片进行精准语义归类
     */
    fun categorizeAsset(fileName: String, relativePath: String = ""): AssetSemanticCategory {
        val lower = (fileName + " " + relativePath).lowercase()
        return when {
            lower.contains("spin") && (lower.contains("btn") || lower.contains("button") || lower.contains("play")) -> AssetSemanticCategory.SPIN_BUTTON
            lower.contains("spin") -> AssetSemanticCategory.SPIN_BUTTON
            lower.contains("bg") || lower.contains("background") || lower.contains("backdrop") -> AssetSemanticCategory.BACKGROUND
            lower.contains("symbol") || lower.contains("sym") || lower.contains("item") || lower.contains("card") ||
                    lower.contains("icon") || lower.contains("crest") || lower.contains("badge") || lower.contains("logo_") ||
                    lower.contains("trophy") || lower.contains("ball") || lower.contains("boot") || lower.contains("shirt") ||
                    Regex(".*(sym_|symbol_|[0-9]+|k|q|j|a|10|9).*", RegexOption.IGNORE_CASE).matches(lower) -> AssetSemanticCategory.REEL_SYMBOL
            lower.contains("btn") || lower.contains("button") || lower.contains("play") || lower.contains("line") ||
                    lower.contains("bet") || lower.contains("plus") || lower.contains("minus") || lower.contains("max") ||
                    lower.contains("auto") || lower.contains("stop") || lower.contains("turbo") -> AssetSemanticCategory.BUTTON
            lower.contains("bar") || lower.contains("hud") || lower.contains("panel") || lower.contains("frame") || lower.contains("border") -> AssetSemanticCategory.HUD_FRAME
            lower.contains("logo") || lower.contains("title") || lower.contains("header") -> AssetSemanticCategory.TITLE_LOGO
            lower.contains("overlay") || lower.contains("light") || lower.contains("glow") || lower.contains("shadow") -> AssetSemanticCategory.OVERLAY_FX
            else -> AssetSemanticCategory.OTHER
        }
    }

    /**
     * 根据素材文件名与路径特征，智能推断其在游戏 UI 布局中的语义角色与职能。
     */
    fun inferAssetRole(fileName: String, relativePath: String = ""): String {
        return when (categorizeAsset(fileName, relativePath)) {
            AssetSemanticCategory.SPIN_BUTTON -> "核心旋转按键 (Spin Button)"
            AssetSemanticCategory.BUTTON -> "交互按键 (Button)"
            AssetSemanticCategory.BACKGROUND -> "全景/局部底图 (Background)"
            AssetSemanticCategory.HUD_FRAME -> "边框/数值底栏 (HUD/Frame)"
            AssetSemanticCategory.TITLE_LOGO -> "标题/Logo微标 (Title/Logo)"
            AssetSemanticCategory.REEL_SYMBOL -> "游戏元素/滚轴符号 (Reel Symbol)"
            AssetSemanticCategory.OVERLAY_FX -> "光影/特效层 (Overlay/FX)"
            AssetSemanticCategory.OTHER -> "视觉图元切片 (Visual Asset)"
        }
    }

    /**
     * 将素材列表序列化为高密度紧凑清单文本。
     *
     * 格式示例：
     * - Back_Button.png [80x80] | 角色: 交互按键 (Button) (ui/back)
     * - Balance_Bar.png [480x88] | 角色: 边框/数值底栏 (HUD/Frame) (hud)
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
                append("] | 角色: ")
                append(inferAssetRole(item.name, item.relativePath))
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
