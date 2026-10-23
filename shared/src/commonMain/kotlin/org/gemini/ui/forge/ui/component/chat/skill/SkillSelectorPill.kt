package org.gemini.ui.forge.ui.component.chat.skill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.skill.SkillActivationMode
import org.gemini.ui.forge.model.skill.UiStudioSkill
import org.gemini.ui.forge.ui.component.tip

/**
 * 输入控制栏技能状态胶囊与手动切换药丸组件 (一文件一 Composable)
 */
@Composable
fun SkillSelectorPill(
    skills: List<UiStudioSkill> = emptyList(),
    enabledSkillIds: Set<String> = emptySet(),
    onToggleSkill: (skillId: String, isEnabled: Boolean) -> Unit = { _, _ -> },
    onRefreshSkills: (() -> Unit)? = null,
    onOpenSkillsDirectory: (() -> Unit)? = null,
    activeSkills: List<UiStudioSkill> = emptyList(),
    currentMode: SkillActivationMode = SkillActivationMode.AUTO,
    onModeChanged: ((SkillActivationMode) -> Unit)? = null,
    minMenuWidth: Dp = 380.dp,
    maxMenuWidth: Dp = 500.dp,
    maxMenuHeight: Dp = 520.dp,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    var pillWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    // 计算当前处于启用状态的技能数量
    val effectiveSkills = if (skills.isNotEmpty()) skills else activeSkills
    val activeSkillsCount = if (skills.isNotEmpty()) {
        skills.count { it.id in enabledSkillIds }
    } else {
        if (currentMode != SkillActivationMode.DISABLED) activeSkills.size else 0
    }
    val hasAnySkillEnabled = activeSkillsCount > 0

    val pillBg = if (hasAnySkillEnabled) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (hasAnySkillEnabled) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when {
        effectiveSkills.isEmpty() -> "技能"
        activeSkillsCount == 0 -> "技能: 已停用"
        activeSkillsCount == effectiveSkills.size -> "技能 (${effectiveSkills.size})"
        else -> "技能 ($activeSkillsCount/${effectiveSkills.size})"
    }

    Box(
        modifier = modifier.onGloballyPositioned { pillWidthPx = it.size.width }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = pillBg,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (hasAnySkillEnabled) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f) else Color.Transparent
            ),
            modifier = Modifier
                .clickable {
                    isMenuOpen = true
                    onRefreshSkills?.invoke()
                }
                .tip("查看与配置界面工程技能 (当前: $statusText)")
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
                    tint = if (hasAnySkillEnabled) MaterialTheme.colorScheme.tertiary else contentColor
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
            }
        }

        val pillWidthDp = with(density) { pillWidthPx.toDp() }

        CommonSkillSelectionMenu(
            expanded = isMenuOpen,
            skills = effectiveSkills,
            enabledSkillIds = enabledSkillIds,
            onToggleSkill = onToggleSkill,
            onDismissRequest = { isMenuOpen = false },
            onRefresh = onRefreshSkills,
            onOpenDirectory = onOpenSkillsDirectory,
            minMenuWidth = maxOf(minMenuWidth, pillWidthDp),
            maxMenuWidth = maxMenuWidth,
            maxMenuHeight = maxMenuHeight,
            offset = DpOffset(0.dp, (-8).dp)
        )
    }
}
