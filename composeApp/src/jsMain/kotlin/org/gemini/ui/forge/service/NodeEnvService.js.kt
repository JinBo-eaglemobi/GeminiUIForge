package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.gameproject.NodeEnvStatus

actual fun createNodeEnvService(): NodeEnvService = object : NodeEnvService {
    override suspend fun checkNodeEnv(): NodeEnvStatus = NodeEnvStatus()
}
