package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import androidx.compose.runtime.Stable

/**
 * UI Studio 专用图元 (UIStudioBlock)
 *
 * UI Studio 独立工程的最小排版单元，继承公用父类 [BaseBlock]（几何/层级/结构）。
 *
 * 架构铁律：本类**不再持有任何图片路径属性**（无 currentImageUri / referenceImage），
 * 图片引用唯一走 [assetHash] 素材哈希绑定 —— 素材库 relativePath 是哈希的「当前路径账本」，
 * 库内移动/改名仅更新账本，图元只认哈希，从根本上杜绝引用断链。
 *
 * @property id 该图元的全局唯一标识符
 * @property type 该图元所属的功能分类
 * @property bounds 该图元相对于其直接父容器的局部相对坐标矩形（持久化保存此值）
 * @property assetHash 唯一图片引用：绑定素材的 SHA-256 文件哈希（仅首次导入生成，永不变化）
 * @property cropRect 相对于绑定图片的局部裁切区域坐标
 * @property children 嵌套在其内部的子图元列表
 * @property isVisible 图层当前是否在画布和预览中可见
 * @property isLocked 图层是否锁定（锁定后在画布上禁止选中和拖动）
 * @property properties 不同类型图元的专属附加属性
 * @property isPureContainer 纯容器/占位层标记：true 表示该图元仅用于排版或组合
 * @property clipOverflow 是否裁剪超出范围的子内容（overflow: hidden）
 * @property scaleX 独立水平缩放变换因数
 * @property scaleY 独立垂直缩放变换因数
 * @property opacity 独立不透明度（范围 0.0~1.0）
 * @property parent 运行时持有的直接父级引用（@Transient 阻断序列化，copy() 自动继承）
 */
@Stable
@Serializable
data class UIStudioBlock(
    override val id: String,
    override val type: UIBlockType,
    override val bounds: SerialRect,
    val assetHash: String? = null, // ★ 唯一图片引用：素材 SHA-256 哈希（路径由素材库账本动态解析）
    override val cropRect: SerialRect? = null,
    override val children: List<UIStudioBlock> = emptyList(),
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    override val properties: BlockProperties? = null,
    override val isPureContainer: Boolean = false,
    override val clipOverflow: Boolean = false,
    override val scaleX: Float = 1.0f,
    override val scaleY: Float = 1.0f,
    override val opacity: Float = 1.0f,
    // ★ 运行时持有直接父级引用，主构造函数声明 + @Transient 阻断 JSON 序列化，copy() 自动继承，永不断裂！
    @Transient
    override val parent: UIStudioBlock? = null
) : BaseBlock() {

    /** 树重建协议：以新子图元列表重建副本（树内类型同质，过滤收敛安全） */
    override fun withChildren(children: List<BaseBlock>): BaseBlock {
        return copy(children = children.filterIsInstance<UIStudioBlock>())
    }

    /** 树重建协议：以新父级引用重建副本（树内类型同质，收敛转换安全） */
    override fun withParent(parent: BaseBlock?): BaseBlock {
        return copy(parent = parent as? UIStudioBlock)
    }
}
