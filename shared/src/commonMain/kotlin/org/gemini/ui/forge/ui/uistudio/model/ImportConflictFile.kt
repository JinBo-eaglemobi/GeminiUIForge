package org.gemini.ui.forge.ui.uistudio.model

import kotlinx.serialization.Serializable

/**
 * 单个重名冲突文件：源绝对路径 + 期望落盘文件名（已清洗）+ 内容短哈希。
 */
@Serializable
data class ImportConflictFile(
    val sourcePath: String,
    val desiredFileName: String,
    val fileHash: String
)
