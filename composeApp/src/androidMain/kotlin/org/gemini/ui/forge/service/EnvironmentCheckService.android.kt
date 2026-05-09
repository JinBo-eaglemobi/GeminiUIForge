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

    override fun uninstallItem(name: String): Flow<String> = flowOf("Android 环境暂不支持自动卸载")

    override suspend fun getInstalledPipPackages(): List<org.gemini.ui.forge.model.app.PipPackageInfo> = emptyList()

    override suspend fun fetchOutdatedPipPackages(): Map<String, String> = emptyMap()

    override suspend fun fetchPackageUrl(packageName: String): String? = null

    override fun batchInstallPipPackages(names: List<String>): Flow<String> = flowOf("Android 暂不支持")

    override fun batchUninstallPipPackages(names: List<String>): Flow<String> = flowOf("Android 暂不支持")

    override suspend fun searchPipPackage(query: String): org.gemini.ui.forge.model.app.PipPackageInfo? = null
}

actual fun createEnvironmentCheckService(): EnvironmentCheckService {
    return AndroidEnvironmentCheckService()
}
