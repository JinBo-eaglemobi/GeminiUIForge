package org.gemini.ui.forge.service

import kotlinx.coroutines.flow.Flow
import org.gemini.ui.forge.model.app.FullEnvironmentStatus

/**
 * 环境检查服务接口
 */
interface EnvironmentCheckService {
    /**
     * 执行全量环境检查
     */
    suspend fun checkAll(): FullEnvironmentStatus

    /**
     * 安装特定依赖项并返回实时日志流
     */
    fun installItem(name: String): Flow<String>

    /**
     * 判断 Python 基础环境是否可用
     */
    suspend fun isPythonAvailable(): Boolean

    /**
     * 卸载特定依赖项并返回实时日志流
     */
    fun uninstallItem(name: String): Flow<String>

    /**
     * 列出所有的 Pip 包 (包含已安装、可更新、以及推荐未安装的包)
     */
    suspend fun listPipPackages(): List<org.gemini.ui.forge.model.app.PipPackageInfo>

    /**
     * 获取包的 Github / Project URL
     */
    suspend fun fetchPackageUrl(packageName: String): String?

    /**
     * 批量更新/安装包
     */
    fun batchInstallPipPackages(names: List<String>): Flow<String>

    /**
     * 批量卸载包
     */
    fun batchUninstallPipPackages(names: List<String>): Flow<String>

    /**
     * 在线搜索/查询特定的 Pip 包信息
     */
    suspend fun searchPipPackage(query: String): org.gemini.ui.forge.model.app.PipPackageInfo?
}

/**
 * 提供跨平台的服务实例化工厂
 */
expect fun createEnvironmentCheckService(): EnvironmentCheckService
