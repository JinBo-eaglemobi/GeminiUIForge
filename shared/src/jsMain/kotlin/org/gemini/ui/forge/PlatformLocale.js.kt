package org.gemini.ui.forge

import kotlinx.browser.document

actual fun setAppLanguage(languageCode: String) {
    document.documentElement?.setAttribute("lang", languageCode)
}
