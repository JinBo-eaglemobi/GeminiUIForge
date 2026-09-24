package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.calibrate_confirm_execute
import geminiuiforge.composeapp.generated.resources.calibrate_confirm_options
import geminiuiforge.composeapp.generated.resources.calibrate_confirm_target_all
import geminiuiforge.composeapp.generated.resources.calibrate_confirm_target_multi
import geminiuiforge.composeapp.generated.resources.calibrate_confirm_target_single
import geminiuiforge.composeapp.generated.resources.calibrate_confirm_title
import geminiuiforge.composeapp.generated.resources.calibrate_no_workspace
import geminiuiforge.composeapp.generated.resources.calibrate_opt_disabled
import geminiuiforge.composeapp.generated.resources.calibrate_opt_enabled
import geminiuiforge.composeapp.generated.resources.calibrate_option_also_crop
import geminiuiforge.composeapp.generated.resources.calibrate_option_sync_data
import geminiuiforge.composeapp.generated.resources.calibrate_split_tooltip
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.manager.CalibrationOptionsManager
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.utils.Toast

/**
 * 顶部状态栏校验拆分下拉按钮 (Split Dropdown Button)
 *
 * - 主按钮区：直接点击触发校验 —— 有选中模块 (多选/单选) 时校验选中集合，无选中时执行全量校验；
 *   执行前强制弹出二次确认弹窗 (MCP 通道除外)；
 * - 下拉箭头区：展开两个独立可勾选的校验后处理选项 (点击仅切换状态，菜单不关闭)：
 *   a. 校验后参考数据同步模块数据；b. 校验后切图并保存到磁盘；
 * - 选项状态经 [CalibrationOptionsManager] 全局持久化，跨重启保持记忆。
 */
@Composable
fun CalibrateSplitButton(modifier: Modifier = Modifier) {
    // 选项本地镜像状态 (初始取仓库内存值，挂起加载完成后刷新)
    var syncOn by remember { mutableStateOf(CalibrationOptionsManager.syncData) }
    var cropOn by remember { mutableStateOf(CalibrationOptionsManager.alsoCrop) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    // 待校验目标：null 代表全量；点击主按钮时实时判定
    var pendingTargets by remember { mutableStateOf<Set<String>?>(null) }

    // 首次进入组合时按需加载持久化选项 (无缓存记录时默认 a 开 / b 关)
    LaunchedEffect(Unit) {
        CalibrationOptionsManager.loadIfNeeded()
        syncOn = CalibrationOptionsManager.syncData
        cropOn = CalibrationOptionsManager.alsoCrop
    }

    // 预取固定文案 (动态目标行在弹窗分支内用带参 stringResource 组装)
    val tooltipText = stringResource(Res.string.calibrate_split_tooltip)
    val confirmTitle = stringResource(Res.string.calibrate_confirm_title)
    val enabledText = stringResource(Res.string.calibrate_opt_enabled)
    val disabledText = stringResource(Res.string.calibrate_opt_disabled)
    val confirmBtnText = stringResource(Res.string.calibrate_confirm_execute)
    val optionSyncText = stringResource(Res.string.calibrate_option_sync_data)
    val optionCropText = stringResource(Res.string.calibrate_option_also_crop)
    val noWorkspaceText = stringResource(Res.string.calibrate_no_workspace)

    /** 主按钮点击：实时获取活跃工作区 VM，判定目标集合后弹出确认 */
    fun onMainClicked() {
        val vm = McpUiActionPipeline.getActiveViewModel()
        if (vm == null) {
            Toast.show(noWorkspaceText, ToastType.INFO)
            return
        }
        val multiIds = vm.state.value.selectedBlockIds
        pendingTargets = when {
            multiIds.isNotEmpty() -> multiIds
            vm.state.value.selectedBlockId != null -> setOf(vm.state.value.selectedBlockId!!)
            else -> null
        }
        showConfirm = true
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 主按钮区：直接触发校验 (带确认弹窗)
            IconButton(onClick = { onMainClicked() }, modifier = Modifier.size(32.dp).tip(tooltipText)) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            VerticalDivider(
                modifier = Modifier.height(18.dp).padding(horizontal = 1.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            // 下拉箭头区：展开选项菜单 (点击菜单项仅切换状态，不关闭)
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    // 选项 a：校验后参考数据同步模块数据
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            syncOn = !syncOn
                            CalibrationOptionsManager.update(syncOn, cropOn)
                        }.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Checkbox(checked = syncOn, onCheckedChange = null)
                        Text(
                            text = optionSyncText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    // 选项 b：校验后切图并保存到磁盘
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            cropOn = !cropOn
                            CalibrationOptionsManager.update(syncOn, cropOn)
                        }.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Checkbox(checked = cropOn, onCheckedChange = null)
                        Text(
                            text = optionCropText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // 执行前强制二次确认弹窗 (MCP 通道不经过此处)
    if (showConfirm) {
        val vm = McpUiActionPipeline.getActiveViewModel()
        val blocks = vm?.state?.value?.currentPage?.blocks ?: emptyList()
        // 统计目标规模与单目标名称 (全量计数 / 单选定位名称)
        var totalCount = 0
        var singleBlockId: String? = null
        fun scanBlocks(list: List<UIBlock>) {
            list.forEach {
                totalCount++
                if (singleBlockId == null && pendingTargets?.contains(it.id) == true) singleBlockId = it.id
                scanBlocks(it.children)
            }
        }
        scanBlocks(blocks)
        val targetLine = when {
            pendingTargets == null -> stringResource(Res.string.calibrate_confirm_target_all, totalCount)
            pendingTargets!!.size == 1 -> stringResource(Res.string.calibrate_confirm_target_single, singleBlockId ?: pendingTargets!!.first())
            else -> stringResource(Res.string.calibrate_confirm_target_multi, pendingTargets!!.size)
        }
        val optionsLine = stringResource(
            Res.string.calibrate_confirm_options,
            if (syncOn) enabledText else disabledText,
            if (cropOn) enabledText else disabledText
        )
        AppConfirmDialog(
            title = confirmTitle,
            message = "$targetLine\n$optionsLine",
            confirmText = confirmBtnText,
            onConfirm = {
                vm?.calibrateBlocks(
                    targetBlockIds = pendingTargets,
                    bindReference = syncOn,
                    saveCropToDisk = cropOn
                )
                showConfirm = false
            },
            onDismiss = { showConfirm = false }
        )
    }
}
