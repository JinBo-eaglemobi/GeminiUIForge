package org.gemini.ui.forge.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

object SerialRectSerializer : KSerializer<SerialRect> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SerialRect") {
        element<Float>("left")
        element<Float>("top")
        element<Float>("right")
        element<Float>("bottom")
    }

    override fun deserialize(decoder: Decoder): SerialRect {
        require(decoder is JsonDecoder) { "This serializer can be used only with JSON format" }
        val element = decoder.decodeJsonElement()
        
        return if (element is JsonArray) {
            val array = element.jsonArray
            SerialRect(
                left = array.getOrNull(0)?.jsonPrimitive?.float ?: 0f,
                top = array.getOrNull(1)?.jsonPrimitive?.float ?: 0f,
                right = array.getOrNull(2)?.jsonPrimitive?.float ?: 0f,
                bottom = array.getOrNull(3)?.jsonPrimitive?.float ?: 0f
            )
        } else if (element is JsonObject) {
            val obj = element.jsonObject
            SerialRect(
                left = obj["left"]?.jsonPrimitive?.float ?: 0f,
                top = obj["top"]?.jsonPrimitive?.float ?: 0f,
                right = obj["right"]?.jsonPrimitive?.float ?: 0f,
                bottom = obj["bottom"]?.jsonPrimitive?.float ?: 0f
            )
        } else {
            throw IllegalArgumentException("Unexpected JSON element for SerialRect: $element")
        }
    }

    override fun serialize(encoder: Encoder, value: SerialRect) {
        require(encoder is JsonEncoder) { "This serializer can be used only with JSON format" }
        // 默认将坐标序列化为体积更小的数组形式 [left, top, right, bottom]
        encoder.encodeJsonElement(JsonArray(listOf(
            JsonPrimitive(value.left),
            JsonPrimitive(value.top),
            JsonPrimitive(value.right),
            JsonPrimitive(value.bottom)
        )))
    }
}

/**
 * 跨平台可序列化的矩形坐标类 (SerialRect)
 * 用于替代 Android/iOS 原生且无法通过 JSON 互相传递的 Rect 类。
 * @property left 矩形左边界的 X 坐标
 * @property top 矩形上边界的 Y 坐标
 * @property right 矩形右边界的 X 坐标
 * @property bottom 矩形下边界的 Y 坐标
 */
@Serializable(with = SerialRectSerializer::class)
data class SerialRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** 矩形的绝对宽度 */
    val width: Float get() = right - left
    /** 矩形的绝对高度 */
    val height: Float get() = bottom - top
}

/**
 * 枚举类：定义所有的 UI 功能模块类型 (UIBlockType)
 * 供 Gemini 视觉大模型在分析图片时进行分类标记，并附带针对图像生成的默认英文 Prompt 前缀，
 * 确保扩散模型 (如 Imagen) 在生图时有基础的环境语义作为支撑。
 */
enum class UIBlockType(val defaultPrompt: String) {
    // ---- 老虎机 (Slots) 专用核心组件 ----
    /** 核心转轴区域：包含 3x5 等网格和抛光的边框 */
    REEL("Slot machine reel area, 3x5 or 4x5 grid, polished frame"),
    /** 旋转主按钮：典型的圆形豪华外观与光泽质感 */
    SPIN_BUTTON("Circular spin button for a slot machine, ornate design, glossy texture"),
    /** 赢分数字显示框：带有豪华背景板和数字显示区域的矩形框 */
    WIN_DISPLAY("Rectangular win score display box, digital numbers area, luxury background"),
    /** 满屏游戏主背景：沉浸式游戏氛围，有主题感 */
    BACKGROUND("Full screen game background, immersive atmosphere, thematic texture"),
    /** 单个图标标志：例如转轴上的水果/皇冠等高品质符号 */
    SYMBOL("Individual slot machine symbol, high-quality icon, vibrant colors"),

    // ---- 通用交互与排版组件 (泛用 UI 解析) ----
    /** 普通交互按钮：例如充值、菜单等可点击组件 */
    BUTTON("Generic interactive button, polished UI element, clickable style"),
    /** 内容面板/底座：用于容纳文字或图标的半透明、实心底板框架 */
    PANEL("Content panel or container box, semi-transparent or solid background, UI frame"),
    /** 顶部导航栏：游戏大标题、个人信息及资产显示区 */
    HEADER("Top header bar, title area, stylized top banner"),
    /** 底部状态栏：通常用于快捷导航或状态底座 */
    FOOTER("Bottom footer bar, navigation or status area, grounded base"),
    /** 纯文本显示区：易于阅读的文本底托板 */
    TEXT_AREA("Text display area, readable background plate for typography"),
    /** 独立小图标：无文字的小徽章或控制小控件 */
    ICON("Small icon or badge, UI control element, crisp vector style"),
    /** 环境装饰物：没有功能意义的花纹、边框、特效粒子或氛围点缀 */
    DECORATION("Ornamental or decorative UI element, flourishes, borders, or particles")
}

/**
 * 最小生成单元模型：UI 功能块 (UIBlock)
 * 描述页面上的一个绝对独立和可重新生成的图层。
 * @property id 该模块的全局唯一标识符
 * @property type 该模块所属的功能分类
 * @property bounds 该模块在页面设计稿上的绝对坐标系（由大模型推断）
 * @property currentImageUri 当前选定加载的本地图片路径或远程 URL
 * @property userPrompt 用户在编辑界面为此模块添加的自定义细化 Prompt（如：深海主题、蒸汽朋克风）
 */
@Serializable
data class UIBlock(
    val id: String,
    val type: UIBlockType,
    val bounds: SerialRect,
    val currentImageUri: String? = null,
    val userPrompt: String = ""
) {
    /** 自动拼接基础类别描述与用户自定义描述，形成最终发给生成模型的完整 Prompt */
    val fullPrompt: String
        get() = "${type.defaultPrompt}, $userPrompt"
}

/**
 * 页面模型：UI 页面 (UIPage)
 * 包含多个散落 UIBlock 的单张视图环境。例如主游戏界面或 Bonus 奖励界面。
 * @property id 页面的唯一标识符
 * @property nameStr 页面的标题名称
 * @property blocks 页面包含的所有子功能块列表
 */
@Serializable
data class UIPage(
    val id: String,
    val nameStr: String = "Page",
    val blocks: List<UIBlock> = emptyList()
)

/**
 * 顶级数据模型：应用模板工程 (ProjectState)
 * 整个模板所有数据序列化落盘的最外层容器结构，通常保存为 `<模板名>.json`。
 * @property projectId 模板的项目 ID 或名称
 * @property globalTheme 整个项目的宏观主题风格（大模型分析得出）
 * @property coverImage 作为主页封面展示的示例海报图的相对文件路径
 * @property pages 该项目内部包含的不同游戏/展示页面集合
 */
@Serializable
data class ProjectState(
    val projectId: String = "default_project",
    val globalTheme: String = "classic casino",
    val coverImage: String? = null,
    val pages: List<UIPage> = emptyList(),
    val createdAt: Long = 0L
)