package org.gemini.ui.forge.ui.feature.gameproject.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_cef_auto_download
import geminiuiforge.composeapp.generated.resources.gp_cef_dl_stats
import geminiuiforge.composeapp.generated.resources.gp_cef_downloading
import geminiuiforge.composeapp.generated.resources.gp_cef_download_page
import geminiuiforge.composeapp.generated.resources.gp_cef_failed
import geminiuiforge.composeapp.generated.resources.gp_cef_need_manual_desc
import geminiuiforge.composeapp.generated.resources.gp_cef_need_manual_title
import geminiuiforge.composeapp.generated.resources.gp_cef_open_dir
import geminiuiforge.composeapp.generated.resources.gp_cef_preparing
import geminiuiforge.composeapp.generated.resources.gp_copy_success
import geminiuiforge.composeapp.generated.resources.gp_preview_none
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.service.CefEnvironment
import org.gemini.ui.forge.utils.ChunkStatus
import org.gemini.ui.forge.utils.DownloadProgress
import org.gemini.ui.forge.utils.DownloadResult
import org.gemini.ui.forge.utils.FileDownloader
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.AppLogger
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import org.jetbrains.compose.resources.stringResource
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.gemini.ui.forge.extend.toClipEntry

/**
 * CEF 预览面板的内部状态机。
 */
private enum class CefPanelState {
    /** 正在检测 / 准备环境 */
    CHECKING,

    /** 环境就绪，可创建浏览器 */
    READY,

    /** 需要用户手动预置资源或选择在线下载 */
    NEED_MANUAL,

    /** 正在在线下载 natives */
    DOWNLOADING,

    /** 初始化失败 */
    FAILED
}

/**
 * 注入页面的调试桥脚本。
 * 职责：加载 laya.debugtool.js（若页面未内置）→ 初始化 DebugPanel →
 * 隐藏其自带 DOM 面板 → 周期性推送节点树 / 按选中 ID 查询属性，
 * 并通过 window.cefQuery 将 JSON 结果回传 Kotlin 侧。
 */
private const val DEBUG_BRIDGE_JS = """
(function () {
  if (window.__gufBridge) { window.__gufBridge.start(); return; }
  // 经 cefQuery 回传 JSON 消息（type: tree / props / log）
  var send = function (obj) {
    try {
      window.cefQuery({
        request: JSON.stringify(obj),
        onSuccess: function () {},
        onFailure: function (errCode, msg) {}
      });
    } catch (e) {}
  };
  var log = function (t) { send({ type: 'log', text: String(t) }); };
  // 递归转换 debugtool 树节点（剔除 target 引用，仅保留可序列化字段）
  var conv = function (n) {
    var kids = n['item'] || [];
    var arr = [];
    for (var i = 0; i < kids.length; i++) arr.push(conv(kids[i]));
    return {
      text: String(n['text'] || ''),
      id: String(n['id'] !== undefined ? n['id'] : ''),
      item: arr
    };
  };
  // 按 ID 在树中查找原始节点（含 target 引用）
  var findNode = function (id, n) {
    if (String(n['id'] || '') === String(id)) return n;
    var kids = n['item'] || [];
    for (var i = 0; i < kids.length; i++) {
      var r = findNode(id, kids[i]);
      if (r) return r;
    }
    return null;
  };
  var B = {
    selectedId: null,
    treeTimer: null,
    propTimer: null,
    panel: function () {
      try { return window.DebugPanel && window.DebugPanel.I; } catch (e) { return null; }
    },
    stage: function () {
      try { return window.Laya && (window.Laya.Laya ? window.Laya.Laya.stage : window.Laya.stage); } catch (e) { return null; }
    },
    // 确保 debugtool 脚本已加载：优先复用页面内置，否则注入脚本标签
    ensureTool: function (cb) {
      if (window.DebugPanel) { cb(true); return; }
      var url = window.__gufDebugToolUrl;
      if (!url) { log('laya.debugtool.js 地址未提供，无法注入'); cb(false); return; }
      var s = document.createElement('script');
      s.src = url;
      s.onload = function () { cb(true); };
      s.onerror = function () { log('laya.debugtool.js 加载失败: ' + url); cb(false); };
      document.head.appendChild(s);
    },
    // 轮询等待 DebugPanel 单例就绪（首次会尝试 DebugTool.init）
    ensurePanel: function (cb, tries) {
      tries = tries || 0;
      if (tries > 40) { log('等待 DebugPanel 就绪超时'); return; }
      var p = B.panel();
      if (p) { cb(p); return; }
      if (tries === 0) {
        try {
          if (window.DebugTool && window.DebugTool.init) window.DebugTool.init();
        } catch (e) { log('DebugTool.init 调用失败: ' + e); }
      }
      setTimeout(function () { B.ensurePanel(cb, tries + 1); }, 250);
    },
    // 启动调试桥：隐藏自带面板 + 建树 + 定时刷新
    start: function () {
      B.stop();
      B.ensureTool(function (ok) {
        if (!ok) return;
        B.ensurePanel(function (p) {
          try {
            // 隐藏调试工具自带的 DOM 面板，数据改由应用右侧面板展示
            if (p.div && p.div.style) p.div.style.display = 'none';
          } catch (e) {}
          try { if (p.setRoot) p.setRoot(B.stage()); } catch (e) { log('setRoot 失败: ' + e); }
          B.pushTree();
          B.treeTimer = setInterval(function () { B.pushTree(); }, 3000);
          B.propTimer = setInterval(function () { if (B.selectedId) B.queryProps(B.selectedId); }, 1000);
        });
      });
    },
    stop: function () {
      if (B.treeTimer) { clearInterval(B.treeTimer); B.treeTimer = null; }
      if (B.propTimer) { clearInterval(B.propTimer); B.propTimer = null; }
    },
    // 推送整棵节点树（序列化后回传）
    pushTree: function () {
      var p = B.panel(); if (!p) return;
      try {
        var list = p._treeDataList && p._treeDataList.length ? p._treeDataList : null;
        var root = list ? conv(list[0]) : null;
        send({ type: 'tree', tree: root });
      } catch (e) { log('pushTree 失败: ' + e); }
    },
    // 查询指定节点的属性列表（保持 selectedId 以便周期刷新）
    queryProps: function (id) {
      var p = B.panel(); if (!p) return;
      B.selectedId = id;
      try {
        var list = p._treeDataList; if (!list || !list.length) return;
        var node = findNode(id, list[0]);
        if (!node || !node.target) { send({ type: 'props', props: [] }); return; }
        var data = window.DebugPanel.getObjectData ? window.DebugPanel.getObjectData(node.target) : [];
        var rows = [];
        for (var i = 0; i < data.length; i++) {
          rows.push({
            key: String(data[i].key),
            value: String(data[i].value),
            type: String(data[i].type || 'string')
          });
        }
        send({ type: 'props', props: rows });
      } catch (e) { log('queryProps 失败: ' + e); }
    }
  };
  window.__gufBridge = B;
  log('调试桥已注入');
  B.start();
})();
"""

/**
 * 游戏 HTML 预览面板的桌面端实现。
 * 通过 jcefmaven 嵌入 Chromium，将 build 目录中的 HTML 页面渲染在 Compose 之内
 * （SwingPanel 承载 JCEF 的 AWT 组件）；调试模式下注入 laya.debugtool.js 并
 * 通过 CefMessageRouter 建立 JS -> Kotlin 的消息桥。
 */
@Composable
actual fun GameHtmlPreview(
    htmlPath: String?,
    debugMode: Boolean,
    inspectTarget: String?,
    onDebugMessage: (String) -> Unit,
    reloadTrigger: Int,
    devToolsTrigger: Int,
    isDemoSelected: Boolean,
    isDebugSelected: Boolean,
    selectedGame: String,
    onUrlComputed: (String) -> Unit,
    modifier: Modifier
) {
    var state by remember { mutableStateOf(CefPanelState.CHECKING) }
    // 在线下载进度快照（FileDownloader 提供：字节 / 总大小 / 速度 / 活跃连接 / 分块明细；null 表示无任务）
    var cefDownload by remember { mutableStateOf<DownloadProgress?>(null) }
    // 剪贴板与复制成功提示（下载地址一键复制用）
    val clipboard = LocalClipboard.current
    val copiedTip = stringResource(Res.string.gp_copy_success)
    var failureMessage by remember { mutableStateOf<String?>(null) }
    var browserRef by remember { mutableStateOf<CefBrowser?>(null) }
    var clientRef by remember { mutableStateOf<CefClient?>(null) }
    // 记录最近一次加载 of URL，避免 update 回调重复触发 loadURL
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    // 本地 HTTP 服务器状态，避免本地 HTML 在 file:/// 协议下遇到的各类跨源限制
    var localServer by remember { mutableStateOf<HttpServer?>(null) }
    var serverPort by remember { mutableStateOf<Int?>(null) }
    var currentRootDirPath by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf<String?>(null) }

    // 动态管理本地预览服务器的生命周期（只有目录变化时才重启服务器，保持物理端口相对稳定）
    LaunchedEffect(htmlPath) {
        val path = htmlPath
        if (path == null) {
            localServer?.stop(0)
            localServer = null
            serverPort = null
            serverUrl = null
            currentRootDirPath = null
            return@LaunchedEffect
        }

        val htmlFile = File(path)
        val rootDir = htmlFile.parentFile ?: return@LaunchedEffect

        val currentServer = localServer
        if (currentServer != null && rootDir.absolutePath == currentRootDirPath) {
            // 同一工作目录，直接重用当前 Server，不关闭，避免重新分配端口
            AppLogger.d("GameHtmlPreview", "重用当前本地服务，目录: ${rootDir.absolutePath}")
        } else {
            // 目录改变，重启本地预览服务
            currentServer?.stop(0)
            localServer = null
            serverPort = null
            try {
                // 使用随机空闲端口启动 JDK 轻量 HttpServer，默认绑定 localhost
                val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                server.createContext("/") { exchange ->
                    val uriPath = exchange.requestURI.path
                    // 防止路径遍历安全问题，简单过滤
                    val normalizedPath = uriPath.removePrefix("/").replace("..", "")
                    val targetFile = File(rootDir, normalizedPath)
                    if (targetFile.exists() && targetFile.isFile) {
                        val bytes = targetFile.readBytes()
                        val contentType = when (targetFile.extension.lowercase()) {
                            "html" -> "text/html; charset=utf-8"
                            "js" -> "application/javascript; charset=utf-8"
                            "css" -> "text/css; charset=utf-8"
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "xml" -> "text/xml; charset=utf-8"
                            "json" -> "application/json; charset=utf-8"
                            "wasm" -> "application/wasm"
                            else -> Files.probeContentType(targetFile.toPath()) ?: "application/octet-stream"
                        }
                        exchange.responseHeaders.set("Content-Type", contentType)
                        // 开启全跨域 CORS，让任何 AJAX 行为顺滑通畅
                        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                    } else {
                        val resp = "404 Not Found"
                        exchange.sendResponseHeaders(404, resp.length.toLong())
                        exchange.responseBody.use { it.write(resp.toByteArray()) }
                    }
                }
                server.executor = null
                server.start()
                localServer = server
                serverPort = server.address.port
                currentRootDirPath = rootDir.absolutePath
                AppLogger.i("GameHtmlPreview", "🚀 游戏预览本地服务器启动成功，分配端口: ${server.address.port}")
            } catch (e: Exception) {
                AppLogger.e("GameHtmlPreview", "❌ 启动本地服务失败: ${e.message}", e)
            }
        }
    }

    // 反应式拼接并热交换 URL（参数 gameId/demo/debug 变化时毫秒级重算，不重启服务器）
    LaunchedEffect(htmlPath, serverPort, isDemoSelected, isDebugSelected) {
        val path = htmlPath ?: return@LaunchedEffect
        val port = serverPort ?: return@LaunchedEffect
        val htmlFile = File(path)
        val rootDir = htmlFile.parentFile ?: return@LaunchedEffect

        // 1. 从 build/assets/configs/gameConfig.js 的 gameIdConfig 映射中匹配获取属于当前游戏文件夹名的数字 ID (如 3032)
        val gameConfigJsFile = File(rootDir, "assets/configs/gameConfig.js")
        var gameId = ""
        if (gameConfigJsFile.exists()) {
            try {
                val text = gameConfigJsFile.readText()
                // 正则匹配 gameIdConfig = { ... } 内部的大括号定义
                val blockMatch = """gameIdConfig\s*=\s*\{([^}]+)}""".toRegex(RegexOption.IGNORE_CASE).find(text)
                if (blockMatch != null) {
                    val block = blockMatch.groupValues[1]
                    // 匹配其中的每一行，如 3032: "Premier League Star",
                    val lineRegex = """(\d+)\s*:\s*["']([^"']+)["']""".toRegex()
                    // 归一化选中的游戏目录名，移除非必要符号和大小写（如 premierLeagueStar -> premierleaguestar）
                    val normalizedSelected = selectedGame.lowercase().replace(" ", "").replace("_", "").replace("-", "")

                    for (lineMatch in lineRegex.findAll(block)) {
                        val id = lineMatch.groupValues[1]
                        val name = lineMatch.groupValues[2]
                        // 归一化配置里的游戏英文名，以进行大小写/空格不敏感的安全匹配
                        val normalizedName = name.lowercase().replace(" ", "").replace("_", "").replace("-", "")
                        if (normalizedSelected == normalizedName) {
                            gameId = id
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("GameHtmlPreview", "解析 gameConfig.js 中的 gameIdConfig 失败: ${e.message}")
            }
        }

        // 1.2 强力双向兜底机制：若未从 gameConfig.js 中匹配到对应的数字 ID，自动以当前选中的游戏名（selectedGame）作为 gameId 填充，
        // 同时在 URL 中增加 gameName=游戏名 以提供完备的 JS 兼容保障！
        val resolvedGameId = gameId.ifBlank { selectedGame }

        // 2. 依据 UI 状态计算 0/1 参数值
        val demoVal = if (isDemoSelected) 1 else 0
        val debugVal = if (isDebugSelected) 1 else 0

        // 3. 构建含有参数的完整 URL，热提供给 JCEF
        val finalUrl = "http://127.0.0.1:$port/${htmlFile.name}?gameId=$resolvedGameId&gameName=$selectedGame&demo=$demoVal&debug=$debugVal"
        serverUrl = finalUrl
        onUrlComputed(finalUrl) // ★ 回传给上层工作台，以供拟真地址栏展示与一键复制！
        AppLogger.i("GameHtmlPreview", "🔗 游戏预览参数已重组: $serverUrl")
    }

    // 监听刷新触发信号
    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0) {
            browserRef?.reload()
        }
    }

    // 监听打开 DevTools 触发信号
    LaunchedEffect(devToolsTrigger) {
        if (devToolsTrigger > 0) {
            // openDevTools 需要传入 java.awt.Point(0,0) 或 null
            browserRef?.openDevTools(null)
        }
    }
    // 供 LoadHandler 闭包读取的最新调试开关（handler 在 factory 中创建一次）
    val currentDebugMode = remember { mutableStateOf(debugMode) }
    currentDebugMode.value = debugMode
    val scope = rememberCoroutineScope()

    /**
     * 构建 CEF 应用。调用前本地 natives 应已就绪且安装标记已补写
     * （见 [CefEnvironment.ensureInstalledMarkers]），jcefmaven 校验必过，
     * 不会触发删除目录或在线下载。
     */
    suspend fun buildApp(transition: CefPanelState) {
        state = transition
        try {
            withContext(Dispatchers.IO) { CefEnvironment.obtainApp() }
            state = CefPanelState.READY
        } catch (e: Exception) {
            failureMessage = e.message ?: e.javaClass.simpleName
            state = CefPanelState.FAILED
        }
    }

    /**
     * 使用通用下载器（8 连接分块 + 断点续传）在线下载 natives 并完成安装：
     * 下载完成 → 解压压缩包 → 补写安装标记 → 初始化。
     * 全程有进度/分块明细，绝不静默删除目录。
     */
    suspend fun downloadNativesWithProgress() {
        state = CefPanelState.DOWNLOADING
        cefDownload = null
        val fileName = CefEnvironment.NATIVES_URL_WINDOWS.substringAfterLast('/')
        val result = FileDownloader().download(
            url = CefEnvironment.NATIVES_URL_WINDOWS,
            destinationDir = CefEnvironment.installDirPath,
            fileName = fileName
        ) { p ->
            // 回调来自 IO 协程：转发到主线程（组合上下文）写状态，确保重组可靠触发
            scope.launch { cefDownload = p }
        }
        when (result) {
            is DownloadResult.Success -> {
                CefEnvironment.extractPlacedArchives()
                CefEnvironment.ensureInstalledMarkers()
                cefDownload = null
                buildApp(CefPanelState.CHECKING)
            }
            is DownloadResult.Failed -> {
                cefDownload = null
                failureMessage = result.message
                state = CefPanelState.FAILED
            }
        }
    }

    /**
     * 向浏览器注入调试桥脚本（含 debugtool 脚本地址预置）。
     */
    fun injectDebugBridge(browser: CefBrowser, pagePath: String) {
        val parent = File(pagePath).parentFile ?: return
        // 在页面同级目录寻找 debugtool（优先非压缩版）
        val toolFile = listOf("laya.debugtool.js", "laya.debugtool.min.js")
            .map { File(parent, it) }
            .firstOrNull { it.isFile }
        val toolUrl = toolFile?.toURI()?.toString() ?: ""
        val script = "window.__gufDebugToolUrl = \"${toolUrl}\";$DEBUG_BRIDGE_JS"
        browser.executeJavaScript(script, pagePath, 0)
    }

    // 进入面板时：优先检测本地预置资源；未就绪则先尝试解压手动放入的压缩包。
    // 就绪后必须补写安装标记（install.lock/build_meta.json）：否则 jcefmaven 校验不通过时会
    // 静默删除整个安装目录并重新在线下载（用户手动预置的文件会被误删）。
    LaunchedEffect(Unit) {
        val ready = if (CefEnvironment.isExtracted()) {
            true
        } else {
            CefEnvironment.extractPlacedArchives()
            CefEnvironment.isExtracted()
        }
        if (ready) {
            CefEnvironment.ensureInstalledMarkers()
            buildApp(CefPanelState.CHECKING)
        } else {
            state = CefPanelState.NEED_MANUAL
        }
    }

    // 页面加载完成且调试模式开启时注入（覆盖"先开调试后加载页面"场景）
    // 调试模式中途开启时也补注入（覆盖"先加载页面后开调试"场景）
    LaunchedEffect(debugMode, lastLoadedUrl) {
        val browser = browserRef ?: return@LaunchedEffect
        val path = htmlPath ?: return@LaunchedEffect
        if (debugMode && lastLoadedUrl != null) {
            injectDebugBridge(browser, path)
        } else if (!debugMode) {
            browser.executeJavaScript("window.__gufBridge && window.__gufBridge.stop();", path, 0)
        }
    }

    // 选中调试节点变化：执行一次属性查询（后续由桥内 1s 定时刷新维持动态更新）
    LaunchedEffect(inspectTarget) {
        val browser = browserRef ?: return@LaunchedEffect
        val id = inspectTarget ?: return@LaunchedEffect
        if (debugMode) {
            val escaped = id.replace("\\", "\\\\").replace("'", "\\'")
            browser.executeJavaScript("window.__gufBridge && window.__gufBridge.queryProps('$escaped');", htmlPath, 0)
        }
    }

    // 面板销毁时释放浏览器资源与本地服务器
    DisposableEffect(Unit) {
        onDispose {
            runCatching { browserRef?.close(true) }
            runCatching { clientRef?.dispose() }
            runCatching { localServer?.stop(0) }
        }
    }

    when {
        // 环境就绪但未选择页面
        state == CefPanelState.READY && htmlPath == null -> {
            EmptyHint(text = stringResource(Res.string.gp_preview_none), modifier = modifier)
        }

        // 环境就绪：创建浏览器并加载页面
        state == CefPanelState.READY -> {
            val platformDensity = org.gemini.ui.forge.ui.theme.LocalPlatformDensity.current
            CompositionLocalProvider(LocalDensity provides platformDensity) {
                SwingPanel(
                    factory = {
                        val client = CefEnvironment.obtainApp().createClient()
                        val browser = client.createBrowser("about:blank", false, false)
                        // 注册 cefQuery 消息路由：JS -> Kotlin 调试桥
                        val router = CefMessageRouter.create()
                        router.addHandler(object : CefMessageRouterHandlerAdapter() {
                            override fun onQuery(
                                browser: CefBrowser,
                                frame: CefFrame,
                                requestId: Long,
                                request: String,
                                persistent: Boolean,
                                callback: CefQueryCallback
                            ): Boolean {
                                // 回传 JSON 原文，由 ViewModel 统一解析分发
                                onDebugMessage(request)
                                callback.success("")
                                return true
                            }
                        }, true)
                        client.addMessageRouter(router)

                        // 拦截 Chrome 浏览器控制台原生日志事件 (console.log / warn / error / asset load fail 等)
                        client.addDisplayHandler(object : org.cef.handler.CefDisplayHandlerAdapter() {
                            override fun onConsoleMessage(
                                browser: org.cef.browser.CefBrowser,
                                level: org.cef.CefSettings.LogSeverity,
                                message: String,
                                source: String,
                                line: Int
                            ): Boolean {
                                val tag = when (level) {
                                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_VERBOSE -> "[VERBOSE]"
                                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_INFO -> "[INFO]"
                                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING -> "[WARN]"
                                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_ERROR -> "[ERROR]"
                                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_FATAL -> "[FATAL]"
                                    else -> "[INFO]"
                                }
                                val escapedMsg = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
                                val escapedSrc = source.substringAfterLast('/').replace("\\", "\\\\").replace("\"", "\\\"")
                                val formatted = "$tag ($escapedSrc:$line) $escapedMsg"
                                onDebugMessage("{\"type\":\"log\",\"text\":\"$formatted\"}")
                                return false
                            }
                        })

                        // 页面主文档加载结束时按需注入调试桥
                        client.addLoadHandler(object : CefLoadHandlerAdapter() {
                            override fun onLoadEnd(
                                browser: CefBrowser,
                                frame: CefFrame,
                                httpStatusCode: Int
                            ) {
                                if (frame.isMain) {
                                    lastLoadedUrl = browser.url
                                    val path = htmlPath
                                    if (currentDebugMode.value && path != null) {
                                        injectDebugBridge(browser, path)
                                    }
                                }
                            }
                        })

                        // 注册 LifeSpanHandler 拦截关闭事件，防止 JS window.close() 关闭整个桌面应用程序
                        client.addLifeSpanHandler(object : org.cef.handler.CefLifeSpanHandlerAdapter() {
                            override fun doClose(browser: CefBrowser): Boolean {
                                AppLogger.i("GameHtmlPreview", "🛑 拦截到网页 JS window.close() 调用，已安全阻止并通知 UI 清理页面")
                                onDebugMessage("{\"type\":\"close\"}")
                                return true
                            }
                        })

                        clientRef = client
                        browserRef = browser
                        val wrapper = JPanel(BorderLayout())
                        wrapper.add(browser.uiComponent, BorderLayout.CENTER)
                        wrapper
                    },
                    update = {
                        val url = serverUrl
                        if (url != null && url != lastLoadedUrl) {
                            lastLoadedUrl = url
                            browserRef?.loadURL(url)
                        }
                    },
                    modifier = modifier.fillMaxSize()
                )
            }
        }

        // 引导手动预置 / 在线下载
        state == CefPanelState.NEED_MANUAL -> {
            ManualGuideCard(
                onOpenDownloadPage = { getPlatform().openInBrowser(CefEnvironment.DOWNLOAD_PAGE_URL) },
                onOpenDir = {
                    CefEnvironment.ensureInstallDir()
                    getPlatform().openInFileExplorer(CefEnvironment.installDirPath)
                },
                onAutoDownload = { scope.launch { downloadNativesWithProgress() } },
                modifier = modifier
            )
        }

        // 失败提示
        state == CefPanelState.FAILED -> {
            EmptyHint(
                text = stringResource(Res.string.gp_cef_failed, failureMessage ?: ""),
                modifier = modifier
            )
        }

        // 在线下载中：展示下载地址（可选复制）与实时进度条
        state == CefPanelState.DOWNLOADING -> {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(Res.string.gp_cef_downloading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = CefEnvironment.NATIVES_URL_WINDOWS,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(CefEnvironment.NATIVES_URL_WINDOWS.toClipEntry())
                                Toast.show(copiedTip)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 总进度与统计由 FileDownloader 快照驱动（8 连接分块 + 断点续传）
                val snap = cefDownload
                val totalFrac = if (snap != null && snap.totalBytes > 0) {
                    snap.downloadedBytes.toFloat() / snap.totalBytes
                } else 0f
                LinearProgressIndicator(
                    progress = { totalFrac },
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
                Spacer(Modifier.height(8.dp))
                snap?.let { s ->
                    // 统计行：已下载 / 总大小 · 速度 · 预计剩余时间（总大小未知时隐藏）
                    if (s.totalBytes > 0L) {
                        Text(
                            text = stringResource(
                                Res.string.gp_cef_dl_stats,
                                formatBytes(s.downloadedBytes),
                                formatBytes(s.totalBytes),
                                formatBytes(s.speedBps),
                                formatEta(
                                    if (s.speedBps > 0) (s.totalBytes - s.downloadedBytes).toDouble() / s.speedBps else -1.0
                                )
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // 分块网格：每块一条迷你进度条（蓝=下载中 / 绿=完成 / 淡蓝=续传跳过 / 灰=排队），
                    // 蓝色条的数量即当前活跃连接数，一目了然
                    s.chunks.forEach { chunk ->
                        val frac = if (chunk.totalBytes > 0) {
                            chunk.downloadedBytes.toFloat() / chunk.totalBytes
                        } else 0f
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth(0.7f).height(4.dp),
                            color = when (chunk.status) {
                                ChunkStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                ChunkStatus.DONE -> Color(0xFF4CAF50)
                                ChunkStatus.SKIPPED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                ChunkStatus.PENDING -> MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }

        // 检测中
        else -> {
            EmptyHint(
                text = stringResource(Res.string.gp_cef_preparing),
                showProgress = true,
                modifier = modifier
            )
        }
    }
}

/**
 * 手动准备 Chromium 资源的引导卡片（私有组件）。
 */
@Composable
private fun ManualGuideCard(
    onOpenDownloadPage: () -> Unit,
    onOpenDir: () -> Unit,
    onAutoDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 剪贴板与复制成功提示（预先在组合上下文解析）
    val clipboard = LocalClipboard.current
    val copiedTip = stringResource(Res.string.gp_copy_success)
    val scope = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            // 宽度自适应上下限：内容自然高度，不做比例拉伸（保持原有紧凑布局）
            Column(modifier = Modifier.padding(20.dp).widthIn(min = 480.dp, max = 720.dp)) {
                // 包裹可选文本容器：标题、安装目录路径与下载地址均支持鼠标拖选复制
                SelectionContainer {
                    Column {
                        Text(
                            text = stringResource(Res.string.gp_cef_need_manual_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.gp_cef_need_manual_desc, CefEnvironment.installDirPath),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))
                        // 下载地址行：等宽字体展示 + 行尾一键复制按钮
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = CefEnvironment.NATIVES_URL_WINDOWS,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(CefEnvironment.NATIVES_URL_WINDOWS.toClipEntry())
                                        Toast.show(copiedTip)
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onOpenDownloadPage) {
                        Text(stringResource(Res.string.gp_cef_download_page))
                    }
                    OutlinedButton(onClick = onOpenDir) {
                        Text(stringResource(Res.string.gp_cef_open_dir))
                    }
                    OutlinedButton(onClick = onAutoDownload) {
                        Text(stringResource(Res.string.gp_cef_auto_download))
                    }
                }
            }
        }
    }
}

/**
 * 空态/加载态提示（私有组件）。
 */
@Composable
private fun EmptyHint(text: String, showProgress: Boolean = false, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 字节数人性化格式（B/KB/MB/GB，语言无关单位），供下载统计行展示。
 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * 剩余秒数格式化为语言无关时间串（如 "45s" / "3m 12s" / "1h 5m"）；负值返回 "--"。
 */
private fun formatEta(seconds: Double): String {
    if (seconds < 0) return "--"
    val total = seconds.toLong()
    return when {
        total < 60 -> "${total}s"
        total < 3600 -> "${total / 60}m ${total % 60}s"
        else -> "${total / 3600}h ${(total % 3600) / 60}m"
    }
}
