package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable

/**
 * 枚举类：定义所有的基础 UI 功能模块类型 (UIBlockType)
 *
 * 【纯净律】：
 * 1. 仅保留纯粹通用的基础 UI 组件类型，剥离特定游戏业务强耦合类型；
 * 2. 枚举自身只带类型名称，提示词正文 100% 独立存储于 prompts/block_type_prompts.json 文件中。
 */
@Serializable(with = UIBlockTypeSerializer::class)
enum class UIBlockType {
    /** 普通交互按钮：例如旋转、充值、返回、菜单等可点击组件 */
    BUTTON,
    /** 基础组件容器/引用独立子页面的组件实例：用于容纳子图元或承载独立子页面的复合组件 */
    COMPONENT,
    /** 纯文本显示区：数值、标题或状态文案标签 */
    TEXT,
    /** 独立图片/图标/背景底图：静态图片、标志、符号或装饰性图形 */
    IMAGE,
    /** 动态滚动列表/网格容器：横向或纵向排列滚动的列表控件 (如 GList / PureList) */
    LIST,
    /** 骨骼动画组件：动态 Spine 骨骼动画或特效节点 (如 GLoader3D / GSpineSkeleton) */
    SPINE,
    /** 下拉选择框：带有选项列表的交互组件 */
    COMBO_BOX,
    /** 进度条：用于显示加载或进度的条形组件 */
    PROGRESS_BAR,
    /** 弹出菜单：点击后出现的浮动菜单栏 */
    POPUP_MENU,
    /** 动态加载器/占位符：表示该模块图片或资源可被动态替换装载 (如 GLoader) */
    LOADER,
    /** 滚动条：用于长内容导航的条状控件 */
    SCROLL_BAR,
    /** 滑块：用于数值调节的交互组件 */
    SLIDER,
    /** 输入框：用于文本输入的编辑框 */
    INPUT;

    /**
     * 获取当前图元类型的默认英文生图提示词（按需从外部配置中懒加载，枚举自身零内容）
     */
    val defaultPrompt: String
        get() = BlockTypePromptProvider.getPrompt(this)

    /**
     * 核心特性标识：该类型组件是否拥有专属的属性配置面板
     */
    val hasSpecificProperties: Boolean
        get() = true

    /**
     * 核心特性标识：该类型组件是否支持在未绑定正式图片时，从底图对应全局坐标中提取切片定位渲染。
     * 排除黑名单：列表容器(LIST)、文本(TEXT)、输入框(INPUT)。
     */
    val supportsReferenceSlice: Boolean
        get() = this !in listOf(LIST, TEXT, INPUT)
}
