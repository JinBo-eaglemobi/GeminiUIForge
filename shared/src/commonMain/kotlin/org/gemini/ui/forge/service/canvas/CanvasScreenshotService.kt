package org.gemini.ui.forge.service.canvas

import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.AppWindowHolder
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIPage
import org.gemini.ui.forge.ui.component.chat.CommonChatAttachment
import org.gemini.ui.forge.utils.CompactImage
import org.gemini.ui.forge.utils.ImageCacheManager
import org.gemini.ui.forge.utils.compressToCompactImage
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.jetbrains.skia.*
import kotlin.math.roundToInt

/**
 * 画布快照模式
 */
enum class CanvasSnapshotMode(val labelZh: String) {
    OFFSCREEN_COMPOSITE("纯净整页合成"),
    VIEWPORT_CLIP("当前视口截图")
}

/**
 * 画布快照核心服务：负责将 UI Studio 画布渲染为多模态附件图片
 */
class CanvasScreenshotService {

    /**
     * 捕获画布快照并生成标准 CommonChatAttachment 附件对象
     */
    suspend fun captureSnapshot(
        projectName: String,
        currentPage: UIPage?,
        windowBounds: SerialRect?,
        mode: CanvasSnapshotMode = CanvasSnapshotMode.OFFSCREEN_COMPOSITE
    ): CommonChatAttachment? = withContext(Dispatchers.Default) {
        if (currentPage == null) return@withContext null

        val compactImage: CompactImage = when (mode) {
            CanvasSnapshotMode.OFFSCREEN_COMPOSITE -> {
                renderOffscreenComposite(currentPage) ?: return@withContext null
            }
            CanvasSnapshotMode.VIEWPORT_CLIP -> {
                captureViewportPhysicalClip(windowBounds) ?: return@withContext null
            }
        }

        val filePrefix = "canvas_snap_${currentPage.id}_${mode.name.lowercase()}"
        val cachedPath = ImageCacheManager.saveCache(
            prefix = filePrefix,
            image = compactImage,
            projectName = projectName
        )

        val w = currentPage.width.toInt()
        val h = currentPage.height.toInt()
        val displayName = "🎨 画布快照: ${currentPage.nameStr} (${w}×${h})"

        CommonChatAttachment(
            name = displayName,
            mimeType = compactImage.mimeType,
            bytes = compactImage.bytes,
            localUri = cachedPath,
            preferCloudUpload = true
        )
    }

    /**
     * 模式 A：纯本地 Skia 离屏图元 1:1 拼装渲染（不受缩放和视口裁剪影响）
     */
    private suspend fun renderOffscreenComposite(page: UIPage): CompactImage? {
        val width = page.width.toInt().coerceAtLeast(100)
        val height = page.height.toInt().coerceAtLeast(100)

        val surface = Surface.makeRasterN32Premul(width, height)
        val canvas = surface.canvas

        // 1. 绘制深色背景底色
        val bgPaint = Paint().apply { color = 0xFF0F172A.toInt() }
        canvas.drawRect(Rect.makeWH(width.toFloat(), height.toFloat()), bgPaint)

        // 2. 收集所有图元
        val allBlocks = mutableListOf<UIBlock>()
        fun collect(blocks: List<UIBlock>) {
            for (b in blocks) {
                if (b.isVisible) {
                    allBlocks.add(b)
                    collect(b.children)
                }
            }
        }
        collect(page.blocks)

        // 3. 按绝对坐标渲染图元贴图
        val imageCache = mutableMapOf<String, Image>()
        val placeholderPaint = Paint().apply {
            color = 0x22FFFFFF
            mode = PaintMode.STROKE
            strokeWidth = 2f
        }

        for (block in allBlocks) {
            val bounds = block.absoluteBounds
            val imgPath = block.currentImageUri?.getAbsolutePath()

            if (!imgPath.isNullOrBlank()) {
                val skiaImg = imageCache[imgPath] ?: run {
                    try {
                        val bytes = readLocalFileBytes(imgPath)
                        if (bytes != null) {
                            val img = Image.makeFromEncoded(bytes)
                            imageCache[imgPath] = img
                            img
                        } else null
                    } catch (_: Throwable) { null }
                }

                if (skiaImg != null) {
                    val dst = Rect.makeXYWH(bounds.left, bounds.top, bounds.width, bounds.height)
                    val paint = Paint().apply { isAntiAlias = true }
                    canvas.drawImageRect(skiaImg, dst, paint)
                    continue
                }
            }

            // 占位线框
            if (!block.isPureContainer) {
                val dst = Rect.makeXYWH(bounds.left, bounds.top, bounds.width, bounds.height)
                canvas.drawRect(dst, placeholderPaint)
            }
        }

        val snapshot = surface.makeImageSnapshot()
        return compressToCompactImage(snapshot, quality = 85)
    }

    /**
     * 模式 B：通过 JVM SkiaLayer 截取视口真实屏幕画面
     */
    private suspend fun captureViewportPhysicalClip(windowBounds: SerialRect?): CompactImage? {
        val region = if (windowBounds != null) {
            IntRect(
                left = windowBounds.left.roundToInt(),
                top = windowBounds.top.roundToInt(),
                right = windowBounds.right.roundToInt(),
                bottom = windowBounds.bottom.roundToInt()
            )
        } else null

        val bytes = AppWindowHolder.captureWindowBytes(region) ?: return null
        return compressToCompactImage(bytes, quality = 85)
    }
}
