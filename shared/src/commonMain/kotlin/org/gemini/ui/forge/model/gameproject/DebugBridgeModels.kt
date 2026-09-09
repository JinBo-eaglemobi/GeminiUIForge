package org.gemini.ui.forge.model.gameproject

import kotlinx.serialization.Serializable

/**
 * 调试树节点模型（右侧调试面板用）。
 * 对应 laya.debugtool.js 中 DebugPanel._treeDataList 的节点结构
 * （{ text, id, item }，其中 target 引用被剔除，仅保留可序列化字段）。
 */
@Serializable
data class DebugTreeNode(
    /** 节点显示文本（类名 + 节点名） */
    val text: String,
    /** 调试工具注册的对象 ID（用于选中与属性查询） */
    val id: String = "",
    /** 子节点列表 */
    val item: List<DebugTreeNode> = emptyList()
)

/**
 * 调试属性行模型（单行显示一条）。
 * 对应 DebugPanel.getObjectData 生成的 { key, value, type } 结构。
 */
@Serializable
data class DebugPropertyRow(
    /** 属性名 */
    val key: String,
    /** 属性值（字符串化） */
    val value: String,
    /** 属性类型（number/boolean/string 等） */
    val type: String = "string"
)

/**
 * 调试桥消息信封：CEF cefQuery 传回 Kotlin 侧的 JSON 外壳。
 */
@Serializable
data class DebugBridgeMessage(
    /** 消息类型：tree（节点树）/ props（属性列表）/ log（日志） */
    val type: String,
    /** 树数据（type=tree 时使用） */
    val tree: DebugTreeNode? = null,
    /** 属性列表（type=props 时使用） */
    val props: List<DebugPropertyRow> = emptyList(),
    /** 日志/附加文本 */
    val text: String = ""
)
