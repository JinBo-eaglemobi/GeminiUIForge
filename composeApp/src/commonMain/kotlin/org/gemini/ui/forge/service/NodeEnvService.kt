package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.gameproject.NodeEnvStatus

/**
 * Node.js 本地环境检测服务接口。
 * 用户在创建向导中选择包管理模式后立即激活检测：
 * node 版本、npm 版本与全局配置（registry / prefix）、pnpm 版本。
 */
interface NodeEnvService {

    /**
     * 执行全量环境检测：node / npm（版本、registry、prefix）/ pnpm。
     */
    suspend fun checkNodeEnv(): NodeEnvStatus
}

/**
 * 跨平台的服务实例化工厂。
 */
expect fun createNodeEnvService(): NodeEnvService
