package org.gemini.ui.forge.service.game

import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import java.io.File

/**
 * 负责将 UI Studio 活跃视图（Page）与图元树（UIStudioBlock）导出为游戏工程中的
 * 纯代码 UIFactory TypeScript 布局配置，并同步复制引用的切片资产。
 */
object GameUiExportService {

    /**
     * 将当前页面图元树与切片资产完整导出到游戏工程目录，并执行 node game/build.js 重新打包。
     */
    suspend fun exportPageToGameProject(
        gameRootDir: File,
        page: UIStudioPage,
        assetLibrary: UIAssetLibrary?,
        onLog: (String) -> Unit = {}
    ): Result<Unit> = runCatching {
        if (!gameRootDir.exists() || !gameRootDir.isDirectory) {
            error("游戏项目根目录不存在: ${gameRootDir.absolutePath}")
        }

        onLog("🚀 开始将 UI Studio 页面 [${page.id}] 导出至游戏工程...")

        // 1. 确保切片输出目录 build/res/gameui/ 存在
        val resGameUiDir = File(gameRootDir, "build/res/gameui").apply { mkdirs() }
        val userHome = System.getProperty("user.home") ?: ""
        val appDataDir = File(userHome, ".geminiuiforge")

        // 建立 Hash 到 UIAssetItem 的索引映射
        val assetHashMap = mutableMapOf<String, UIAssetItem>()
        assetLibrary?.let { collectAssetItems(it, assetHashMap) }

        // 2. 扫描图元并物理同步切片资源
        val copiedFiles = mutableSetOf<String>()
        val blockAssetNames = mutableMapOf<String, String>() // block.id -> fileName

        fun scanBlockAssets(block: UIStudioBlock) {
            val hash = block.assetHash
            if (!hash.isNullOrBlank()) {
                val assetItem = assetHashMap[hash]
                if (assetItem != null) {
                    val relativePath = assetItem.relativePath
                    val sourceFile = File(appDataDir, relativePath)
                    val fileName = File(relativePath).name
                    blockAssetNames[block.id] = fileName

                    val targetFile = File(resGameUiDir, fileName)
                    if (sourceFile.exists() && (!targetFile.exists() || targetFile.length() != sourceFile.length())) {
                        sourceFile.copyTo(targetFile, overwrite = true)
                        copiedFiles.add(fileName)
                        onLog("📦 同步切片: $fileName (${assetItem.width}x${assetItem.height})")
                    }
                }
            }
            for (child in block.children) {
                scanBlockAssets(child)
            }
        }

        for (block in page.blocks) {
            scanBlockAssets(block)
        }

        if (copiedFiles.isNotEmpty()) {
            onLog("✅ 本次共同步 ${copiedFiles.size} 个切片资源至 build/res/gameui/")
        } else {
            onLog("ℹ️ 切片资源无需更新或已与磁盘保持一致")
        }

        // 3. 将 UIStudioBlock 树生成符合纯代码 UIFactory 规范的 TypeScript 配置
        val canvasWidth = page.width
        val canvasHeight = page.height
        val elementsTsCode = generateElementsTsCode(page.blocks, blockAssetNames, indent = "        ")

        val layoutConfigTs = buildString {
            appendLine("/**")
            appendLine(" * 由 GeminiUIForge UI Studio 全自动导出的纯代码 UIFactory 页面布局配置。")
            appendLine(" * 页面名称: ${page.id}")
            appendLine(" * 设计基准分辨率: ${canvasWidth}x${canvasHeight}")
            appendLine(" * 遵循规范: tsCore.UIFactory")
            appendLine(" */")
            appendLine("export const layoutConfig: any = {")
            appendLine("    type: 'panel',")
            appendLine("    name: '${page.id.ifBlank { "MainView" }}',")
            appendLine("    x: 0,")
            appendLine("    y: 0,")
            appendLine("    width: $canvasWidth,")
            appendLine("    height: $canvasHeight,")
            appendLine("    elements: [")
            append(elementsTsCode)
            appendLine("    ]")
            appendLine("};")
        }

        // 4. 写入 game/src/ui/layoutConfig.ts
        val layoutConfigFile = File(gameRootDir, "game/src/ui/layoutConfig.ts")
        layoutConfigFile.parentFile.mkdirs()
        layoutConfigFile.writeText(layoutConfigTs, Charsets.UTF_8)
        onLog("📝 生成纯代码 UIFactory 配置文件: game/src/ui/layoutConfig.ts")

        // 5. 调度执行 node game/build.js 编译打包至 build/main.min.js
        onLog("⚡ 开始执行 rollup 编译打包 (node game/build.js)...")
        val (packOk, packOutput) = GameEnvironmentManager.runProcess(
            workDir = gameRootDir,
            command = listOf("node", "game/build.js"),
            timeoutMinutes = 3,
            onLog = onLog
        )
        if (!packOk) {
            error("Rollup 编译打包失败: $packOutput")
        }

        val mainBundle = File(gameRootDir, "build/main.min.js")
        if (!mainBundle.exists() || mainBundle.length() == 0L) {
            error("打包产物 build/main.min.js 未生成或为空！")
        }

        onLog("🎉 UI Studio 页面与游戏代码编译闭环完成！产物大小: ${mainBundle.length() / 1024} KB")
    }

    private fun collectAssetItems(library: UIAssetLibrary, map: MutableMap<String, UIAssetItem>) {
        for (item in library.items) {
            map[item.hash] = item
        }
    }

    /**
     * 将图元列表转换为 UIFactory element 配置的 TypeScript 源码字符串
     */
    private fun generateElementsTsCode(
        blocks: List<UIStudioBlock>,
        assetNameMap: Map<String, String>,
        indent: String
    ): String = buildString {
        for ((idx, block) in blocks.withIndex()) {
            if (!block.isVisible) continue

            val assetFileName = assetNameMap[block.id]
            val x = block.bounds.left.toInt()
            val y = block.bounds.top.toInt()
            val w = block.bounds.width.toInt()
            val h = block.bounds.height.toInt()
            val alpha = block.opacity

            appendLine("$indent{")
            when (block.type) {
                UIBlockType.BUTTON -> {
                    // 按钮类型：构建为 fgui.GButton，默认注入点击变暗特效（downEffect: 1, downEffectValue: 0.8）
                    appendLine("$indent    type: 'button',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    appendLine("$indent    downEffect: 1,")
                    appendLine("$indent    downEffectValue: 0.8,")
                    appendLine("$indent    elements: [")

                    val buttonElements = mutableListOf<String>()
                    // 1. 若按钮自身绑定切片资产，作为内部背景/主体 icon 元件
                    if (!assetFileName.isNullOrBlank()) {
                        val iconCode = buildString {
                            appendLine("$indent        {")
                            appendLine("$indent            type: 'icon',")
                            appendLine("$indent            name: '${block.id}_icon',")
                            appendLine("$indent            url: '$assetFileName',")
                            appendLine("$indent            x: 0,")
                            appendLine("$indent            y: 0,")
                            appendLine("$indent            width: $w,")
                            appendLine("$indent            height: $h")
                            append("$indent        }")
                        }
                        buttonElements.add(iconCode)
                    }

                    // 2. 若按钮包含子图元，递归渲染
                    if (block.children.isNotEmpty()) {
                        val childrenCode = generateElementsTsCode(block.children, assetNameMap, "$indent        ").trimEnd()
                        if (childrenCode.isNotBlank()) {
                            buttonElements.add(childrenCode)
                        }
                    }

                    if (buttonElements.isNotEmpty()) {
                        appendLine(buttonElements.joinToString(",\n"))
                    }
                    appendLine("$indent    ]")
                }

                UIBlockType.VIEW -> {
                    // 纯视图容器层，构建为 panel 并递归嵌套子图元
                    appendLine("$indent    type: 'panel',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    appendLine("$indent    elements: [")
                    append(generateElementsTsCode(block.children, assetNameMap, "$indent        "))
                    appendLine("$indent    ]")
                }

                UIBlockType.TEXT -> {
                    // 纯文本图元
                    appendLine("$indent    type: 'text',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    text: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha")
                }

                else -> {
                    // 其他图元类型（IMAGE, BACKGROUND, ICON 等）
                    if (block.children.isNotEmpty()) {
                        // 带有子图元的复合图元，构建为 panel 容器
                        appendLine("$indent    type: 'panel',")
                        appendLine("$indent    name: '${block.id}',")
                        appendLine("$indent    x: $x,")
                        appendLine("$indent    y: $y,")
                        appendLine("$indent    width: $w,")
                        appendLine("$indent    height: $h,")
                        appendLine("$indent    alpha: $alpha,")
                        appendLine("$indent    elements: [")
                        if (!assetFileName.isNullOrBlank()) {
                            appendLine("$indent        {")
                            appendLine("$indent            type: 'icon',")
                            appendLine("$indent            name: '${block.id}_bg',")
                            appendLine("$indent            url: '$assetFileName',")
                            appendLine("$indent            x: 0,")
                            appendLine("$indent            y: 0,")
                            appendLine("$indent            width: $w,")
                            appendLine("$indent            height: $h")
                            appendLine("$indent        },")
                        }
                        append(generateElementsTsCode(block.children, assetNameMap, "$indent        "))
                        appendLine("$indent    ]")
                    } else if (!assetFileName.isNullOrBlank()) {
                        // 独立切片图元，构建为 icon
                        appendLine("$indent    type: 'icon',")
                        appendLine("$indent    name: '${block.id}',")
                        appendLine("$indent    url: '$assetFileName',")
                        appendLine("$indent    x: $x,")
                        appendLine("$indent    y: $y,")
                        appendLine("$indent    width: $w,")
                        appendLine("$indent    height: $h,")
                        appendLine("$indent    alpha: $alpha")
                    } else {
                        // 空白占位图元，构建为基础 panel
                        appendLine("$indent    type: 'panel',")
                        appendLine("$indent    name: '${block.id}',")
                        appendLine("$indent    x: $x,")
                        appendLine("$indent    y: $y,")
                        appendLine("$indent    width: $w,")
                        appendLine("$indent    height: $h,")
                        appendLine("$indent    alpha: $alpha")
                    }
                }
            }
            append("$indent}")
            if (idx < blocks.size - 1) append(",")
            appendLine()
        }
    }
}
