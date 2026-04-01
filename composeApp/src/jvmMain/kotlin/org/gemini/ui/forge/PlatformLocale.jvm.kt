package org.gemini.ui.forge

import java.util.Locale

actual fun setAppLanguage(languageCode: String) {
    Locale.setDefault(Locale.of(languageCode))
}
