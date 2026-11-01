package org.gemini.ui.forge.service.tool

import kotlin.concurrent.Volatile

/**
 * 中立工具聚合中心 (ToolHub)。
 *
 * 采用六边形架构的依赖倒置原则：
 * ToolHub 位于中立根包，完全不感知也不 import 具体的 [mcp] 或 [ai] 子包。
 * 各功能域（如 MCP 域、AI 域）在启动初始化时向 ToolHub 注册自身的提供者回调 [registerSource]。
 *
 * 所有调用执行统一经由 [findTool] 进入，实现"名称即身份，执行入口唯一"。
 */
object ToolHub {

    @Volatile
    private var sourcesSnapshot: Map<String, () -> List<ToolDefinition>> = emptyMap()

    /**
     * 注册工具源（由 mcp 或 ai 等独立域在自身初始化时调用）
     *
     * @param domain 领域标识符（如 "mcp", "ai"）
     * @param provider 动态提供该域全部工具清单的回调函数
     */
    fun registerSource(domain: String, provider: () -> List<ToolDefinition>) {
        sourcesSnapshot = sourcesSnapshot + (domain to provider)
    }

    /**
     * 注销指定领域的工具源
     */
    fun unregisterSource(domain: String) {
        sourcesSnapshot = sourcesSnapshot - domain
    }

    /**
     * 获取全量工具列表（聚合去重）。
     * 执行层全量可用，不受暴露策略裁剪。
     */
    fun getAllTools(): List<ToolDefinition> {
        val providers = sourcesSnapshot.values
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ToolDefinition>()

        for (provider in providers) {
            val list = try {
                provider()
            } catch (_: Throwable) {
                emptyList()
            }
            for (tool in list) {
                if (seen.add(tool.name)) {
                    result.add(tool)
                }
            }
        }
        return result
    }

    /**
     * 根据名称全局检索工具定义。
     * 统一执行入口，保证双通道查找路径一致。
     */
    fun findTool(name: String): ToolDefinition? {
        val providers = sourcesSnapshot.values
        for (provider in providers) {
            val list = try {
                provider()
            } catch (_: Throwable) {
                emptyList()
            }
            val match = list.firstOrNull { it.name == name }
            if (match != null) return match
        }
        return null
    }

    /**
     * 获取受暴露策略保护的核心工具清单（供大模型默认 Function Calling 声明使用）
     */
    fun getCoreTools(): List<ToolDefinition> {
        val all = getAllTools()
        return all.filter { ToolExposurePolicy.isCoreExposed(it.name) }
    }

    /**
     * 清理所有注册源（主要用于测试）
     */
    fun clear() {
        sourcesSnapshot = emptyMap()
    }
}
