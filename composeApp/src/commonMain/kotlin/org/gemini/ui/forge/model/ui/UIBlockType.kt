package org.gemini.ui.forge.model.ui
import kotlinx.serialization.Serializable

/**
 * 枚举类：定义所有的 UI 功能模块类型 (UIBlockType)
 * 供 Gemini 视觉大模型在分析图片时进行分类标记，并附带针对图像生成的默认英文 Prompt 前缀，
 * 确保扩散模型 (如 Imagen) 在生图时有基础的环境语义作为支撑。
 */
@Serializable(with = UIBlockTypeSerializer::class)
enum class UIBlockType(val defaultPrompt: String) {
    // ---- (Slots) 专用核心组件 ----
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
    /** 顶部导航栏：游戏大标题、个人信息及资产显示区 */
    HEADER("Top header bar, title area, stylized top banner"),
    /** 底部状态栏：通常用于快捷导航或状态底座 */
    FOOTER("Bottom footer bar, navigation or status area, grounded base"),

    // ---- 通用交互与排版组件 (泛用 UI 解析) ----
    /** 普通交互按钮：例如充值、菜单等可点击组件 */
    BUTTON("Generic interactive button, polished UI element, clickable style"),
    /** 基础视图内容面板/底座：用于容纳文字或图标的半透明、实心底板框架 */
    VIEW("Content view or container box, semi-transparent or solid background, UI frame"),
    /** 纯文本显示区：易于阅读的文本底托板 */
    TEXT("Text display area, readable background plate for typography"),
    /** 独立图片/图标：静态图片、标志或装饰性图形 */
    IMAGE("Graphic image or icon, visual asset, crisp vector or raster style"),
    /** 下拉选择框：带有选项列表的交互组件 */
    COMBO_BOX("Dropdown combo box, selection menu UI element, interactive list"),
    /** 进度条：用于显示加载或进度的条形组件 */
    PROGRESS_BAR("Progress bar, loading indicator, linear scale UI element"),
    /** 弹出菜单：点击后出现的浮动菜单栏 */
    POPUP_MENU("Popup menu, floating context menu, list of actions UI element"),
    /** 动态加载器/占位符：表示该模块图片可被动态替换，通常带有装饰边框 */
    LOADER("Dynamic image loader placeholder, media frame, interchangeable asset area"),
    /** 滚动条：用于长内容导航的条状控件 */
    SCROLL_BAR("Scrollbar, navigation slider for long content, sleek scroll track"),
    /** 滑块：用于数值调节的交互组件 */
    SLIDER("Interactive slider, adjustment knob, range selector UI element"),
    /** 输入框：用于文本输入的编辑框 */
    INPUT("Text input field, editable text box, entry area with cursor hint")
}
