package org.gemini.ui.forge.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.gemini.ui.forge.model.app.FullEnvironmentStatus

class IosEnvironmentCheckService : EnvironmentCheckService {
    override suspend fun checkAll(): FullEnvironmentStatus {
        // iOS 平台暂不支持本地 Python 环境抠图，返回空列表
        return FullEnvironmentStatus(items = emptyList(), isChecking = false)
    }

    override fun installItem(name: String): Flow<String> = flow {
        emit("Not supported on iOS")
    }

    override suspend fun isPythonAvailable(): Boolean = false
}

actual fun createEnvironmentCheckService(): EnvironmentCheckService = IosEnvironmentCheckService()
