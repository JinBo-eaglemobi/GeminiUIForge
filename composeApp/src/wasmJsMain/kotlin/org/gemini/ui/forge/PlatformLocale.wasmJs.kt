package org.gemini.ui.forge

actual fun setAppLanguage(languageCode: String) {
    // JavaScript/WASM environment usually doesn't allow overriding system locale synchronously
    // in standard Kotlin standard library without JS-specific API calls.
}
