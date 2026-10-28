package org.gemini.ui.forge.ui.component.chat.skill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import org.gemini.ui.forge.ui.component.AppMenu
import org.gemini.ui.forge.ui.component.AppMenuTier
import org.gemini.ui.forge.ui.component.tip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 界面工程技能列表查看与单项加载开关菜单 (一文件一 Composable)
 * 菜单项文字与描述 100% 来源于技能配置文件，无任何自定义合成选项
 */
@Composable
fun CommonSkillSelectionMenu(
    expanded: Boolean,
    skills: List<UiStudioSkill>,
    enabledSkillIds: Set<String>,
    onToggleSkill: (skillId: String, isEnabled: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    onOpenDirectory: (() -> Unit)? = null,
    minMenuWidth: Dp = 380.dp,
    maxMenuWidth: Dp = 500.dp,
    maxMenuHeight: Dp = 520.dp,
    offset: DpOffset = DpOffset(0.dp, (-8).dp),
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val colorScheme = MaterialTheme.colorScheme
    val activeCount = skills.count { it.id in enabledSkillIds }
    AppMenu(
        expanded = expanded,
        items = skills,
        onDismissRequest = onDismissRequest,
        onItemSelected = { },
        tier = AppMenuTier.WIDE,
        offset = offset,
        maxMenuHeight = maxMenuHeight,
        modifier = modifier,
        headerContent = {
            // 1. 顶部 Header：技能列表信息与总览徽章
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ 界面工程技能",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (activeCount > 0) colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "$activeCount/${skills.size} 已加载",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeCount > 0) colorScheme.primary
                                        else colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "查看已安装技能，控制单个技能是否注入对话模型",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(26.dp).tip("重新扫描外部技能目录")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Skills",
                                modifier = Modifier.size(15.dp),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onOpenDirectory != null) {
                        IconButton(
                            onClick = onOpenDirectory,
                            modifier = Modifier.size(26.dp).tip("打开本地技能配置目录")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open Skills Folder",
                                modifier = Modifier.size(15.dp),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = 4.dp)
            )
        },
        emptyContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "暂无已注册技能",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "技能配置文件位于 skills/ 目录",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        },
        itemContent = { skill ->
            val isSkillEnabled = skill.id in enabledSkillIds
            SkillItemCard(
                skill = skill,
                isEnabled = isSkillEnabled,
                onToggle = { isChecked ->
                    onToggleSkill(skill.id, isChecked)
                }
            )
        }
    )
}
