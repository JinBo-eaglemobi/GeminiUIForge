package org.gemini.ui.forge

import androidx.compose.ui.unit.IntRect
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Window
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * 应用主窗口持有器 (JVM 桌面实现)
 *
 * 统一采用【屏幕绝对坐标系】：Compose Desktop 的 Dialog 是独立 heavyweight 窗口，
 * Compose 原生语义树 (ComposeAccessibilityGateway) 上报屏幕绝对 bounds，本持有器跨主窗与任意层级弹窗通吃。
 *
 * 1. captureWindowBytes: 遍历主窗 + 全部 owned 弹窗，定位与目标区域相交的 Compose 渲染层
 *    (org.jetbrains.skiko.SkiaLayer) 调用其官方 screenshot() API 离屏软件重绘 ——
 *    Swing printAll 对 skiko OpenGL 自绘层无效 (纯色空白)，而 SkiaLayer.screenshot()
 *    直接快照 Skia surface，最小化 / 被遮挡 / 后台运行均可获得完整应用画面；
 * 2. tapAt: 在命中屏幕坐标所属的应用窗口内，合成 AWT MouseEvent 序列异步投递到
 *    命中坐标的最深组件，经 JB Compose 层转换为真实 Compose 指针事件 (等效人工点击)。
 */
actual object AppWindowHolder {

    // 持窗口引用与主进程同生命周期，窗口为 null 时自动失效
    @Volatile
    private var windowRef: JFrame? = null

    actual fun holdWindow(ref: Any?) {
        windowRef = ref as? JFrame
    }

    /** 收集主窗与全部可见弹窗 (递归 ownedWindows，按窗口层级倒序=最上层优先) */
    private fun collectVisibleWindows(): List<Window> {
        val frame = windowRef ?: return emptyList()
        val all = mutableListOf<Window>()
        fun walk(w: Window) {
            if (w.isShowing) all.add(w)
            w.ownedWindows.forEach { walk(it) }
        }
        walk(frame)
        // ownedWindows 越靠后层级越高 (弹出顺序)，倒序保证最上层窗口优先命中
        return all
    }

    /** 递归遍历组件树，定位 Compose 渲染层 (SkiaLayer) */
    private fun findSkiaLayer(component: Component): SkiaLayer? {
        if (component is SkiaLayer) return component
        if (component is java.awt.Container) {
            for (child in component.components) {
                findSkiaLayer(child)?.let { return it }
            }
        }
        return null
    }

    /**
     * 坐标系契约 (实测标定)：
     * 语义节点 positionOnScreen、SkiaLayer.screenshot() Bitmap、AWT locationOnScreen
     * 三者均为【真实物理屏幕像素】且 1:1 相等 (Compose Desktop JVM 进程为 DPI aware，
     * 高分屏缩放已由 Skia 内部消化)。因此坐标全程直接使用、无需任何换算。
     */

    /**
     * 区域截图语义：
     * - region == null → 主窗整窗画面；
     * - region != null (屏幕绝对坐标) → 遍历应用全部窗口，在命中的 Skia 渲染层上裁剪交集区域。
     */
    actual fun captureWindowBytes(region: IntRect?): ByteArray? {
        val frame = windowRef ?: return null
        return try {
            if (region == null) {
                // 主窗整窗：直接快照主窗 Compose 渲染层
                val layer = findSkiaLayer(frame.contentPane) ?: return null
                return encodeLayerSnapshot(layer, null)
            }

            // 区域模式：遍历主窗 + 弹窗，定位屏幕区域命中的 Compose 渲染层
            // 全程物理像素 1:1，region 直接与渲染层屏幕矩形求交
            for (window in collectVisibleWindows().asReversed()) {
                val layer = findSkiaLayer(window) ?: continue
                val layerBounds = layer.bounds
                val layerLocation = layer.locationOnScreen.takeIf { layer.isShowing } ?: continue
                // 渲染层屏幕矩形 (物理像素)
                val lLeft = layerLocation.x
                val lTop = layerLocation.y
                val lRight = lLeft + layerBounds.width
                val lBottom = lTop + layerBounds.height
                // 求交集 (物理像素，1:1 即 Bitmap 像素)
                val cLeft = maxOf(region.left, lLeft)
                val cTop = maxOf(region.top, lTop)
                val cRight = minOf(region.right, lRight)
                val cBottom = minOf(region.bottom, lBottom)
                if (cRight - cLeft <= 0 || cBottom - cTop <= 0) continue
                // 交集换算为渲染层本地坐标 (物理 = Bitmap 像素，1:1)
                val localBitmap = IntRect(cLeft - lLeft, cTop - lTop, cRight - lLeft, cBottom - lTop)
                return encodeLayerSnapshot(layer, localBitmap)
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    /** Skia 渲染层快照 → (可选本地区域裁剪) → PNG 字节 */
    private fun encodeLayerSnapshot(layer: SkiaLayer, localRegion: IntRect?): ByteArray? {
        // Skia surface 快照 (软件重绘，不依赖窗口可见性)
        val bitmap: Bitmap = layer.screenshot() ?: return null
        val pngBytes = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: return null
        if (localRegion == null) return pngBytes

        val fullImage: BufferedImage = ImageIO.read(pngBytes.inputStream())
        val width = fullImage.width
        val height = fullImage.height
        val left = localRegion.left.coerceIn(0, width)
        val top = localRegion.top.coerceIn(0, height)
        val right = localRegion.right.coerceIn(0, width)
        val bottom = localRegion.bottom.coerceIn(0, height)
        if (right - left <= 0 || bottom - top <= 0) return null

        val subImage = fullImage.getSubimage(left, top, right - left, bottom - top)
        val baos = ByteArrayOutputStream()
        ImageIO.write(subImage, "png", baos)
        return baos.toByteArray()
    }

    /**
     * 在屏幕绝对坐标 (x, y) 注入真实点击。
     *
     * 采用 java.awt.Robot 系统级事件注入 (mouseMove + press + release)：
     * 事件沿 AWT → SkiaLayer → Compose 指针输入 → 命中测试的完整系统链路派发，
     * 与人工鼠标操作 100% 同链路，主窗与任意层级弹窗通吃；
     * 相比手动 dispatchEvent(MOUSE_PRESSED/RELEASED) 不依赖组件监听器细节，绝对可靠。
     * (副作用：会移动真实鼠标光标，符合"模拟真实人工点击"语义)
     */
    actual fun tapAt(x: Float, y: Float): Boolean {
        return try {
            // 入参为屏幕绝对坐标 (真实物理像素，与语义 positionOnScreen 1:1)，直接注入
            val px = x.toInt()
            val py = y.toInt()
            // 前置校验：目标坐标必须落在应用某个窗口内 (主窗/弹窗)，防止误点系统其他区域
            var hitWindow: Window? = null
            val inside = collectVisibleWindows().any { w ->
                try {
                    val loc = w.locationOnScreen
                    val ok = px >= loc.x && py >= loc.y && px < loc.x + w.width && py < loc.y + w.height
                    if (ok) hitWindow = w
                    ok
                } catch (_: Throwable) {
                    false
                }
            }
            if (!inside || hitWindow == null) {
                org.gemini.ui.forge.utils.AppLogger.w(
                    "AppWindowHolder", "tapAt 屏幕坐标 ($px,$py) 不在任何应用窗口内，已拒绝注入"
                )
                return false
            }

            // 关键：应用窗口可能被 IDE 等其他程序完全遮挡或处于最小化状态。
            // 1) 最小化自愈：恢复 NORMAL 状态 (Robot 无法点击最小化窗口的内容)；
            // 2) 置顶钉住：将目标窗口钉到最顶层并等待 z-order 真实生效，点击完成后再取消，
            //    保证 Robot 系统级点击落到应用自身界面而非遮挡窗口上。
            var pinned = false
            try {
                hitWindow?.let { w ->
                    if (w is java.awt.Frame &&
                        w.extendedState and java.awt.Frame.ICONIFIED != 0
                    ) {
                        w.extendedState = java.awt.Frame.NORMAL
                    }
                    w.isAlwaysOnTop = true
                    pinned = true
                    w.toFront()
                    w.requestFocus()
                    Thread.sleep(280)
                }
            } catch (_: Throwable) {
                // 置前失败不阻断注入 (窗口可能本就在前台)
            }

            val robot = java.awt.Robot()
            robot.autoDelay = 30
            robot.mouseMove(px, py)
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)

            // 点击完成后取消置顶，恢复窗口正常层级
            if (pinned) {
                try {
                    hitWindow?.isAlwaysOnTop = false
                } catch (_: Throwable) {
                    // 忽略取消置顶异常
                }
            }
            true
        } catch (t: Throwable) {
            org.gemini.ui.forge.utils.AppLogger.w(
                "AppWindowHolder", "tapAt Robot 注入失败: (${x.toInt()},${y.toInt()}) - ${t.message}"
            )
            false
        }
    }
}
