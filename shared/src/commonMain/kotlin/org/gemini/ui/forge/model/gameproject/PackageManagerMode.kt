package org.gemini.ui.forge.model.gameproject

/**
 * 游戏项目使用的包管理模式枚举。
 * 选择后会立即激活对应的本地环境检测（node / npm / pnpm）。
 */
enum class PackageManagerMode {
    /** 使用 npm 管理依赖 */
    NPM,

    /** 使用 pnpm 管理依赖（本项目强制要求存在 pnpm 环境） */
    PNPM
}
