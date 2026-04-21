package org.gemini.ui.forge.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.gemini.ui.forge.model.app.FullEnvironmentStatus

class AndroidEnvironmentCheckService : EnvironmentCheckService {
    override suspend fun checkAll(): FullEnvironmentStatus {
        return FullEnvironmentStatus(items = emptyList(), isChecking = false)
    }

    override fun installItem(name: String): Flow<String> {
        return flowOf("Android 环境暂不支持自动安装")
    }

    override suspend fun isPythonAvailable(): Boolean = true
}

actual fun createEnvironmentCheckService(): EnvironmentCheckService {
    return AndroidEnvironmentCheckService()
}
