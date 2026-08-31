package org.gemini.ui.forge.ui.feature.gameproject.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 游戏 HTML 预览面板（平台抽象）。
 * 桌面端 (JVM) 使用嵌入式 Chromium (jcefmaven) 渲染 build 目录中的 HTML 页面；
 * 其余平台暂以占位提示实现（待适配，遵循桌面版优先规范）。
 *
 * @param htmlPath 当前要加载的 HTML 文件绝对路径；null 表示未选择页面
 * @param debugMode 调试模式开关：开启后注入 laya.debugtool.js 并建立调试桥
 * @param inspectTarget 当前要查询属性的调试节点 ID（值变化时触发一次属性查询）
 * @param onDebugMessage 调试桥消息回调（JSON 原文，common 层解析为 DebugBridgeMessage）
 * @param modifier 布局修饰符
 */
@Composable
expect fun GameHtmlPreview(
    htmlPath: String?,
    debugMode: Boolean = false,
    inspectTarget: String? = null,
    onDebugMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
)
