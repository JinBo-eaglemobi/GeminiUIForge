package org.gemini.ui.forge.ui.component.chat

import androidx.compose.runtime.Immutable
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.ApiFlavor

/**
 * 待发送的多模态附件载荷模型
 *
 * @property name 文件名称或标签
 * @property localUri 本地文件绝对路径或 URI（若有）
 * @property bytes 文件的内存二进制字节（用于离线渲染与 Base64 发送）
 * @property mimeType 媒体格式类型（如 image/png, application/json 等）
 */
@Immutable
data class CommonChatAttachment(
    val name: String,
    val localUri: String? = null,
    val bytes: ByteArray? = null,
    val mimeType: String = "image/png"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CommonChatAttachment

        if (name != other.name) return false
        if (localUri != other.localUri) return false
        if (mimeType != other.mimeType) return false
        if (bytes != null) {
            if (other.bytes == null) return false
            if (!bytes.contentEquals(other.bytes)) return false
        } else if (other.bytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (localUri?.hashCode() ?: 0)
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

/**
 * 通用 AI 对话工作室全场景可定制配置契约
 *
 * 遵循项目规范：严禁硬编码任何业务文案与魔法数字，全部参数可通过配置模型进行覆盖或通过插槽定制。
 *
 * @property title 工作室主标题
 * @property emptyTipText 消息为空时的占位引导提示文案
 * @property inputPlaceholder 底部输入框默认占位提示
 * @property defaultModel 默认选中的 Gemini 模型（出厂默认最新旗舰 gemini-3.8-flash）
 * @property availableModels 供用户切换的可选模型列表
 * @property defaultApiFlavor 默认通信协议（出厂默认新一代会话 Interactions API）
 * @property availableApiFlavors 供用户切换的可选通信协议列表
 * @property showModelSelector 是否在底部控制栏显示模型切换下拉框
 * @property showApiFlavorSelector 是否在底部控制栏显示协议切换下拉框
 * @property showAttachmentPicker 是否支持多模态文件/图片上传
 * @property showSessionSearch 是否在会话侧栏展示搜索过滤框
 * @property enableNarrowModeAutoFold 是否在宽度受限（< 640dp，如侧边栏/浮窗）时自动折叠会话侧栏为抽屉式切换
 * @property narrowBreakpointDp 判定为窄屏模式的宽度阈值 Dp（默认 640dp）
 */
@Immutable
data class CommonChatStudioConfig(
    val title: String = "AI 智能视觉助手",
    val emptyTipText: String = "开始与 AI 助手对话，探索无限创意...",
    val inputPlaceholder: String = "输入提示词或指令... (Enter 发送，Shift+Enter 换行)",
    val defaultModel: GeminiModel = GeminiModel.GEMINI_3_8_FLASH,
    val availableModels: List<GeminiModel> = GeminiModel.entries,
    val defaultApiFlavor: ApiFlavor = ApiFlavor.INTERACTIONS,
    val availableApiFlavors: List<ApiFlavor> = ApiFlavor.entries,
    val showModelSelector: Boolean = true,
    val showApiFlavorSelector: Boolean = true,
    val showAttachmentPicker: Boolean = true,
    val showSessionSearch: Boolean = true,
    val enableNarrowModeAutoFold: Boolean = true,
    val narrowBreakpointDp: Float = 640f
)
