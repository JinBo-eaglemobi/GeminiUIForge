package org.gemini.ui.forge.utils

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes

@OptIn(InternalResourceApi::class)
actual fun readResourceBytesSync(path: String): ByteArray = runBlocking {
    readResourceBytes(path)
}
