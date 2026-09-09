package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.gameproject.PnpmDependencyStatus

actual fun createPnpmDependencyService(): PnpmDependencyService = object : PnpmDependencyService {
    override suspend fun checkDependencies(projectDir: String): List<PnpmDependencyStatus> = emptyList()
    override suspend fun installDependencies(projectDir: String, onLog: (String) -> Unit): String? = "Browser 暂不支持 pnpm 安装"
}
