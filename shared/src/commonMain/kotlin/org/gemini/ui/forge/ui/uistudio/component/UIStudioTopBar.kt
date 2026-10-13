package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.action_save_layout
import geminiuiforge.composeapp.generated.resources.back_to_home
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * UI Studio 顶部导航与全局工具栏组件。
 * 提供大厅返回、工程名与分辨率展示、视口缩放状态、素材库开关与工程保存。
 */
@Composable
fun UIStudioTopBar(
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val state = viewModel.state
    val projectName = state.project?.projectId ?: "UI Project"

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：返回大厅按钮 + 标题徽章 + 项目名称
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                IconButton(
                    onClick = onBackToHome,
                    modifier = Modifier
                        .size(36.dp)
                        .tip(stringResource(Res.string.back_to_home))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back_to_home)
                    )
                }

                // UI Studio 标识胶囊
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = spacing.extraSmall)
                ) {
                    Text(
                        text = "UI Studio",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 项目名称
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 画布当前视图分辨率药丸
                val currentView = state.project?.pages?.find { it.id == state.currentViewId }
                    ?: state.project?.pages?.firstOrNull()
                val currentW = currentView?.width?.toInt() ?: state.canvasWidth.toInt()
                val currentH = currentView?.height?.toInt() ?: state.canvasHeight.toInt()

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "$currentW × $currentH",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 右侧：素材库开关 + 保存按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                // 素材库侧栏显隐切换按钮
                IconButton(
                    onClick = { viewModel.toggleAssetPanel() },
                    modifier = Modifier
                        .size(36.dp)
                        .tip("切换左侧面板 (Toggle Sidebar)")
                ) {
                    Icon(
                        imageVector = if (state.isAssetPanelVisible) Icons.AutoMirrored.Filled.ViewSidebar else Icons.Default.FolderOpen,
                        contentDescription = "Toggle Sidebar",
                        tint = if (state.isAssetPanelVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 一键保存工程按钮
                IconButton(
                    onClick = { viewModel.saveProject(templateRepo) },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .size(36.dp)
                        .tip(stringResource(Res.string.action_save_layout))
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(Res.string.action_save_layout),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
