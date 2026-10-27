package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.ui.component.AppSearchField

/**
 * UI Studio 紧凑型独立自定义搜索框组件。
 *
 * 已平滑重构并委托给系统全局公共 [AppSearchField]，保持对外 API 100% 兼容。
 *
 * @param value 当前搜索关键词
 * @param onValueChange 关键词变更回调
 * @param placeholder 占位提示文案
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "搜索...",
    modifier: Modifier = Modifier
) {
    AppSearchField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier
    )
}
