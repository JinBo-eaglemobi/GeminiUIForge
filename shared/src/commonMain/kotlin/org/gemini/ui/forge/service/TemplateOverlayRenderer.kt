package org.gemini.ui.forge.service

import androidx.compose.ui.graphics.ImageBitmap
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.fetchImageBytes
import org.gemini.ui.forge.utils.toComposeImageBitmap
import org.jetbrains.skia.*

/**
 * 纯本地 Skia 离屏模板图元标注图渲染器
 *
 * 核心特性：
 * 1. 100% 纯本地离屏渲染，毫秒级完成，零 AI Token 消耗；
 * 2. 以原设计参考图为底图，递归计算各模块全局绝对坐标 (absoluteBounds)；
 * 3. 区分纯容器/占位层（鲜橙色框线与标签）与业务图元（亮青色框线与标签）；
 * 4. 双时态落盘支持：
 *    - 初次生成全景快照: overlay_initial.png
 *    - 随时反映当前最新排版: overlay_latest.png
 * 5. 支持纯内存实时渲染 ([renderOverlayMemory])，供界面直接查看，零磁盘 I/O。
 * 6. 自动注册至 ImageCacheManager，支持界面与外部工具快速调用。
 */
object TemplateOverlayRenderer {

    private const val TAG = "TemplateOverlayRenderer"
    private const val PROJECTS_DIR = "templates"

    data class RenderResult(
        val initialPath: String?,
        val latestPath: String?,
        val bytes: ByteArray,
        val totalBlocks: Int,
        val containerCount: Int,
        val businessCount: Int
    )

    data class RenderMemoryResult(
        val imageBitmap: ImageBitmap,
        val pngBytes: ByteArray,
        val totalBlocks: Int,
        val containerCount: Int,
        val businessCount: Int,
        val canvasWidth: Int,
        val canvasHeight: Int
    )

    /**
     * 预先解码参考底图为 Skia [Image] 并驻留内存，供实时预览高频重绘复用。
     */
    suspend fun loadBaseImage(projectState: ProjectState): Image? {
        val page = projectState.pages.firstOrNull() ?: return null
        val refLargePath = page.sourceImageUri?.getAbsolutePath()
            ?: projectState.referenceImages.firstOrNull()?.getAbsolutePath()
        if (refLargePath.isNullOrBlank()) return null
        return try {
            val bytes = fetchImageBytes(refLargePath) ?: return null
            Image.makeFromEncoded(bytes)
        } catch (e: Throwable) {
            AppLogger.w(TAG, "加载预解码参考底图异常: $refLargePath, 原因: ${e.message}")
            null
        }
    }

    /**
     * 纯内存离屏渲染模板图元标注图（零磁盘 I/O，不落盘）
     *
     * 与 [renderAndSave] 保持 100% 相同绘制算法与样式规范。
     * 支持 [baseImage] 预解码复用（若传入，彻底跳过磁盘 I/O 与图片解码，毫秒级出图）。
     * 支持 [hiddenBlockIds] 纯内存过滤，跳过指定图元的框线绘制。
     * 支持 [encodePng] 是否执行昂贵的 PNG 编码压缩（实时交互预览置 false，纯内存秒出 ImageBitmap；落盘置 true）。
     */
    suspend fun renderOverlayMemory(
        projectState: ProjectState,
        hiddenBlockIds: Set<String> = emptySet(),
        baseImage: Image? = null,
        encodePng: Boolean = false
    ): RenderMemoryResult? {
        val page = projectState.pages.firstOrNull() ?: run {
            AppLogger.w(TAG, "项目状态无有效页面，跳过标注图内存渲染")
            return null
        }

        val canvasW = page.width.toInt().coerceAtLeast(100)
        val canvasH = page.height.toInt().coerceAtLeast(100)

        // 1. 初始化 Skia 离屏画布
        val surface = Surface.makeRasterN32Premul(canvasW, canvasH)
        val canvas = surface.canvas

        // 2. 解析并绘制底图（优先复用已预解码的 baseImage，避免重复磁盘 I/O 与 CPU 解码）
        val resolvedImg: Image? = baseImage ?: run {
            val refLargePath = page.sourceImageUri?.getAbsolutePath()
                ?: projectState.referenceImages.firstOrNull()?.getAbsolutePath()
            if (!refLargePath.isNullOrBlank()) {
                try {
                    val refBytes = fetchImageBytes(refLargePath)
                    if (refBytes != null) Image.makeFromEncoded(refBytes) else null
                } catch (e: Throwable) {
                    AppLogger.w(TAG, "解码并绘制参考底图异常: ${e.message}")
                    null
                }
            } else null
        }

        if (resolvedImg != null) {
            try {
                val srcRect = Rect.makeWH(resolvedImg.width.toFloat(), resolvedImg.height.toFloat())
                val dstRect = Rect.makeWH(canvasW.toFloat(), canvasH.toFloat())
                val paint = Paint().apply { isAntiAlias = true }
                canvas.drawImageRect(resolvedImg, srcRect, dstRect, paint)
            } catch (e: Throwable) {
                AppLogger.w(TAG, "绘制参考底图失败: ${e.message}")
                val bgPaint = Paint().apply { color = 0xFF1E1E24.toInt() }
                canvas.drawRect(Rect.makeWH(canvasW.toFloat(), canvasH.toFloat()), bgPaint)
            }
        } else {
            val bgPaint = Paint().apply { color = 0xFF1E1E24.toInt() }
            canvas.drawRect(Rect.makeWH(canvasW.toFloat(), canvasH.toFloat()), bgPaint)
        }

        // 4. 接线父级引用以确保 absoluteBounds 运算准确
        val boundBlocks = page.blocks.bindParents()

        // 5. 递归收集图元，拆分为容器层与业务层
        val containerBlocks = mutableListOf<UIBlock>()
        val businessBlocks = mutableListOf<UIBlock>()

        fun collect(blocks: List<UIBlock>) {
            for (b in blocks) {
                if (b.type == UIBlockType.BACKGROUND && b.parent == null) {
                    // 全屏背景底图单独处理或跳过厚重遮罩
                } else if (b.isPureContainer || b.type == UIBlockType.CONTAINER) {
                    containerBlocks.add(b)
                } else {
                    businessBlocks.add(b)
                }
                collect(b.children)
            }
        }
        collect(boundBlocks)

        // 6. 配置画笔与字体 (纯线框 + 左上角极简文字，无任何实体色块遮挡)
        val typeface: Typeface? = try {
            val mgr = FontMgr.default
            val candidateNames = listOf("Segoe UI", "Arial", "Microsoft YaHei", "Tahoma", "SimHei", "sans-serif")
            var matched: Typeface? = null
            for (name in candidateNames) {
                matched = mgr.matchFamilyStyle(name, FontStyle.NORMAL)
                if (matched != null) break
            }
            if (matched == null && mgr.familiesCount > 0) {
                matched = mgr.matchFamilyStyle(mgr.getFamilyName(0), FontStyle.NORMAL)
            }
            matched
        } catch (_: Throwable) {
            null
        }
        val font = if (typeface != null) Font(typeface, 18f) else Font().apply { size = 18f }

        // 文字黑色阴影描边画笔（确保在任何明暗底图上都清晰可见）
        val textShadowPaint = Paint().apply {
            color = 0xDD000000.toInt()
            isAntiAlias = true
        }

        // 容器画笔（亮橙色纯线框）
        val containerStrokePaint = Paint().apply {
            color = 0xEEFF6400.toInt() // 鲜橙色
            mode = PaintMode.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val containerTextPaint = Paint().apply {
            color = 0xFFFF7A00.toInt()
            isAntiAlias = true
        }

        // 业务图元画笔（亮青色纯线框）
        val businessStrokePaint = Paint().apply {
            color = 0xFF00FFFF.toInt() // 亮青色 (Cyan)
            mode = PaintMode.STROKE
            strokeWidth = 3.5f
            isAntiAlias = true
        }
        val businessTextPaint = Paint().apply {
            color = 0xFF00FFFF.toInt()
            isAntiAlias = true
        }

        AppLogger.i(TAG, "开始绘制标注图: 容器 ${containerBlocks.size} 个, 业务图元 ${businessBlocks.size} 个")

        // 7. 先绘制容器（下层纯细线框，无任何半透明遮罩与药丸色块）
        for (block in containerBlocks) {
            if (block.id in hiddenBlockIds) continue
            val abs = block.absoluteBounds
            val rect = Rect.makeXYWH(abs.left, abs.top, abs.width, abs.height)
            canvas.drawRect(rect, containerStrokePaint)

            // 左上角紧凑无遮挡文字标识
            val label = block.id
            val tx = abs.left + 4f
            val ty = abs.top + 18f
            // 黑色阴影提高可读性
            canvas.drawString(label, tx + 1f, ty + 1f, font, textShadowPaint)
            canvas.drawString(label, tx, ty, font, containerTextPaint)
        }

        // 8. 后绘制业务图元（上层亮青色纯细线框）
        for (block in businessBlocks) {
            if (block.id in hiddenBlockIds) continue
            val abs = block.absoluteBounds
            val rect = Rect.makeXYWH(abs.left, abs.top, abs.width, abs.height)
            canvas.drawRect(rect, businessStrokePaint)

            // 左上角紧凑无遮挡文字标识
            val label = block.id
            val tx = abs.left + 4f
            val ty = abs.top + 18f
            // 黑色阴影提高可读性
            canvas.drawString(label, tx + 1f, ty + 1f, font, textShadowPaint)
            canvas.drawString(label, tx, ty, font, businessTextPaint)
        }

        // 9. 导出 ImageBitmap 与可选 PNG 字节（若不落盘则跳过昂贵的 PNG 压缩）
        val snapshot = surface.makeImageSnapshot()
        val imageBitmap = snapshot.toComposeImageBitmap()
        val pngBytes = if (encodePng) {
            val pngData = snapshot.encodeToData(EncodedImageFormat.PNG)
            pngData?.bytes ?: ByteArray(0)
        } else {
            ByteArray(0)
        }

        return RenderMemoryResult(
            imageBitmap = imageBitmap,
            pngBytes = pngBytes,
            totalBlocks = containerBlocks.size + businessBlocks.size,
            containerCount = containerBlocks.size,
            businessCount = businessBlocks.size,
            canvasWidth = canvasW,
            canvasHeight = canvasH
        )
    }

    /**
     * 渲染模板全景标注图并持久化落盘
     *
     * @param projectName 模板工程名称
     * @param projectState 模板当前状态
     * @param isInitial 是否为初始生成阶段（若 true，同时落盘 overlay_initial.png 与 overlay_latest.png）
     */
    suspend fun renderAndSave(
        projectName: String,
        projectState: ProjectState,
        isInitial: Boolean = false
    ): RenderResult? {
        val memResult = renderOverlayMemory(projectState, encodePng = true) ?: return null
        val pngBytes = memResult.pngBytes
        if (pngBytes.isEmpty()) {
            AppLogger.e(TAG, "Skia 离屏编码 PNG 字节失败")
            return null
        }

        // 10. 落盘至磁盘
        val sanitizedName = projectName.replace(" ", "_")
        var initialAbsPath: String? = null
        if (isInitial) {
            val initialFile = TemplateFile("$PROJECTS_DIR/$sanitizedName/overlay_initial.png")
            initialFile.writeBytes(pngBytes)
            initialAbsPath = initialFile.getAbsolutePath()
            AppLogger.i(TAG, "✅ [初次生成] 标注图已落盘: $initialAbsPath")
        }

        val latestFile = TemplateFile("$PROJECTS_DIR/$sanitizedName/overlay_latest.png")
        latestFile.writeBytes(pngBytes)
        val latestAbsPath = latestFile.getAbsolutePath()
        AppLogger.i(TAG, "✅ [动态最新] 标注图已落盘: $latestAbsPath")

        return RenderResult(
            initialPath = initialAbsPath,
            latestPath = latestAbsPath,
            bytes = pngBytes,
            totalBlocks = memResult.totalBlocks,
            containerCount = memResult.containerCount,
            businessCount = memResult.businessCount
        )
    }

    fun getLatestOverlayFile(projectName: String): TemplateFile {
        val sanitizedName = projectName.replace(" ", "_")
        return TemplateFile("$PROJECTS_DIR/$sanitizedName/overlay_latest.png")
    }

    fun getInitialOverlayFile(projectName: String): TemplateFile {
        val sanitizedName = projectName.replace(" ", "_")
        return TemplateFile("$PROJECTS_DIR/$sanitizedName/overlay_initial.png")
    }

    suspend fun renderAndSaveInitialAndLatest(projectName: String, projectState: ProjectState): RenderResult? {
        return renderAndSave(projectName, projectState, isInitial = true)
    }

    suspend fun renderAndSaveLatest(projectName: String, projectState: ProjectState): RenderResult? {
        return renderAndSave(projectName, projectState, isInitial = false)
    }
}
