package org.gemini.ui.forge.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.ui.component.ToastType

/**
 * 二次确认后即时物理落盘与事务性执行通用调度器。
 *
 * 核心人机工程学哲学：
 * 凡是经过用户显式二次确认的决策，均视为正式生效的严肃指令。该调度器统一接管：
 * 1. 内存状态即时同步应用（UI 立即响应，零卡顿感）；
 * 2. 物理磁盘 IO 与深度清理异步安全调度（后台执行，避免阻塞主线程）；
 * 3. 关联账本 / 配置中心即时持久化刷盘（彻底杜绝未手动保存导致的冷启回退与状态脱节）；
 * 4. 统一的结构化异常捕获、日志记录与轻量气泡通知。
 *
 * @param actionName 操作业务名称，用于结构化日志与追踪（例如 "彻底删除素材文件夹"）
 * @param scope 协程作用域（通常传入 ViewModel 的 scope）
 * @param onApplyState 内存状态修改（同步执行于调用线程，确保 UI 瞬间刷新）
 * @param onDiskIo 物理磁盘 IO 回调（可选，调度至 [Dispatchers.Default] 异步执行）
 * @param onFlushPersistence 即时落盘保存回调（可选，在磁盘 IO 完成后立即持久化更新元数据账本）
 * @param successToast 操作成功后展示给用户的气泡文案（可选，自动通过 [Toast.show] 弹出）
 * @param onFailure 异常回调（可选，未传时默认弹出错误气泡并记录 Error 日志）
 */
fun executeConfirmedAction(
    actionName: String,
    scope: CoroutineScope,
    onApplyState: () -> Unit,
    onDiskIo: (suspend () -> Unit)? = null,
    onFlushPersistence: (suspend () -> Unit)? = null,
    successToast: String? = null,
    onFailure: ((Throwable) -> Unit)? = null
) {
    try {
        // 1. 同步应用内存状态，保证 UI 界面第一时间响应
        onApplyState()
    } catch (e: Throwable) {
        AppLogger.e("ConfirmedActionHelper", "❌ [$actionName] 内存状态更新失败", e)
        onFailure?.invoke(e)
        Toast.show("操作失败: ${e.message}", ToastType.ERROR)
        return
    }

    // 2. 调度后台 IO 与物理持久化落盘流水线
    scope.launch(Dispatchers.Default) {
        try {
            // A. 执行实际磁盘物理操作（如文件删除、跨目录移动、物理覆写等）
            onDiskIo?.invoke()

            // B. 执行元数据账本/配置中心的即时物理刷盘
            onFlushPersistence?.invoke()

            AppLogger.i("ConfirmedActionHelper", "✅ [$actionName] 即时物理落盘流水线已完成")

            // C. 成功反馈
            if (!successToast.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.show(successToast, ToastType.SUCCESS)
                }
            }
        } catch (e: Throwable) {
            AppLogger.e("ConfirmedActionHelper", "💥 [$actionName] 磁盘物理操作或持久化刷盘异常", e)
            withContext(Dispatchers.Main) {
                Toast.show("操作未完全完成: ${e.message}", ToastType.ERROR)
            }
            onFailure?.invoke(e)
        }
    }
}
