package org.gemini.ui.forge.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.app.UpdateStatus
import org.gemini.ui.forge.model.app.UpdateInfo
import org.gemini.ui.forge.service.UpdateService
import org.gemini.ui.forge.data.repository.TemplateRepository

/**
 * 专门负责软件更新业务的独立 ViewModel
 * 整合了所有的检测、下载及重启安装逻辑
 */
class AppUpdateViewModel(
    private val templateRepo: TemplateRepository = TemplateRepository(),
    private val updateService: UpdateService = UpdateService("1.0.0")
) : ViewModel() {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /**
     * 检查软件更新
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            org.gemini.ui.forge.utils.AppLogger.i("AppUpdateViewModel", "🔄 正在手动触发更新检查...")
            _status.update { UpdateStatus.Checking }
            val info = updateService.checkUpdate()
            if (info != null) {
                org.gemini.ui.forge.utils.AppLogger.i("AppUpdateViewModel", "🔔 发现可用更新: v${info.version}")
                _status.update { UpdateStatus.Available(info) }
            } else {
                org.gemini.ui.forge.utils.AppLogger.i("AppUpdateViewModel", "✅ 检查完成：当前版本已是最新。")
                _status.update { UpdateStatus.UpToDate }
            }
        }
    }

    /**
     * 执行下载与重启安装流
     */
    fun performUpdate(info: UpdateInfo) {
        viewModelScope.launch(Dispatchers.Default) {
            org.gemini.ui.forge.utils.AppLogger.i("AppUpdateViewModel", "🚀 用户确认更新，开始处理版本: v${info.version}")
            try {
                // 1. 获取临时存放目录
                val storageDir = templateRepo.getDataDir()
                val tempPath = Path(storageDir, "update_" + info.fileName)
                org.gemini.ui.forge.utils.AppLogger.d("AppUpdateViewModel", "📂 临时更新包路径: $tempPath")

                // 2. 执行流式下载并报告进度
                updateService.downloadUpdate(info.downloadUrl, tempPath).collect { progress ->
                    _status.update { UpdateStatus.Downloading(progress) }
                }

                // 3. 准备就绪
                org.gemini.ui.forge.utils.AppLogger.i("AppUpdateViewModel", "💾 更新包下载已校验，准备交由平台接力脚本执行替换重启...")
                _status.update { UpdateStatus.ReadyToInstall }
                delay(1500) // 给 UI 一点反馈时间

                // 4. 触发跨平台重启接力
                getPlatform().applyUpdateAndRestart(tempPath.toString())
            } catch (e: Exception) {
                org.gemini.ui.forge.utils.AppLogger.e("AppUpdateViewModel", "❌ 更新安装流程中断", e)
                _status.update { UpdateStatus.Error(e.message ?: "Unknown Error") }
            }
        }
    }
}
