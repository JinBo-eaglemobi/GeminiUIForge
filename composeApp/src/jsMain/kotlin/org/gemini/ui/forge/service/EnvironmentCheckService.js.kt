package org.gemini.ui.forge.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.gemini.ui.forge.model.app.FullEnvironmentStatus

actual fun createEnvironmentCheckService(): EnvironmentCheckService {
    return object : EnvironmentCheckService {
        override suspend fun isPythonAvailable(): Boolean = false
        override suspend fun checkAll(): FullEnvironmentStatus {
            return FullEnvironmentStatus()
        }
        override fun installItem(name: String): Flow<String> = emptyFlow()
    }
}
