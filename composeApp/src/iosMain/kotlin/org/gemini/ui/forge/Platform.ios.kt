package org.gemini.ui.forge

import androidx.compose.ui.input.pointer.PointerIcon
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDevice

import platform.UIKit.UIApplication
import platform.Foundation.NSURL

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    
    override fun openInBrowser(url: String) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl != null) {
            UIApplication.sharedApplication.openURL(nsUrl)
        }
    }

    override fun applyUpdateAndRestart(tempFilePath: String) {
        // iOS 平台不支持静默自更新，通常由 App Store 处理
        println("Update not supported on iOS platform: $tempFilePath")
    }
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
actual val ResizeVerticalIcon: PointerIcon = PointerIcon.Default