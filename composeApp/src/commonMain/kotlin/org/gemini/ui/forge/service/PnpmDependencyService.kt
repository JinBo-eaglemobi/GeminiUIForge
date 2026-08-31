package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.gameproject.PnpmDependencyStatus

/**
 * pnpm 依赖库检查与安装服务接口。
 * 解析项目 package.json 中声明的依赖，检查本地 node_modules 中的实际存在情况，
 * 并可执行 pnpm 安装（遵循 package.json 的版本声明）。
 */
interface PnpmDependencyService {

    /**
     * 检查项目全部声明依赖的本地安装情况。
     * 解析 package.json 的 dependencies 与 devDependencies，
     * 并逐项检查 node_modules/<包名> 是否存在。
     * @param projectDir 项目本地根目录（包含 package.json）
     * @return 依赖检查结果列表；package.json 不存在时返回空列表
     */
    suspend fun checkDependencies(projectDir: String): List<PnpmDependencyStatus>

    /**
     * 执行 pnpm 安装（install 遵循 package.json 版本声明，workspace 感知）。
     * @param projectDir 项目本地根目录
     * @param onLog 实时日志回调
     * @return 成功返回 null；失败返回错误描述
     */
    suspend fun installDependencies(projectDir: String, onLog: (String) -> Unit): String?
}

/**
 * 跨平台的服务实例化工厂。
 */
expect fun createPnpmDependencyService(): PnpmDependencyService
