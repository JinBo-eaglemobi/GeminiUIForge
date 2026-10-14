package org.gemini.ui.forge.extend

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@OptIn(ExperimentalComposeUiApi::class)
actual fun String.toClipEntry(): ClipEntry {
    return ClipEntry(StringSelection(this))
}

actual suspend fun readClipboardImageBytes(): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            val image = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image
            if (image != null) {
                val bufferedImage = if (image is BufferedImage) {
                    image
                } else {
                    val width = image.getWidth(null).coerceAtLeast(1)
                    val height = image.getHeight(null).coerceAtLeast(1)
                    val bImg = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    val g2d = bImg.createGraphics()
                    g2d.drawImage(image, 0, 0, null)
                    g2d.dispose()
                    bImg
                }
                val out = ByteArrayOutputStream()
                ImageIO.write(bufferedImage, "png", out)
                return@withContext out.toByteArray()
            }
        }
    } catch (_: Throwable) {
    }
    null
}

actual suspend fun getSelectedTextOrNull(): String? = withContext(Dispatchers.IO) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val oldClip = try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                clipboard.getData(DataFlavor.stringFlavor) as? String
            } else null
        } catch (_: Throwable) { null }

        // 尝试使用 AWT Robot 触发 Ctrl+C 复制选区
        val robot = java.awt.Robot()
        val isMac = System.getProperty("os.name")?.contains("Mac", ignoreCase = true) == true
        val mask = if (isMac) java.awt.event.KeyEvent.VK_META else java.awt.event.KeyEvent.VK_CONTROL
        robot.keyPress(mask)
        robot.keyPress(java.awt.event.KeyEvent.VK_C)
        robot.keyRelease(java.awt.event.KeyEvent.VK_C)
        robot.keyRelease(mask)

        // 短暂让位剪贴板刷新
        kotlinx.coroutines.delay(40)

        val newClip = try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                clipboard.getData(DataFlavor.stringFlavor) as? String
            } else null
        } catch (_: Throwable) { null }

        if (!newClip.isNullOrBlank() && newClip != oldClip) {
            newClip
        } else null
    } catch (_: Throwable) {
        null
    }
}
