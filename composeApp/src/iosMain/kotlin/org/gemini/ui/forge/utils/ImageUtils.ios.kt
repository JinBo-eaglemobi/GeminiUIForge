package org.gemini.ui.forge.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.gemini.ui.forge.model.ui.SerialRect
import platform.UIKit.*
import platform.CoreGraphics.*
import platform.Foundation.*
import kotlinx.cinterop.*

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return org.jetbrains.skia.Image.makeFromEncoded(this).toComposeImageBitmap()
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getImageSize(uri: String): Pair<Int, Int>? {
    val image = UIImage.imageWithContentsOfFile(uri) ?: return null
    return Pair(image.size.useContents { width.toInt() }, image.size.useContents { height.toInt() })
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun cropImage(
    imageSource: String,
    bounds: SerialRect,
    logicalWidth: Float,
    logicalHeight: Float,
    isPng: Boolean,
    forceWidth: Int?,
    forceHeight: Int?
): ByteArray? {
    val image = UIImage.imageWithContentsOfFile(imageSource) ?: return null
    
    val imageSize = image.size.useContents { this }
    val scaleX = imageSize.width / logicalWidth
    val scaleY = imageSize.height / logicalHeight
    
    val cropRect = CGRectMake(
        bounds.left.toDouble() * scaleX,
        bounds.top.toDouble() * scaleY,
        bounds.width.toDouble() * scaleX,
        bounds.height.toDouble() * scaleY
    )
    
    val imageRef = CGImageCreateWithImageInRect(image.CGImage, cropRect) ?: return null
    val croppedImage = UIImage.imageWithCGImage(imageRef)
    
    val targetSize = if (forceWidth != null && forceHeight != null) {
        CGSizeMake(forceWidth.toDouble(), forceHeight.toDouble())
    } else {
        CGSizeMake(bounds.width.toDouble(), bounds.height.toDouble())
    }
    
    UIGraphicsBeginImageContextWithOptions(targetSize, false, 1.0)
    croppedImage.drawInRect(CGRectMake(0.0, 0.0, targetSize.useContents { width }, targetSize.useContents { height }))
    val finalImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    
    if (finalImage == null) return null
    
    val data = if (isPng) UIImagePNGRepresentation(finalImage) else UIImageJPEGRepresentation(finalImage, 0.8)
    if (data == null) return null
    
    val bytes = ByteArray(data.length.toInt())
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getNonTransparentBounds(imageSource: String): SerialRect? {
    val image = UIImage.imageWithContentsOfFile(imageSource) ?: return null
    val cgImage = image.CGImage ?: return null
    
    val width = CGImageGetWidth(cgImage).toInt()
    val height = CGImageGetHeight(cgImage).toInt()
    
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bytesPerPixel = 4
    val bytesPerRow = bytesPerPixel * width
    val bitsPerComponent = 8
    val rawData = nativeHeap.allocArray<ByteVar>(height * bytesPerRow)
    
    val context = CGBitmapContextCreate(
        rawData,
        width.toULong(),
        height.toULong(),
        bitsPerComponent.toULong(),
        bytesPerRow.toULong(),
        colorSpace,
        CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
    )
    
    if (context == null) {
        nativeHeap.free(rawData)
        return null
    }
    
    CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
    
    var minX = width
    var minY = height
    var maxX = 0
    var maxY = 0
    var found = false
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            val alpha = rawData[(y * bytesPerRow + x * bytesPerPixel + 3)].toInt() and 0xFF
            if (alpha > 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                found = true
            }
        }
    }
    
    nativeHeap.free(rawData)
    
    return if (found) {
        SerialRect(minX.toFloat(), minY.toFloat(), (maxX - minX + 1).toFloat(), (maxY - minY + 1).toFloat())
    } else {
        null
    }
}

actual suspend fun trimTransparency(imageSource: String): ByteArray? {
    val bounds = getNonTransparentBounds(imageSource) ?: return null
    val size = getImageSize(imageSource) ?: return null
    return cropImage(imageSource, bounds, size.first.toFloat(), size.second.toFloat(), true, null, null)
}
