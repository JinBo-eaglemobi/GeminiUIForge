package org.gemini.ui.forge.model.gameproject

/**
 * 单个 pnpm 依赖项的检查结果。
 * installed 为 false 时在依赖列表中以红色显示，并由用户选择安装方式。
 */
data class PnpmDependencyStatus(
    /** 依赖包名 */
    val name: String,
    /** package.json 中声明的版本范围（如 ^2.4.1） */
    val versionRange: String,
    /** 是否属于 devDependencies */
    val isDev: Boolean,
    /** 本地 node_modules 中是否已存在该依赖 */
    val installed: Boolean,
    /** 用户选择的安装方式：null=未选择，SKIP=不安装，PNPM=pnpm 安装 */
    val installChoice: InstallChoice? = null
)

/**
 * 依赖缺失时用户可选的安装方式。
 */
enum class InstallChoice {
    /** 不安装（跳过） */
    SKIP,

    /** 使用 pnpm 安装 */
    PNPM
}
