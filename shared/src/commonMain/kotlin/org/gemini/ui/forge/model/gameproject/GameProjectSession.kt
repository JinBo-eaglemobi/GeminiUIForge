package org.gemini.ui.forge.model.gameproject

/**
 * 当前正在操作的游戏项目会话单例。
 * 用于在导航（创建完成 → 工作台、大厅卡片 → 工作台）时传递 GameProjectInfo 上下文，
 * 避免在 Compose 参数链中层层透传。
 */
object GameProjectSession {
    /** 当前会话激活的项目信息，进入工作台前必须设置 */
    var currentProject: GameProjectInfo? = null
}
