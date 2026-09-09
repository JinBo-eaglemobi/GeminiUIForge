package org.gemini.ui.forge.utils

actual fun printToConsole(level: String, tag: String, message: String, throwable: Throwable?) {
    val formattedMsg = "[$tag] $message"
    when (level) {
        "DEBUG" -> if (throwable != null) console.log(formattedMsg, throwable) else console.log(formattedMsg)
        "INFO" -> if (throwable != null) console.info(formattedMsg, throwable) else console.info(formattedMsg)
        "ERROR" -> if (throwable != null) console.error(formattedMsg, throwable) else console.error(formattedMsg)
        else -> console.log(formattedMsg)
    }
}

actual fun getPlatformLogDirectory(): String {
    // 浏览器没有传统的文件系统绝对路径，我们使用基于 OPFS 的虚拟路径标志
    return "/geminiuiforge_logs"
}
