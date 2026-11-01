package org.gemini.ui.forge.ui.uistudio.model

/**
 * 外部素材导入重名冲突处理策略（对齐市场主流：替换 / 保留两者 / 跳过）。
 */
enum class ImportConflictStrategy {
    /** 替换：覆盖目标目录下同名文件内容，仅刷新条目宽高与更新时间，hash/id/引用链不动 */
    OVERWRITE,
    /** 保留两者：自动追加 _N 序号别名后作为新条目入库 */
    KEEP_BOTH,
    /** 跳过：放弃导入冲突文件 */
    SKIP
}
