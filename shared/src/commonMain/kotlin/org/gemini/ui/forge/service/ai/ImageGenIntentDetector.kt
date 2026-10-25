package org.gemini.ui.forge.service.ai

import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.ModelStudioGroup
import org.gemini.ui.forge.model.StudioModelResolver

/**
 * 用户输入生图意图探测工具类
 *
 * 负责通过中英文关键词与多模态意图模式，精准识别用户的“生图/出图/作图/绘制/重新生成图片”需求，
 * 协助交互层（如 CommonChatInputBar）自适应感知并智能推荐或切换至专属生图模型。
 */
object ImageGenIntentDetector {

    /**
     * 中文核心生图与出图关键词（直接命中）
     */
    private val CHINESE_EXACT_KEYWORDS = Regex(
        """(生图|出图|画图|做图|作图|绘图|配图|重新生图|重新出图|文生图|图生图)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 中文高频生图意图正则（动词 + 范围修饰 + 数量词 + 形容修饰 + 视觉实体）
     */
    private val CHINESE_IMAGE_INTENT_REGEX = Regex(
        """(?:(?:单独|另外|帮我|给我|为我|请|立刻|马上|一并)?\s*(?:生成|绘制|画|做|制作|渲染|设计)\s*(?:(?:一|几|两|[0-9]+)\s*(?:张|幅|个|只|条|匹|款|种|套|位|具|把)?\s*)?(?:[\u4e00-\u9fa5a-zA-Z0-9]{0,6}?的\s*|[\u4e00-\u9fa5a-zA-Z0-9]{0,4}?\s*)?(?:图片|图|画面|海报|插图|插画|壁纸|头像|原画|素材|纹理|切片|图元|资产|资产图|照片|按钮|图标|背景|底图|边框|底框|弹窗|卡片|装饰|外框|徽章|贴图|小猫|猫咪|猫|小狗|狗狗|狗|动物|角色|人物|怪兽|神兽|机甲|武器|金币|宝箱|皇冠|转盘|宝石|星星|爱心|徽记|标志))""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 英文动宾组合与专业词汇正则
     */
    private val ENGLISH_IMAGE_INTENT_REGEX = Regex(
        """\b(generate|create|draw|make|render|design)\s+((an?|some|[0-9]+)\s+)?([a-zA-Z-]+\s+){0,3}(image|picture|icon|button|asset|graphic|photo|illustration|slice|sprite|texture|wallpaper|avatar|cat|dog|character|creature|background|banner)\b|\b(text-to-image|image\s*generation|re-?generate\s+image)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 判断给定文本是否包含明显的生图意图
     *
     * @param text 用户输入的文本内容
     * @return 若命中生图意图特征返回 true，否则返回 false
     */
    fun isImageGenIntent(text: String): Boolean {
        if (text.isBlank()) return false
        val trimmed = text.trim()
        return CHINESE_EXACT_KEYWORDS.containsMatchIn(trimmed) ||
                CHINESE_IMAGE_INTENT_REGEX.containsMatchIn(trimmed) ||
                ENGLISH_IMAGE_INTENT_REGEX.containsMatchIn(trimmed)
    }

    /**
     * 根据全局预设或可用模型库，解析出当前应当使用的目标生图模型标识
     *
     * @param presetModel 用户在设置中心预设的默认生图模型名称（可选）
     * @return 目标生图模型唯一标识（如 gemini-2.5-flash-image）
     */
    fun resolveTargetImageModel(presetModel: String?): String {
        if (!presetModel.isNullOrBlank()) {
            return presetModel
        }
        val specialized = StudioModelResolver.resolveStudioModels()
            .firstOrNull { it.group == ModelStudioGroup.IMAGE_SPECIALIZED }
        return specialized?.model?.modelName ?: GeminiModel.GEMINI_2_5_FLASH_IMAGE.modelName
    }

    /**
     * 根据全局预设或可用模型库，解析出当前应当使用的目标生图 GeminiModel 枚举
     *
     * @param presetModel 用户在设置中心预设的默认生图模型名称（可选）
     * @return 目标生图模型枚举（如 GeminiModel.GEMINI_2_5_FLASH_IMAGE）
     */
    fun resolveTargetGeminiModel(presetModel: String?): GeminiModel {
        val targetModelName = resolveTargetImageModel(presetModel)
        return GeminiModel.entries.firstOrNull { it.modelName == targetModelName }
            ?: GeminiModel.GEMINI_2_5_FLASH_IMAGE
    }

    /**
     * 判定给定 GeminiModel 是否属于生图专属模型组 (IMAGE_SPECIALIZED)
     */
    fun isImageSpecialized(model: GeminiModel): Boolean {
        return StudioModelResolver.resolveStudioModels()
            .firstOrNull { it.model == model }?.group == ModelStudioGroup.IMAGE_SPECIALIZED
    }
}
