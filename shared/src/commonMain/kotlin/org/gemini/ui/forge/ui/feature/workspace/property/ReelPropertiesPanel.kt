package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.asset.ReelSymbolManagerDialog
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel

/**
 * 转轴组件的专属属性面板
 */
@Composable
fun ReelPropertiesPanel(
    viewModel: ProjectWorkspaceViewModel,
    state: ProjectWorkspaceState,
    apiKey: String,
    selectedBlock: UIBlock,
    onPropertiesChanged: (BlockProperties) -> Unit
) {
    val props = selectedBlock.properties as? BlockProperties.ReelProperties ?: BlockProperties.ReelProperties()
    var showSymbolManager by remember { mutableStateOf(false) }

    Text("转轴核心配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

    Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
        NumberOutlinedTextField(
            value = props.rows.toString(),
            onValueChange = { onPropertiesChanged(props.copy(rows = it.toIntOrNull() ?: props.rows)) },
            label = { Text("行数") },
            modifier = Modifier.weight(1f),
            isFloat = false,
            enabled = !state.isGenerating
        )
        NumberOutlinedTextField(
            value = props.columns.toString(),
            onValueChange = { onPropertiesChanged(props.copy(columns = it.toIntOrNull() ?: props.columns)) },
            label = { Text("列数") },
            modifier = Modifier.weight(1f),
            isFloat = false,
            enabled = !state.isGenerating
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = props.showBackground,
            onCheckedChange = { onPropertiesChanged(props.copy(showBackground = it)) },
            enabled = !state.isGenerating
        )
        Text("显示外框背景板", style = MaterialTheme.typography.bodySmall)
        
        Icon(
            Icons.Default.Info,
            contentDescription = "说明",
            modifier = Modifier.padding(start = 4.dp).size(14.dp).tip("是否为整个转轴区域渲染一个背板/底框图层。若取消勾选，转轴内的符号将直接浮现在主背景之上。"),
            tint = MaterialTheme.colorScheme.outline
        )
        
        Spacer(Modifier.weight(1f))
        
        IconButton(
            onClick = {
                onPropertiesChanged(props.copy(rollSeed = props.rollSeed + 1))
            },
            modifier = Modifier.size(28.dp).tip("随机洗牌：重新打乱并分配当前符号集中的元素到网格中"),
            enabled = !state.isGenerating
        ) {
            Icon(Icons.Default.Refresh, "重新生成元素", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

    // 仅仅提供一个入口按钮
    Button(
        onClick = { showSymbolManager = true },
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = AppShapes.small
    ) {
        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("配置转轴符号集 (共 ${props.items.size} 个)")
    }

    // 独立的元素集管理器 Dialog
    if (showSymbolManager) {
        ReelSymbolManagerDialog(
            props = props,
            onDismiss = { showSymbolManager = false },
            viewModel = viewModel,
            apiKey = apiKey,
            state = state
        )
    }
}
