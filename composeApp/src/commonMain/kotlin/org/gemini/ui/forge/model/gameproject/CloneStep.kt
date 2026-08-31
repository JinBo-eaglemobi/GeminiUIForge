package org.gemini.ui.forge.model.gameproject

/**
 * 游戏项目克隆流程的执行步骤枚举。
 * 克隆采用"先扫描后选择"的两段式流程：完成 SCAN_GAMES 后挂起等待用户选择目标游戏。
 */
enum class CloneStep {
    /** 创建本地项目文件夹 */
    CREATE_FOLDER,

    /** 连接远端并执行 blobless 拉取（仅拉取目录树，不下载文件内容） */
    CONNECT_FETCH,

    /** 等待用户在弹窗中选择要检出的版本（分支 / 标签 / 提交，挂起交互步骤） */
    SELECT_VERSION,

    /** 扫描 game/src/ 下的游戏列表（排除 main） */
    SCAN_GAMES,

    /** 等待用户在弹窗中选择要管理的游戏（挂起交互步骤） */
    WAIT_SELECT_GAME,

    /** 应用仓库过滤配置（读取 .downloadignore 并解析剔除规则） */
    APPLY_FILTER,

    /** 按选择的游戏执行 sparse-checkout 资源下载 */
    DOWNLOAD_ASSETS,

    /** 执行 pnpm 依赖库检查 */
    CHECK_DEPENDENCIES,

    /** 全部完成 */
    COMPLETE,

    /** 流程失败 */
    FAILED
}
