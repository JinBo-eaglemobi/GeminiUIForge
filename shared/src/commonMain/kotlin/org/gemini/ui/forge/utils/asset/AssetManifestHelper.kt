package org.gemini.ui.forge.utils.asset

import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem

/**
 * UI Studio 切片素材库清单工业级命名与智能识别规范助手。
 *
 * 核心契约：
 * 1. 采用纯文件名独立词元 (Token-based) 解析，彻底杜绝目录相对路径带来的字母与关键字污染；
 * 2. 设立严格高置信度门禁，仅对完全符合工业级前缀/后缀命名标准的切片返回推断角色；
 * 3. 命名不规范者角色一律为 null（未标识），坚决不强行伪造定位，由 AI 大模型自主调用 get_assets 审阅材质画面决定排版位置。
 */
object AssetManifestHelper {

    /**
     * 素材语义角色分类枚举 (高置信度)
     */
    enum class AssetSemanticCategory(val displayName: String) {
        SPIN_BUTTON("核心旋转按键 (Spin Button)"),
        BUTTON("交互按键 (Button)"),
        BACKGROUND("全景/局部底图 (Background)"),
        HUD_FRAME("边框/数值底栏 (HUD/Frame)"),
        TITLE_LOGO("标题/Logo微标 (Title/Logo)"),
        REEL_SYMBOL("游戏元素/滚轴符号 (Reel Symbol)"),
        OVERLAY_FX("光影/特效层 (Overlay/FX)")
    }

    /**
     * 根据素材纯文件名分词匹配，对切片进行严格高置信度语义归类。
     * 若命名不规范或无法明确推断，一律返回 null，绝不模糊猜测。
     *
     * @param fileName 素材文件名（支持带或不带扩展名）
     * @return 匹配成功的 [AssetSemanticCategory]，不合规时返回 null
     */
    fun categorizeAsset(fileName: String): AssetSemanticCategory? {
        val cleanName = fileName.substringBeforeLast('.').trim()
        if (cleanName.isBlank()) return null

        val lowerName = cleanName.lowercase()
        // 按照下划线、短横线、空格拆分为独立词元数组
        val tokens = lowerName.split(Regex("[_\\-\\s]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val tokenSet = tokens.toSet()

        // 1. 优先判定：核心旋转按键 (Spin Button)
        // 条件：明确带有 spin 且包含按键词元（btn/button/main/auto/play），且不能包含 symbol/sym/free_spin_symbol 这种滚轴符号
        val isSpinToken = tokenSet.contains("spin") || lowerName.contains("spin")
        val isButtonToken = tokenSet.contains("btn") || tokenSet.contains("button") ||
                lowerName.startsWith("btn_") || lowerName.endsWith("_btn") ||
                lowerName.startsWith("button_") || lowerName.endsWith("_button")
        val isSymbolToken = tokenSet.contains("symbol") || tokenSet.contains("sym") || tokenSet.contains("card")

        if (isSpinToken && isButtonToken && !isSymbolToken) {
            return AssetSemanticCategory.SPIN_BUTTON
        }
        if (isSpinToken && (tokenSet.contains("main") || tokenSet.contains("auto")) && !isSymbolToken) {
            return AssetSemanticCategory.SPIN_BUTTON
        }

        // 2. 交互按键 (Button)
        if (isButtonToken) {
            return AssetSemanticCategory.BUTTON
        }
        if (tokenSet.contains("play") || tokenSet.contains("plus") || tokenSet.contains("minus") ||
            tokenSet.contains("close") || tokenSet.contains("back") || tokenSet.contains("menu") ||
            tokenSet.contains("sound") || tokenSet.contains("music") || tokenSet.contains("setting") ||
            tokenSet.contains("settings") || tokenSet.contains("help") || tokenSet.contains("info")
        ) {
            // 需配合前缀或独立按键语境
            if (lowerName.endsWith("_button") || lowerName.endsWith("_btn") || lowerName.startsWith("btn_") ||
                tokenSet.contains("button") || tokenSet.contains("btn") || tokens.size == 1
            ) {
                return AssetSemanticCategory.BUTTON
            }
        }

        // 3. 全景/局部底图 (Background)
        if (tokenSet.contains("bg") || tokenSet.contains("background") || tokenSet.contains("backdrop") ||
            lowerName.startsWith("bg_") || lowerName.startsWith("background_") ||
            lowerName.endsWith("_bg") || lowerName.endsWith("_background")
        ) {
            return AssetSemanticCategory.BACKGROUND
        }

        // 4. 标题/Logo微标 (Title/Logo)
        if (tokenSet.contains("logo") || tokenSet.contains("title") || tokenSet.contains("header") ||
            lowerName.startsWith("logo_") || lowerName.startsWith("title_") ||
            lowerName.endsWith("_logo") || lowerName.endsWith("_title")
        ) {
            return AssetSemanticCategory.TITLE_LOGO
        }

        // 5. 边框/数值底栏 (HUD/Frame)
        if (tokenSet.contains("hud") || tokenSet.contains("frame") || tokenSet.contains("bar") ||
            tokenSet.contains("panel") || tokenSet.contains("border") ||
            lowerName.startsWith("hud_") || lowerName.startsWith("panel_") || lowerName.startsWith("bar_") || lowerName.startsWith("frame_") ||
            lowerName.endsWith("_hud") || lowerName.endsWith("_panel") || lowerName.endsWith("_bar") || lowerName.endsWith("_frame")
        ) {
            return AssetSemanticCategory.HUD_FRAME
        }

        // 6. 光影/特效层 (Overlay/FX)
        if (tokenSet.contains("fx") || tokenSet.contains("effect") || tokenSet.contains("glow") ||
            tokenSet.contains("overlay") || tokenSet.contains("light") || tokenSet.contains("shadow") ||
            lowerName.startsWith("fx_") || lowerName.endsWith("_fx") || lowerName.endsWith("_glow")
        ) {
            return AssetSemanticCategory.OVERLAY_FX
        }

        // 7. 游戏元素/滚轴符号 (Reel Symbol)
        // 必须具备明确符号特征或规范扑克牌前缀（严格边界，绝不无规则单字母泛匹配）
        if (isSymbolToken || tokenSet.contains("wild") || tokenSet.contains("scatter") ||
            tokenSet.contains("trophy") || tokenSet.contains("crest") || tokenSet.contains("badge") ||
            lowerName.startsWith("sym_") || lowerName.startsWith("symbol_") ||
            lowerName.endsWith("_sym") || lowerName.endsWith("_symbol")
        ) {
            return AssetSemanticCategory.REEL_SYMBOL
        }

        // 扑克牌符号严苛边界守恒：仅当带有 card_ 前缀，或为特定扑克牌规范词元
        val pokerTokens = setOf("10", "9", "a", "k", "q", "j")
        if (lowerName.startsWith("card_") || lowerName.endsWith("_card")) {
            return AssetSemanticCategory.REEL_SYMBOL
        }
        if (tokens.size >= 2 && tokens[0] == "card" && pokerTokens.contains(tokens[1])) {
            return AssetSemanticCategory.REEL_SYMBOL
        }
        if (tokens.size >= 2 && tokens[0] == "sym" && pokerTokens.contains(tokens[1])) {
            return AssetSemanticCategory.REEL_SYMBOL
        }

        // 未匹配任何明确工业级规范，返回 null（未识别），交由 AI 视觉自审
        return null
    }

    /**
     * 根据素材纯文件名，智能推断其在游戏 UI 布局中的语义角色与职能。
     * 若未遵循工业级命名规范，返回 null，绝不强行指定伪造定位。
     *
     * @param fileName 素材纯文件名（例如 'btn_back.png' 或 'GodCup_Logo'）
     * @param relativePath 已废弃，保留可选参数兼容既有调用点，不再参与路径计算
     * @return 明确角色文案（如 '交互按键 (Button)'），无法高置信推断时返回 null
     */
    fun inferAssetRole(fileName: String, @Suppress("UNUSED_PARAMETER") relativePath: String = ""): String? {
        return categorizeAsset(fileName)?.displayName
    }

    /**
     * 将素材列表序列化为高密度紧凑清单文本。
     *
     * 核心契约：
     * 遵循资产唯一 Hash 契约，输出素材名称、唯一 hash、物理宽高与语义角色。
     * - 符合规范的切片：显式输出 '| 角色: 交互按键 (Button)'；
     * - 未识别或不合规的切片：输出 '(角色未标识，由AI审阅图片)'，明确告知大模型需自主审阅画面材质决定排版位置。
     *
     * 格式示例：
     * - [hash: 9a3f2c...] btn_back.png [80x84] | 角色: 交互按键 (Button)
     * - [hash: e81b7a...] D02.png [480x88] (角色未标识，由AI审阅图片)
     */
    fun buildCompactManifest(assets: List<UIAssetItem>): String {
        if (assets.isEmpty()) return "暂无可用切片素材"

        return buildString {
            assets.forEach { item ->
                append("- [hash: ")
                append(item.hash)
                append("] ")
                append(item.name)
                append(" [")
                append(item.width)
                append("x")
                append(item.height)
                append("]")
                val role = inferAssetRole(item.name)
                if (role != null) {
                    append(" | 角色: ")
                    append(role)
                } else {
                    append(" (角色未标识，由AI审阅图片)")
                }
                append("\n")
            }
        }.trimEnd()
    }
}
