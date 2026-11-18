package org.gemini.ui.forge.service.game

import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.ImageResizeMode
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
                    val props = block.properties as? BlockProperties.ButtonProperties ?: BlockProperties.ButtonProperties()
                    appendLine("$indent    type: 'button',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    appendLine("$indent    downEffect: ${props.downEffect},")
                    appendLine("$indent    downEffectValue: ${props.downEffectValue},")
                    if (props.mode != "Common") {
                        appendLine("$indent    mode: '${props.mode}',")
                    }
                    if (props.relatedController.isNotBlank()) {
                        appendLine("$indent    relatedController: '${props.relatedController}',")
                    }
                    if (props.pageId.isNotBlank()) {
                        appendLine("$indent    pageId: '${props.pageId}',")
                    }
                    if (props.text.isNotBlank()) {
                        appendLine("$indent    text: '${props.text}',")
                    }
                    appendLine("$indent    elements: [")

                    val buttonElements = mutableListOf<String>()
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

                UIBlockType.LIST -> {
                    val props = block.properties as? BlockProperties.ListProperties ?: BlockProperties.ListProperties()
                    appendLine("$indent    type: 'list',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    appendLine("$indent    listLayout: '${props.listLayout}',")
                    appendLine("$indent    lineGap: ${props.safeLineGap},")
                    appendLine("$indent    columnGap: ${props.safeColumnGap},")
                    if (props.safeItemWidth > 0) appendLine("$indent    itemWidth: ${props.safeItemWidth},")
                    if (props.safeItemHeight > 0) appendLine("$indent    itemHeight: ${props.safeItemHeight},")
                    appendLine("$indent    align: '${props.align}',")
                    appendLine("$indent    verticalAlign: '${props.verticalAlign}',")
                    if (props.scrollType >= 0) {
                        appendLine("$indent    scrollType: ${props.scrollType},")
                        appendLine("$indent    overflow: ${props.overflow},")
                    }
                    if (props.safeLineCount > 0) appendLine("$indent    lineCount: ${props.safeLineCount},")
                    if (props.safeColumnCount > 0) appendLine("$indent    columnCount: ${props.safeColumnCount},")
                    if (props.autoResizeItem) appendLine("$indent    autoResizeItem: true,")
                    if (props.selectionMode != "None") appendLine("$indent    selectionMode: '${props.selectionMode}',")
                    if (props.defaultItem.isNotBlank()) {
                        // 【单页面渲染铁律】：若配置了 defaultItem，列表渲染唯一绑定该页面模板
                        appendLine("$indent    defaultItem: '${props.defaultItem}'")
                    } else {
                        appendLine("$indent    elements: [")
                        append(generateElementsTsCode(block.children, assetNameMap, "$indent        "))
                        appendLine("$indent    ]")
                    }
                }

                UIBlockType.PROGRESS_BAR -> {
                    val props = block.properties as? BlockProperties.ProgressBarProperties ?: BlockProperties.ProgressBarProperties()
                    appendLine("$indent    type: 'progressBar',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    appendLine("$indent    max: ${props.max},")
                    appendLine("$indent    value: ${props.value},")
                    appendLine("$indent    titleType: '${props.titleType}'")
                }

                UIBlockType.SLIDER -> {
                    val props = block.properties as? BlockProperties.SliderProperties ?: BlockProperties.SliderProperties()
                    appendLine("$indent    type: 'slider',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    appendLine("$indent    min: ${props.min},")
                    appendLine("$indent    max: ${props.max},")
                    appendLine("$indent    value: ${props.value},")
                    appendLine("$indent    step: ${props.step},")
                    appendLine("$indent    wholeNumbers: ${props.wholeNumbers}")
                }

                UIBlockType.COMBO_BOX, UIBlockType.POPUP_MENU -> {
                    val props = block.properties as? BlockProperties.ComboBoxProperties ?: BlockProperties.ComboBoxProperties()
                    appendLine("$indent    type: 'comboBox',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    val itemsCode = props.items.joinToString(", ", "[", "]") { "'$it'" }
                    appendLine("$indent    items: $itemsCode,")
                    appendLine("$indent    selectedIndex: ${props.selectedIndex},")
                    appendLine("$indent    visibleItemCount: ${props.visibleItemCount}")
                }

                UIBlockType.LOADER -> {
                    val props = block.properties as? BlockProperties.LoaderProperties ?: BlockProperties.LoaderProperties()
                    val url = props.url.ifBlank { assetFileName ?: "" }
                    appendLine("$indent    type: 'icon',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    url: '$url',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    appendLine("$indent    fill: '${props.fill}',")
                    if (props.clip) appendLine("$indent    clip: true,")
                    appendLine("$indent    align: '${props.align}',")
                    appendLine("$indent    valign: '${props.verticalAlign}'")
                }

                UIBlockType.SPINE -> {
                    val props = block.properties as? BlockProperties.SpineProperties ?: BlockProperties.SpineProperties()
                    appendLine("$indent    type: 'spine',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    appendLine("$indent    animationName: '${props.animationName}',")
                    appendLine("$indent    skinName: '${props.skinName}',")
                    appendLine("$indent    loop: ${props.loop},")
                    appendLine("$indent    timeScale: ${props.timeScale}")
                }

                UIBlockType.SCROLL_BAR -> {
                    val props = block.properties as? BlockProperties.ScrollBarProperties ?: BlockProperties.ScrollBarProperties()
                    appendLine("$indent    type: 'scrollBar',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    appendLine("$indent    fixedGripSize: ${props.fixedGripSize},")
                    appendLine("$indent    arrowButton: ${props.arrowButton}")
                }

                UIBlockType.COMPONENT -> {
                    val props = block.properties as? BlockProperties.ViewProperties ?: BlockProperties.ViewProperties()
                    appendLine("$indent    type: 'panel',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha,")
                    if (props.layoutType != "none") {
                        appendLine("$indent    layoutType: '${props.layoutType}',")
                        appendLine("$indent    gap: ${props.gap},")
                        if (props.flexWrap) appendLine("$indent    flexWrap: true,")
                        if (props.alignItems != "flex-start") appendLine("$indent    alignItems: '${props.alignItems}',")
                        if (props.justifyContent != "flex-start") appendLine("$indent    justifyContent: '${props.justifyContent}',")
                    }
                    if (props.scrollType >= 0) {
                        appendLine("$indent    scrollType: ${props.scrollType},")
                        appendLine("$indent    overflow: ${props.overflow},")
                    }
                    if (props.backgroundColor.isNotBlank()) {
                        appendLine("$indent    background: { type: 'graph', fillColor: '${props.backgroundColor}' },")
                    }
                    appendLine("$indent    elements: [")
                    append(generateElementsTsCode(block.children, assetNameMap, "$indent        "))
                    appendLine("$indent    ]")
                }

                UIBlockType.TEXT -> {
                    val props = block.properties as? BlockProperties.TextProperties ?: BlockProperties.TextProperties()
                    val textVal = props.text.ifBlank { block.id }
                    val hAlign = props.horizontalAlign.lowercase()
                    val vAlign = if (props.verticalAlign.equals("CENTER", true)) "middle" else props.verticalAlign.lowercase()
                    appendLine("$indent    type: 'text',")
                    appendLine("$indent    name: '${block.id}',")
                    appendLine("$indent    text: '$textVal',")
                    appendLine("$indent    fontSize: ${props.textSize},")
                    appendLine("$indent    color: '${props.textColor}',")
                    appendLine("$indent    align: '$hAlign',")
                    appendLine("$indent    valign: '$vAlign',")
                    if (props.isUbb) appendLine("$indent    isUbb: true,")
                    if (props.isBold) appendLine("$indent    bold: true,")
                    if (props.isItalic) appendLine("$indent    italic: true,")
                    if (props.underline) appendLine("$indent    underline: true,")
                    if (props.singleLine) appendLine("$indent    singleLine: true,")
                    if (props.strokeWidth > 0 && props.strokeColor.isNotBlank()) {
                        appendLine("$indent    stroke: ${props.strokeWidth},")
                        appendLine("$indent    strokeColor: '${props.strokeColor}',")
                    }
                    if (props.leading != 4) appendLine("$indent    leading: ${props.leading},")
                    if (props.letterSpacing != 0) appendLine("$indent    letterSpacing: ${props.letterSpacing},")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha")
                }

                UIBlockType.INPUT -> {
                    val props = block.properties as? BlockProperties.InputProperties ?: BlockProperties.InputProperties()
                    appendLine("$indent    type: 'text',")
                    appendLine("$indent    isInput: true,")
                    appendLine("$indent    name: '${block.id}',")
                    if (props.hintText.isNotBlank()) appendLine("$indent    promptText: '${props.hintText}',")
                    appendLine("$indent    fontSize: ${props.textSize},")
                    appendLine("$indent    color: '${props.textColor}',")
                    if (props.maxLength > 0) appendLine("$indent    maxLength: ${props.maxLength},")
                    if (props.isPassword) appendLine("$indent    isPassword: true,")
                    appendLine("$indent    singleLine: ${props.singleLine},")
                    appendLine("$indent    x: $x,")
                    appendLine("$indent    y: $y,")
                    appendLine("$indent    width: $w,")
                    appendLine("$indent    height: $h,")
                    appendLine("$indent    alpha: $alpha")
                }

                else -> {
                    // 其他图元类型（IMAGE, ICON 等）
                    val imgProps = block.properties as? BlockProperties.ImageProperties
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
                        if (imgProps != null && imgProps.resizeMode == ImageResizeMode.NINE_PATCH) {
                            val np = imgProps.ninePatchConfig
                            appendLine("$indent    sizeGrid: '${np.left},${np.top},${np.right},${np.bottom}',")
                        }
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
