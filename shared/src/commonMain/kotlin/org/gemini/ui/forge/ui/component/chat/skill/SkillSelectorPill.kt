package org.gemini.ui.forge.ui.component.chat.skill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 输入控制栏技能状态胶囊与手动切换药丸组件 (一文件一 Composable)
 */
@Composable
fun SkillSelectorPill(
    activeSkills: List<UiStudioSkill>,
    currentMode: SkillActivationMode,
    onModeChanged: (SkillActivationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    var isMenuOpen by remember { mutableStateOf(false) }

    val hasActiveSkill = activeSkills.isNotEmpty() && currentMode != SkillActivationMode.DISABLED
    val pillBg = if (hasActiveSkill) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (hasActiveSkill) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = pillBg,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (hasActiveSkill) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f) else Color.Transparent
            ),
            modifier = Modifier.clickable { isMenuOpen = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (hasActiveSkill) MaterialTheme.colorScheme.tertiary else contentColor
                )
                Text(
                    text = when {
                        currentMode == SkillActivationMode.DISABLED -> "技能: 已停用"
                        hasActiveSkill -> "技能: ${activeSkills.first().name}"
                        else -> "技能: 自动探测"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
            }
        }

        DropdownMenu(
            expanded = isMenuOpen,
            onDismissRequest = { isMenuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("⚡ 智能自动路由 (意图/文档自动激活)") },
                trailingIcon = {
                    if (currentMode == SkillActivationMode.AUTO) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = {
                    onModeChanged(SkillActivationMode.AUTO)
                    isMenuOpen = false
                }
            )
            DropdownMenuItem(
                text = { Text("🚀 强制启用界面组装 (Spec-to-View)") },
                trailingIcon = {
                    if (currentMode == SkillActivationMode.ALWAYS_ON) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = {
                    onModeChanged(SkillActivationMode.ALWAYS_ON)
                    isMenuOpen = false
                }
            )
            DropdownMenuItem(
                text = { Text("🚫 停用技能 (纯文本自由对话)") },
                trailingIcon = {
                    if (currentMode == SkillActivationMode.DISABLED) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = {
                    onModeChanged(SkillActivationMode.DISABLED)
                    isMenuOpen = false
                }
            )
        }
    }
}
