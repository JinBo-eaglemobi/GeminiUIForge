package org.gemini.ui.forge.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.gemini.ui.forge.utils.AppLogger

/**
 * 全局校验后处理选项仓库 (单例)
 *
 * 承载顶部状态栏校验拆分下拉按钮的两个独立选项状态，并经 ConfigManager 持久化到
 * `~/.geminiuiforge/config.conf`，实现跨重启的全局记忆：
 * - [syncData]  选项 a：校验后参考数据同步模块数据 (UI 默认未勾选，用户未操作过即显示未勾选)
 * - [alsoCrop]  选项 b：校验后同时使用参考范围对参考图进行切图并保存到磁盘 (默认关闭)
 *
 * 两通道语义分流：
 * - UI 通道：完全遵循内存/缓存状态，用户未操作过时两选项均为未勾选；
 * - MCP 通道 ([resolveMcpDefaults])：用户从未设置过缓存时，执行校验默认启用选项 a；
 *   一旦存在缓存记录，UI 与 MCP 均完全遵循缓存结果。
 */
object CalibrationOptionsManager {

    /** 持久化键名：选项 a (数据同步) */
    private const val KEY_SYNC_DATA = "CALIBRATE_SYNC_DATA"

    /** 持久化键名：选项 b (切图落盘) */
    private const val KEY_ALSO_CROP = "CALIBRATE_ALSO_CROP"

    /** 选项 a：校验后参考数据同步模块数据 (UI 默认未勾选，用户显式切换后遵循缓存) */
    var syncData: Boolean = false
        private set

    /** 选项 b：校验后切图并保存到磁盘 */
    var alsoCrop: Boolean = false
        private set

    /** 用户是否曾经显式切换过选项 (存在持久化缓存记录) */
    var hasCachedRecord: Boolean = false
        private set

    /** 是否已完成至少一次磁盘加载 */
    private var loaded = false

    private val configManager = ConfigManager()

    /** 独立后台协程作用域 (单例生命周期与进程一致，无泄漏风险) */
    private val ioScope = CoroutineScope(Dispatchers.Default)

    /**
     * 按需加载持久化选项 (挂起版，供 Composable LaunchedEffect 与 MCP 协程调用)
     * 无缓存记录时保持代码内默认值 (a 关 / b 关) 且不回写；异常不阻断流程。
     */
    suspend fun loadIfNeeded() {
        if (loaded) return
        try {
            val sync = configManager.loadKey(KEY_SYNC_DATA)?.toBooleanStrictOrNull()
            val crop = configManager.loadKey(KEY_ALSO_CROP)?.toBooleanStrictOrNull()
            if (sync != null) syncData = sync
            if (crop != null) alsoCrop = crop
            // 只要存在任意持久化记录，即视为用户曾经显式设置过
            hasCachedRecord = sync != null || crop != null
        } catch (e: Exception) {
            AppLogger.w("CalibrationOptions", "读取校验选项缓存失败: ${e.message}")
        } finally {
            loaded = true
        }
    }

    /**
     * MCP 校验通道的后处理选项裁决值
     *
     * - 用户从未设置过缓存 (首次)：选项 a 默认启用 (数据同步)，选项 b 遵循默认关闭；
     * - 存在缓存记录：完全遵循缓存结果，与 UI 显示状态保持一致。
     */
    fun resolveMcpDefaults(): Pair<Boolean, Boolean> {
        return if (hasCachedRecord) syncData to alsoCrop
        else true to alsoCrop
    }

    /**
     * 更新两选项并异步持久化到 config.conf
     *
     * @param newSyncData 选项 a 新状态
     * @param newAlsoCrop 选项 b 新状态
     */
    fun update(newSyncData: Boolean, newAlsoCrop: Boolean) {
        syncData = newSyncData
        alsoCrop = newAlsoCrop
        // 显式切换即产生持久化记录，此后 MCP 与 UI 均遵循缓存
        hasCachedRecord = true
        ioScope.launch {
            try {
                configManager.saveKey(KEY_SYNC_DATA, newSyncData.toString())
                configManager.saveKey(KEY_ALSO_CROP, newAlsoCrop.toString())
            } catch (e: Exception) {
                AppLogger.w("CalibrationOptions", "持久化校验选项失败: ${e.message}")
            }
        }
    }
}
