package org.gemini.ui.forge.ui.feature.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.feature.workspace.property.AssetGenPropertyContent
import org.gemini.ui.forge.ui.feature.workspace.property.LayoutPropertyContent
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 统一属性面板：集成布局编辑、物理参数、AI 生成配置及组件特有属性。
 * 采用选项卡（Tabs）结构，支持在不同职责间无缝切换。
 *
 * @param state 当前工作区状态快照。
 * @param viewModel 统一工作区 ViewModel。
 * @param apiKey AI 服务密钥。
 * @param onRefineClick 触发区域重塑的回调。
 * @param onSetReferenceAreaClick 触发参考区域设置的回调。
 * @param onShowHistory 触发历史资源选择的回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedPropertyPanel(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    apiKey: String,
    onRefineClick: (String?) -> Unit,
    onSetReferenceAreaClick: (String) -> Unit,
    onShowHistory: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部导航选项卡
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Box(Modifier.padding(vertical = 12.dp)) {
                    Text(stringResource(Res.string.editor_properties), style = MaterialTheme.typography.labelLarge)
                }
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Box(Modifier.padding(vertical = 12.dp)) {
                    Text(stringResource(Res.string.editor_gen_settings), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 内容滚动区
        Box(
            modifier = Modifier.weight(1f).padding(LocalAppSpacing.current.medium).verticalScroll(rememberScrollState())
        ) {
            if (selectedTab == 0) {
                LayoutPropertyContent(
                    state = state,
                    viewModel = viewModel,
                    apiKey = apiKey,
                    onRefineClick = onRefineClick,
                    onSetReferenceAreaClick = onSetReferenceAreaClick,
                    onDeleteRequest = onDeleteRequest
                )
            } else {
                AssetGenPropertyContent(
                    state = state,
                    viewModel = viewModel,
                    apiKey = apiKey,
                    onShowHistory = onShowHistory
                )
            }
        }
    }
}

/**
 * 通用的可折叠板块组件。
 */
@Composable
fun CollapsibleSection(
    title: String,
    defaultExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp, start = 8.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
        HorizontalDivider(modifier = Modifier.alpha(0.4f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
