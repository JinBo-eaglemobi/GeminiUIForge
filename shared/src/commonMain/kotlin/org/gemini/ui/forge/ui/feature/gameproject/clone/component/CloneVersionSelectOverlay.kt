package org.gemini.ui.forge.ui.feature.gameproject.clone.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.service.CloneRefType
import org.gemini.ui.forge.service.CommitSummary
import org.gemini.ui.forge.service.GitRefCatalog
import org.gemini.ui.forge.service.RefSelection
import org.gemini.ui.forge.ui.theme.AppShapes
import org.jetbrains.compose.resources.stringResource

/**
 * 版本选择覆盖层组件：分支 / 标签 / 提交三类别切换 + 懒加载列表 + 手动输入。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun CloneVersionSelectOverlay(
    catalog: GitRefCatalog,
    onConfirm: (RefSelection) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var refType by remember { mutableStateOf(CloneRefType.BRANCH) }
    var branchList by remember { mutableStateOf<List<String>?>(null) }
    var tagList by remember { mutableStateOf<List<String>?>(null) }
    var commitList by remember { mutableStateOf<List<CommitSummary>?>(null) }
    var manualInput by remember { mutableStateOf("") }

    LaunchedEffect(catalog, refType) {
        when (refType) {
            CloneRefType.BRANCH -> if (branchList == null) branchList = catalog.branches()
            CloneRefType.TAG -> if (tagList == null) tagList = catalog.tags()
            CloneRefType.COMMIT -> if (commitList == null) commitList = catalog.commits()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = AppShapes.small
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(Res.string.gp_clone_select_version_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = refType == CloneRefType.BRANCH,
                    onClick = { refType = CloneRefType.BRANCH; manualInput = "" },
                    label = { Text(stringResource(Res.string.gp_form_ref_branch)) }
                )
                FilterChip(
                    selected = refType == CloneRefType.TAG,
                    onClick = { refType = CloneRefType.TAG; manualInput = "" },
                    label = { Text(stringResource(Res.string.gp_form_ref_tag)) }
                )
                FilterChip(
                    selected = refType == CloneRefType.COMMIT,
                    onClick = { refType = CloneRefType.COMMIT; manualInput = "" },
                    label = { Text(stringResource(Res.string.gp_form_ref_commit)) }
                )
            }
            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val currentList: List<String>? = when (refType) {
                    CloneRefType.BRANCH -> branchList
                    CloneRefType.TAG -> tagList
                    CloneRefType.COMMIT -> commitList?.map { "${it.shortHash}  ${it.subject}" }
                }
                when {
                    currentList == null -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.gp_clone_ref_loading), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    currentList.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.gp_clone_ref_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(currentList) { entry ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            manualInput = entry.substringBefore("  ")
                                        }
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = entry,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                placeholder = { Text(stringResource(Res.string.gp_clone_ref_manual_ph)) },
                singleLine = true,
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onConfirm(RefSelection(CloneRefType.BRANCH, null)) },
                    shape = AppShapes.medium
                ) {
                    Text(stringResource(Res.string.gp_clone_ref_default))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCancel, shape = AppShapes.medium) {
                    Text(stringResource(Res.string.gp_clone_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val value = manualInput.trim()
                        onConfirm(RefSelection(refType, value.ifBlank { null }))
                    },
                    enabled = refType == CloneRefType.BRANCH || manualInput.isNotBlank(),
                    shape = AppShapes.medium
                ) {
                    Text(stringResource(Res.string.gp_clone_ref_confirm))
                }
            }
        }
    }
}
