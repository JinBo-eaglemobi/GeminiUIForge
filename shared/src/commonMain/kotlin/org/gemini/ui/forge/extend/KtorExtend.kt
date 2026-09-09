package org.gemini.ui.forge.extend

import io.ktor.client.statement.*

fun HttpResponse.isSuccessful() = this.status.value in 200 .. 299