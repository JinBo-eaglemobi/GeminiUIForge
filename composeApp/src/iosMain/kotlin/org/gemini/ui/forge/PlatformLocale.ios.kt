package org.gemini.ui.forge

actual fun setAppLanguage(languageCode: String) {
    // Basic implementation for iOS. To fully support iOS language dynamic switching,
    // we would need NSUserDefaults manipulation, but for now we skip to avoid build errors.
}
