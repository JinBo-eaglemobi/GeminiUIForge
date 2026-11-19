package org.gemini.ui.forge.ui.uistudio.dialog

import androidx.compose.runtime.Composable
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.ui.uistudio.model.NestingRepairPlan
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.uistudio_repair_confirm_title
import geminiuiforge.composeapp.generated.resources.uistudio_repair_confirm_body
import geminiuiforge.composeapp.generated.resources.action_confirm

/**
 * 内嵌子页面一键拆分修复确认弹窗。
 *
 * 在执行破坏性或结构性重构前向用户展示即将拆分的违规组件总数，
 * 告知操作将自动生成独立页面并支持整体撤销，由用户显式确认后方可落地。
 *
 * @param plan 待执行的修复预案数据
 * @param onConfirm 用户确认执行修复的回调
 * @param onDismiss 用户取消或关闭弹窗的回调
 */
@Composable
fun NestingRepairConfirmDialog(
    plan: NestingRepairPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppConfirmDialog(
        title = stringResource(Res.string.uistudio_repair_confirm_title),
        message = stringResource(Res.string.uistudio_repair_confirm_body, plan.errors.size),
        confirmText = stringResource(Res.string.action_confirm),
        isDestructive = false,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
