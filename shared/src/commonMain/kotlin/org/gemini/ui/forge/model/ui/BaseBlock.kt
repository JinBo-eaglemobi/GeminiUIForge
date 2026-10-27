package org.gemini.ui.forge.model.ui

import androidx.compose.runtime.Stable

/**
 * 图元公用父类 (BaseBlock)
 *
 * 提取自 UIBlock 的纯几何/层级/结构公用部分，作为传统 Slots 图元 [UIBlock]
 * 与 UI Studio 专用图元 [UIStudioBlock] 的共同父类。
 *
 * 序列化设计铁律：本类**禁止**添加 @Serializable 注解 —— kotlinx-serialization 仅处理
 * 子类自声明（含 override 构造）属性，从而保证两个子类的 JSON 均为扁平结构、
 * 无多态 type 标签，传统 Slots 工程 JSON 结构 100% 零变化。
 *
 * 树重建协议：子类必须实现 [withChildren] / [withParent]，
 * 供泛型树工具（bindParents / updateBlockInList 等）在不依赖具体子类 copy 的情况下重建树结构。
 */
@Stable
abstract class BaseBlock {

    /** 该模块的全局唯一标识符 */
    abstract val id: String

    /** 该模块所属的功能分类 */
    abstract val type: UIBlockType

    /** 该模块相对于其直接父容器的局部相对坐标矩形（持久化保存此值） */
    abstract val bounds: SerialRect

    /** 相对于原始图片的局部裁切区域坐标 */
    abstract val cropRect: SerialRect?

    /** 嵌套在其内部的子图元列表（子类协变覆写为具体类型） */
    abstract val children: List<BaseBlock>

    /** 图层当前是否在画布和预览中可见 */
    abstract val isVisible: Boolean

    /** 图层是否锁定（锁定后在画布上禁止选中和拖动） */
    abstract val isLocked: Boolean

    /** 不同组件分类的专属附加属性（如 ButtonProperties, SpinProperties 等） */
    abstract val properties: BlockProperties?

    /** 纯容器/占位层标记：true 表示该模块仅用于排版或组合，不参与任何 AI 图片资源生成 */
    abstract val isPureContainer: Boolean

    /** 是否裁剪超出范围的子内容（overflow: hidden），默认 false 为可见 */
    abstract val clipOverflow: Boolean

    /** 独立水平缩放变换因数 (默认 1.0f 为原寸) */
    abstract val scaleX: Float

    /** 独立垂直缩放变换因数 (默认 1.0f 为原寸) */
    abstract val scaleY: Float

    /** 独立不透明度 (默认 1.0f 为完全不透明，范围 0.0~1.0) */
    abstract val opacity: Float

    /** 运行时持有的直接父级引用（子类以 @Transient 协变覆写，阻断序列化） */
    abstract val parent: BaseBlock?

    /** 以新子图元列表重建当前模块副本（树重建协议，子类基于 copy 实现） */
    abstract fun withChildren(children: List<BaseBlock>): BaseBlock

    /** 以新父级引用重建当前模块副本（树重建协议，子类基于 copy 实现） */
    abstract fun withParent(parent: BaseBlock?): BaseBlock

    /**
     * 生效的水平缩放比例（允许设置为 0，完全忠实反映用户设定与变换）
     */
    val effectiveScaleX: Float
        get() = scaleX

    /**
     * 生效的垂直缩放比例（允许设置为 0，完全忠实反映用户设定与变换）
     */
    val effectiveScaleY: Float
        get() = scaleY

    /**
     * 生效的不透明度（允许设置为 0 完全透明，范围 0.0~1.0）
     */
    val effectiveOpacity: Float
        get() = opacity.coerceIn(0.0f, 1.0f)

    /**
     * 判断当前模块是否开启了溢出子内容裁剪/隐藏 (overflow: hidden)
     */
    val shouldClipOverflow: Boolean
        get() = clipOverflow || (properties as? BlockProperties.ViewProperties)?.clipOverflow == true

    /**
     * 当前模块在页面全景大图上的全局绝对逻辑矩形 (核心只读计算属性)
     *
     * 性能与数学铁律：
     * 1. 纯二维平移变换，模块宽高绝对恒定，100% 不参与平移计算；
     * 2. 扁平 while 循环直接累加原始浮点数位移，中间过程 0 临时对象分配 (Zero Allocation)。
     *
     * @return 当前模块在整张页面全景大图坐标系下的全局绝对逻辑矩形 [SerialRect]
     */
    val absoluteBounds: SerialRect
        get() {
            var cur = parent
            var ox = 0f
            var oy = 0f
            while (cur != null) {
                ox += cur.bounds.left
                oy += cur.bounds.top
                cur = cur.parent
            }
            val absLeft = bounds.left + ox
            val absTop = bounds.top + oy
            return SerialRect(
                left = absLeft,
                top = absTop,
                right = absLeft + bounds.width,
                bottom = absTop + bounds.height
            )
        }

    /**
     * 无参直接获取全局绝对逻辑矩形（函数式别名，等价于直接访问 [absoluteBounds] 属性）
     *
     * @return 当前模块在全景大图坐标系下的全局绝对逻辑矩形 [SerialRect]
     */
    fun toAbsoluteBounds(): SerialRect = absoluteBounds

    /**
     * 将全局绝对矩形逆向换算回相对于当前模块直接父容器的局部相对矩形 (对称逆向方法)
     *
     * 宽高恒定保持一致，仅做原点平移反算，中间过程 0 临时对象创建。
     *
     * @param absRect 在整页全景大图绝对坐标系下的目标矩形（如用户拖拽、画框选区或画布吸附计算出的绝对矩形）
     * @return 逆向扣除所有父级累计位移后，适合直接持久化写回当前模块 [bounds] 的局部相对矩形 [SerialRect]
     */
    fun toLocalBounds(absRect: SerialRect): SerialRect {
        var cur = parent
        var ox = 0f
        var oy = 0f
        while (cur != null) {
            ox += cur.bounds.left
            oy += cur.bounds.top
            cur = cur.parent
        }
        val relLeft = absRect.left - ox
        val relTop = absRect.top - oy
        return SerialRect(
            left = relLeft,
            top = relTop,
            right = relLeft + absRect.width,
            bottom = relTop + absRect.height
        )
    }
}
