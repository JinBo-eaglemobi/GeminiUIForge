package org.gemini.ui.forge

import platform.Foundation.NSUserDefaults

actual fun setAppLanguage(languageCode: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(listOf(languageCode), forKey = "AppleLanguages")
    defaults.synchronize()
}
