package org.gemini.ui.forge

import platform.UIKit.UIDevice
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import androidx.compose.ui.input.pointer.PointerIcon

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getCurrentTimeMillis(): Long = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon.Default
actual val ResizeVerticalIcon: PointerIcon = PointerIcon.Default