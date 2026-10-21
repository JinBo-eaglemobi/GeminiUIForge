package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable

/**
 * 枚举类：定义所有的 UI 功能模块类型 (UIBlockType)
 * 供 Gemini 视觉大模型在分析图片时进行分类标记。
 *
 * 【纯净律】：
 * 本枚举严格遵循纯净律，枚举中只带类型名称，绝不包含任何提示词内容！
 * 提示词正文 100% 独立存储于 prompts/block_type_prompts.json 文件中，
 * 通过 defaultPrompt 属性按需从 BlockTypePromptProvider 懒加载获取。
 */
@Serializable(with = UIBlockTypeSerializer::class)
enum class UIBlockType {
    // ---- (Slots) 专用核心组件 ----
    /** 核心转轴区域：包含 3x5 等网格和抛光的边框 */
    REEL,
    /** 旋转主按钮：典型的圆形豪华外观与光泽质感 */
    SPIN_BUTTON,
    /** 赢分数字显示框：带有豪华背景板和数字显示区域的矩形框 */
    WIN_DISPLAY,
    /** 满屏游戏主背景：沉浸式游戏氛围，有主题感 */
    BACKGROUND,
    /** 单个图标标志：例如转轴上的水果/皇冠等高品质符号 */
    SYMBOL,
    /** 顶部导航栏：游戏大标题、个人信息及资产显示区 */
    HEADER,
    /** 底部状态栏：通常用于快捷导航或状态底座 */
    FOOTER,

    // ---- 通用交互与排版组件 (泛用 UI 解析) ----
    /** 普通交互按钮：例如充值、菜单等可点击组件 */
    BUTTON,
    /** 基础视图内容面板/底座：用于容纳文字或图标的半透明、实心底板框架 */
    VIEW,
    /** 纯文本显示区：易于阅读的文本底托板 */
    TEXT,
    /** 独立图片/图标：静态图片、标志或装饰性图形 */
    IMAGE,
    /** 下拉选择框：带有选项列表的交互组件 */
    COMBO_BOX,
    /** 进度条：用于显示加载或进度的条形组件 */
    PROGRESS_BAR,
    /** 弹出菜单：点击后出现的浮动菜单栏 */
    POPUP_MENU,
    /** 动态加载器/占位符：表示该模块图片可被动态替换，通常带有装饰边框 */
    LOADER,
    /** 滚动条：用于长内容导航的条状控件 */
    SCROLL_BAR,
    /** 滑块：用于数值调节的交互组件 */
    SLIDER,
    /** 输入框：用于文本输入的编辑框 */
    INPUT,
    /** 纯容器/占位层：仅用于占位排版或承载组合子组件，不参与任何图片资源生成 */
    CONTAINER;

    /**
     * 获取当前图元类型的默认英文生图提示词（按需从外部配置中懒加载，枚举自身零内容）
     */
    val defaultPrompt: String
        get() = BlockTypePromptProvider.getPrompt(this)

    /**
     * 核心特性标识：该类型组件是否拥有专属的属性配置面板
     */
    val hasSpecificProperties: Boolean
        get() = this in listOf(REEL, SPIN_BUTTON, BUTTON, VIEW, TEXT, INPUT)

    /**
     * 核心特性标识：该类型组件是否支持在未绑定正式图片时，从底图对应全局坐标中提取切片定位渲染。
     * 排除黑名单：转轴网格(REEL)、顶部导航栏(HEADER)、底部状态栏(FOOTER)、文本(TEXT)、输入框(INPUT)、纯容器(CONTAINER)。
     * 其余所有类型一律支持并保持原图切片定位展示。
     */
    val supportsReferenceSlice: Boolean
        get() = this !in listOf(REEL, HEADER, FOOTER, TEXT, INPUT, CONTAINER)
}
