package org.gemini.ui.forge.model.app

import kotlinx.serialization.Serializable

/**
 * 编译环境配置模型。
 * 包含运行环境根目录、编译脚本路径以及资源保存目录。
 */
@Serializable
data class CompileConfig(
    val rootDir: String = "",
    val scriptPath: String = "",
    val outputDir: String = ""
)
