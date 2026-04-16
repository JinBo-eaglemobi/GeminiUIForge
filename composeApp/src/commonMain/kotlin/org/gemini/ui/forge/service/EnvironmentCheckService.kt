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
}

/**
 * 提供跨平台的服务实例化工厂
 */
expect fun createEnvironmentCheckService(): EnvironmentCheckService
