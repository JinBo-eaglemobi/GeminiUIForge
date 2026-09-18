package org.gemini.ui.forge.utils

/**
 * 全局统一的 JVM 启动参数标准定义与单一数据源字典 (Single Source of Truth, SSOT)
 *
 * 彻底消除在 Gradle、代码、本地文件中到处手动修改启动参数的割裂问题。
 * 一处定义，构建脚本、运行时自愈、打包发行全自动同步响应。
 */
object JvmOptionsDefaults {

    /**
     * 系统级底层必需参数集合（消除 JDK 24/25 JNI 原生访问警告、编码统一等）
     */
    val MANDATORY_SYSTEM_ARGS: List<String> = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dstdout.encoding=utf-8",
        "-Dstderr.encoding=utf-8",
        "-Dsun.stdout.encoding=utf-8",
        "-Dsun.stderr.encoding=utf-8"
    )

    /**
     * 默认推荐的内存配置参数
     */
    val DEFAULT_MEMORY_ARGS: List<String> = listOf(
        "-Xmx1G",
        "-Xms512M"
    )

    /**
     * 获取全量基础默认参数组合
     */
    fun getFullDefaultOptions(): List<String> {
        return DEFAULT_MEMORY_ARGS + MANDATORY_SYSTEM_ARGS
    }
}
