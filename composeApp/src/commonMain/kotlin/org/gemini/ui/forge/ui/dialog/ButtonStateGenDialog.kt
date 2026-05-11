package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.BorderStroke
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.gemini.ui.forge.state.TemplateAssetGenState
import org.gemini.ui.forge.viewmodel.TemplateAssetGenViewModel
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.model.ui.BlockProperties

@Composable
fun ButtonStateGenDialog(
    state: TemplateAssetGenState,
    viewModel: TemplateAssetGenViewModel,
    apiKey: String
) {
    if (!state.showButtonGenDialog) return

    Dialog(
        onDismissRequest = { if (!state.isButtonGenInProgress) viewModel.closeButtonGenDialog() },
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = !state.isButtonGenInProgress)
    ) {
        Surface(
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "配置按钮多态生成",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "按下态 (Pressed) 提示词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                SelectAllOutlinedTextField(
                    value = state.buttonPressedPrompt,
                    onValueChange = { viewModel.updateButtonGenPrompts(it, state.buttonDisabledPrompt) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    enabled = !state.isButtonGenInProgress
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "禁用态 (Disabled) 提示词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                SelectAllOutlinedTextField(
                    value = state.buttonDisabledPrompt,
                    onValueChange = { viewModel.updateButtonGenPrompts(state.buttonPressedPrompt, it) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    enabled = !state.isButtonGenInProgress
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (state.buttonPressedCandidate != null || state.buttonDisabledCandidate != null || (state.selectedBlock?.properties as? BlockProperties.ButtonProperties)?.isMultiState == true) {
                    Text("生成结果对比 (左:当前 / 右:新图)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    
                    val currentProps = state.selectedBlock?.properties as? BlockProperties.ButtonProperties

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Pressed State Section
                        Column(modifier = Modifier.weight(1f)) {
                            Text("按下态 (Pressed)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Current
                                PreviewCard(
                                    uri = currentProps?.pressedUri,
                                    label = "当前",
                                    modifier = Modifier.weight(1f)
                                )
                                // Candidate
                                PreviewCard(
                                    uri = state.buttonPressedCandidate,
                                    label = "新图",
                                    modifier = Modifier.weight(1f),
                                    isNew = true
                                )
                            }
                            if (state.buttonPressedCandidate != null || currentProps?.pressedUri != null) {
                                TextButton(
                                    onClick = { viewModel.executeButtonStateGen(apiKey, TemplateAssetGenViewModel.ButtonGenTarget.PRESSED) },
                                    enabled = !state.isButtonGenInProgress,
                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if(state.buttonPressedCandidate == null) "重新生成" else "再次尝试", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // Disabled State Section
                        Column(modifier = Modifier.weight(1f)) {
                            Text("禁用态 (Disabled)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Current
                                PreviewCard(
                                    uri = currentProps?.disabledUri,
                                    label = "当前",
                                    modifier = Modifier.weight(1f)
                                )
                                // Candidate
                                PreviewCard(
                                    uri = state.buttonDisabledCandidate,
                                    label = "新图",
                                    modifier = Modifier.weight(1f),
                                    isNew = true
                                )
                            }
                            if (state.buttonDisabledCandidate != null || currentProps?.disabledUri != null) {
                                TextButton(
                                    onClick = { viewModel.executeButtonStateGen(apiKey, TemplateAssetGenViewModel.ButtonGenTarget.DISABLED) },
                                    enabled = !state.isButtonGenInProgress,
                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if(state.buttonDisabledCandidate == null) "重新生成" else "再次尝试", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { viewModel.closeButtonGenDialog() },
                        enabled = !state.isButtonGenInProgress
                    ) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (state.buttonPressedCandidate != null || state.buttonDisabledCandidate != null) {
                        OutlinedButton(
                            onClick = { viewModel.executeButtonStateGen(apiKey, TemplateAssetGenViewModel.ButtonGenTarget.ALL) },
                            enabled = !state.isButtonGenInProgress
                        ) {
                            Text("全部重绘")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.confirmButtonStates() },
                            enabled = !state.isButtonGenInProgress
                        ) {
                            Text("确认应用")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.executeButtonStateGen(apiKey, TemplateAssetGenViewModel.ButtonGenTarget.ALL) },
                            enabled = !state.isButtonGenInProgress
                        ) {
                            if (state.isButtonGenInProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("开始生成")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewCard(
    uri: org.gemini.ui.forge.data.TemplateFile?,
    label: String,
    modifier: Modifier = Modifier,
    isNew: Boolean = false
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = AppShapes.small,
            colors = CardDefaults.cardColors(
                containerColor = if (isNew) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                else Color.Black.copy(alpha = 0.05f)
            ),
            border = if (isNew && uri != null) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (uri != null) {
                    AsyncImage(
                        model = uri.getAbsolutePath(),
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(if(isNew) "未生成" else "无", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if(isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
