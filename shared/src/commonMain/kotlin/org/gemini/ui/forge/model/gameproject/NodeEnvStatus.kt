package org.gemini.ui.forge.model.gameproject

/**
 * 本地 Node.js 相关环境的检测结果快照。
 * 由 NodeEnvService 在用户选择包管理模式后自动检测填充。
 */
data class NodeEnvStatus(
    /** nodejs 是否已安装 */
    val nodeInstalled: Boolean = false,
    /** nodejs 版本号（如 v20.11.0），未安装时为 null */
    val nodeVersion: String? = null,
    /** npm 是否可用 */
    val npmInstalled: Boolean = false,
    /** npm 版本号，不可用时为 null */
    val npmVersion: String? = null,
    /** npm 全局仓库源地址（registry 配置），不可用时为 null */
    val npmRegistry: String? = null,
    /** npm 全局安装路径（prefix 配置），不可用时为 null */
    val npmPrefix: String? = null,
    /** pnpm 是否已安装 */
    val pnpmInstalled: Boolean = false,
    /** pnpm 版本号，未安装时为 null */
    val pnpmVersion: String? = null,

    /** Git 是否已安装 */
    val gitInstalled: Boolean = false,
    /** Git 版本号，未安装时为 null */
    val gitVersion: String? = null,

    /** Python 是否已安装 */
    val pythonInstalled: Boolean = false,
    /** Python 版本号，未安装时为 null */
    val pythonVersion: String? = null,
    /** pip 是否已安装 */
    val pipInstalled: Boolean = false,
    /** pip 版本号，未安装时为 null */
    val pipVersion: String? = null,

    /** Java/JDK 是否已安装 */
    val javaInstalled: Boolean = false,
    /** Java 版本号，未安装时为 null */
    val javaVersion: String? = null,

    /** Gradle 是否已安装 */
    val gradleInstalled: Boolean = false,
    /** Gradle 版本号，未安装时为 null */
    val gradleVersion: String? = null
) {
    /**
     * nodejs 未安装视为硬性阻断：后续创建流程的确认按钮必须禁用，
     * 只能通过重新检测刷新或引导用户安装 nodejs。
     */
    val isHardBlocked: Boolean get() = !nodeInstalled
}
