package org.gemini.ui.forge.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.gemini.ui.forge.model.app.FullEnvironmentStatus

actual fun createEnvironmentCheckService(): EnvironmentCheckService {
    return object : EnvironmentCheckService {
        override suspend fun isPythonAvailable(): Boolean = false

    override fun uninstallItem(name: String): Flow<String> = flowOf("Browser 暂不支持")

    override suspend fun listPipPackages(): List<org.gemini.ui.forge.model.app.PipPackageInfo> = emptyList()

    override suspend fun fetchPackageUrl(packageName: String): String? = null

    override fun batchInstallPipPackages(names: List<String>): Flow<String> = flowOf("Browser 暂不支持")

    override fun batchUninstallPipPackages(names: List<String>): Flow<String> = flowOf("Browser 暂不支持")

    override suspend fun searchPipPackage(query: String): org.gemini.ui.forge.model.app.PipPackageInfo? = null        override suspend fun checkAll(): FullEnvironmentStatus {
            return FullEnvironmentStatus()
        }
        override fun installItem(name: String): Flow<String> = emptyFlow()
    }
}
