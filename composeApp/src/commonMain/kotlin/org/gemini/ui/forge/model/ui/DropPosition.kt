package org.gemini.ui.forge.model.ui
/**
 * 定义拖拽放置图层时的相对位置
 */
enum class DropPosition {
    BEFORE,  // 插入到目标图层之前（同级）
    INSIDE,  // 插入到目标图层内部（作为子图层）
    AFTER    // 插入到目标图层之后（同级）
}
