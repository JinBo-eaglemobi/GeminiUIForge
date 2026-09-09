package org.gemini.ui.forge.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

    override fun uninstallItem(name: String): Flow<String> = flowOf("iOS 暂不支持")

    override suspend fun getInstalledPipPackages(): List<org.gemini.ui.forge.model.app.PipPackageInfo> = emptyList()

    override suspend fun fetchOutdatedPipPackages(): Map<String, String> = emptyMap()

    override suspend fun fetchPackageUrl(packageName: String): String? = null

    override fun batchInstallPipPackages(names: List<String>): Flow<String> = flowOf("iOS 暂不支持")

    override fun batchUninstallPipPackages(names: List<String>): Flow<String> = flowOf("iOS 暂不支持")

    override suspend fun searchPipPackage(query: String): org.gemini.ui.forge.model.app.PipPackageInfo? = null

    override suspend fun fetchTopPackages(): List<String> = emptyList()
}

actual fun createEnvironmentCheckService(): EnvironmentCheckService = IosEnvironmentCheckService()
