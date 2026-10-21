package org.gemini.ui.forge.model.prompt

/**
 * 提示词强类型标识枚举。
 *
 * 【纯净律与规范】：
 * 枚举仅作为类型安全的标识键，只定义名称与关联文件名 ID，绝对不包含任何提示词正文文本！
 * 所有的提示词正文与模板内容 100% 独立存储于外部/内置文件中（.txt / .md / .json）。
 */
enum class PromptKey(
    val id: String,
    val isMarkdown: Boolean = false,
    val isJson: Boolean = false
) {
    ANALYZE_TEMPLATE("analyze_template"),
    REFINE_TEMPLATE("refine_template"),
    IMAGE_TO_UI_SPEC("IMAGE_TO_UI_SPEC", isMarkdown = true),
    AI_OPTIMIZE_PROMPT("ai_optimize_prompt"),
    OPTIMIZE_INSTRUCTION_ZH("optimize_instruction_zh"),
    OPTIMIZE_INSTRUCTION_EN("optimize_instruction_en"),
    REFINE_INSTRUCTION_UPDATE("refine_instruction_update"),
    REFINE_INSTRUCTION_NEW("refine_instruction_new"),
    CHAT_REFINE_INSTRUCTION("chat_refine_instruction"),
    GEMINI_IMAGE_GEN("gemini_image_gen"),
    DETECT_ASSET_SHEET_COORDS("detect_asset_sheet_coords"),
    NORMALIZE_ASSET_SHEET("normalize_asset_sheet"),
    NORMALIZE_ASSET_SHEET_USER("normalize_asset_sheet_user"),
    BLOCK_TYPE_PROMPTS("block_type_prompts", isJson = true),
    UI_STUDIO_AGENT_PROMPT("ui_studio_agent_prompt", isMarkdown = true);

    val fileName: String
        get() = when {
            isMarkdown -> "$id.md"
            isJson -> "$id.json"
            else -> "$id.txt"
        }

    companion object {
        fun fromId(id: String): PromptKey? = entries.find { it.id == id }
    }
}
