package org.gemini.ui.forge.ui.dialog.system

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.service.mcp.McpInteractionBridge
import org.gemini.ui.forge.service.mcp.McpInteractionRequest
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.Toast
import kotlinx.coroutines.launch

/**
 * MCP 交互宿主 Composable (挂载于 App 根组合)
 * 监听 MCP 双向请求流并渲染相应的确认与抉择弹窗
 */
@Composable
fun McpInteractionHost(
    bridge: McpInteractionBridge = McpInteractionBridge
) {
    var pendingConfirmation by remember { mutableStateOf<McpInteractionRequest.Confirmation?>(null) }
    var pendingChoice by remember { mutableStateOf<McpInteractionRequest.Choice?>(null) }
    var selectedChoiceIndex by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        bridge.setHostActive(true)
        onDispose {
            bridge.setHostActive(false)
        }
    }

    LaunchedEffect(Unit) {
        bridge.requests.collect { req ->
            when (req) {
                is McpInteractionRequest.Notification -> {
                    val toastType = when (req.type.uppercase()) {
                        "SUCCESS" -> ToastType.SUCCESS
                        "ERROR" -> ToastType.ERROR
                        else -> ToastType.INFO
                    }
                    Toast.show(req.message, toastType, req.durationMillis)
                }
                is McpInteractionRequest.Confirmation -> {
                    pendingConfirmation = req
                }
                is McpInteractionRequest.Choice -> {
                    pendingChoice = req
                    selectedChoiceIndex = null
                }
            }
        }
    }

    pendingConfirmation?.let { req ->
        AlertDialog(
            onDismissRequest = {
                coroutineScope.launch { bridge.completeConfirmation(req.id, false) }
                pendingConfirmation = null
            },
            modifier = Modifier.width(LocalAppSpacing.current.dialogConfirmWidth),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.mcp_interaction_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = req.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch { bridge.completeConfirmation(req.id, false) }
                            pendingConfirmation = null
                        },
                        modifier = Modifier.tip(stringResource(Res.string.mcp_confirm_default_deny))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.mcp_confirm_default_deny),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Text(
                    text = req.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch { bridge.completeConfirmation(req.id, true) }
                        pendingConfirmation = null
                    },
                    colors = if (req.isDestructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(req.confirmText.ifBlank { stringResource(Res.string.mcp_confirm_default_allow) })
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch { bridge.completeConfirmation(req.id, false) }
                        pendingConfirmation = null
                    }
                ) {
                    Text(req.dismissText.ifBlank { stringResource(Res.string.mcp_confirm_default_deny) })
                }
            }
        )
    }

    pendingChoice?.let { req ->
        AlertDialog(
            onDismissRequest = {
                if (req.allowCancel) {
                    coroutineScope.launch { bridge.completeChoice(req.id, null) }
                    pendingChoice = null
                }
            },
            modifier = Modifier.width(LocalAppSpacing.current.dialogConfigWidth),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.mcp_interaction_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = req.title.ifBlank { stringResource(Res.string.mcp_choice_title_default) },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (req.allowCancel) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch { bridge.completeChoice(req.id, null) }
                                pendingChoice = null
                            },
                            modifier = Modifier.tip(stringResource(Res.string.mcp_choice_cancel_btn))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.mcp_choice_cancel_btn),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (req.message.isNotBlank()) {
                        Text(
                            text = req.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(req.options) { index, option ->
                            val isSelected = selectedChoiceIndex == index
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedChoiceIndex = index }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedChoiceIndex = index }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = selectedChoiceIndex
                        if (chosen != null) {
                            coroutineScope.launch { bridge.completeChoice(req.id, chosen) }
                            pendingChoice = null
                        }
                    },
                    enabled = selectedChoiceIndex != null
                ) {
                    Text(stringResource(Res.string.mcp_choice_confirm_btn))
                }
            },
            dismissButton = if (req.allowCancel) {
                {
                    TextButton(
                        onClick = {
                            coroutineScope.launch { bridge.completeChoice(req.id, null) }
                            pendingChoice = null
                        }
                    ) {
                        Text(stringResource(Res.string.mcp_choice_cancel_btn))
                    }
                }
            } else null
        )
    }
}
