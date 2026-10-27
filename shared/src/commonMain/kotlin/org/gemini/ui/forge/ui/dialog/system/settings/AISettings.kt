package org.gemini.ui.forge.ui.dialog.system.settings


import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.model.StudioModelResolver
import org.gemini.ui.forge.model.ModelStudioGroup
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing


/**
 * AI 相关设置区块
 *
 * 提供 Gemini API Key、最大重试次数、单次图片生成数量以及提示词语言偏好的配置选项。
 *
 * @param globalState 全局状态管理对象，包含各项底层设置的当前状态
 * @param appViewModel 全局 App 视图模型，负责将更新后的 AI 状态传递到其他组件
 * @param settingsViewModel 设置相关的业务逻辑视图模型，控制 AI 密钥、重试次数和生图参数的保存
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettings(
    globalState: org.gemini.ui.forge.state.app.AppGlobalState,
    appViewModel: org.gemini.ui.forge.viewmodel.AppViewModel,
    settingsViewModel: org.gemini.ui.forge.viewmodel.AppSettingsViewModel
) {
    SettingSectionTitle(stringResource(Res.string.settings_category_ai))

    var keyInput by remember(globalState.apiKey) { mutableStateOf(globalState.apiKey) }
    SelectAllOutlinedTextField(
        value = keyInput,
        onValueChange = {
            keyInput = it
            settingsViewModel.saveApiKey(it)
            appViewModel.updateApiKey(it)
        },
        label = { Text(stringResource(Res.string.settings_gemini_key_title)) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        visualTransformation = PasswordVisualTransformation(),
        placeholder = { Text(stringResource(Res.string.settings_gemini_key_placeholder)) },
        shape = AppShapes.medium
    )

    var retriesExpanded by remember { mutableStateOf(false) }
    val retryOptions = listOf(0, 1, 2, 3, 5, 10)

    ExposedDropdownMenuBox(expanded = retriesExpanded, onExpandedChange = { retriesExpanded = !retriesExpanded }) {
        SelectAllOutlinedTextField(
            value = globalState.maxRetries.toString(),
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_max_retries_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(retriesExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = retriesExpanded, onDismissRequest = { retriesExpanded = false }) {
            retryOptions.forEach { count ->
                DropdownMenuItem(
                    text = { Text(count.toString()) },
                    onClick = {
                        settingsViewModel.saveMaxRetries(count)
                        appViewModel.updateMaxRetriesState(count)
                        retriesExpanded = false
                    }
                )
            }
        }
    }

    var genCountExpanded by remember { mutableStateOf(false) }
    val genCountOptions = listOf(1, 2, 4, 8, 12, 16)

    ExposedDropdownMenuBox(expanded = genCountExpanded, onExpandedChange = { genCountExpanded = !genCountExpanded }) {
        SelectAllOutlinedTextField(
            value = globalState.imageGenCount.toString(),
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_image_gen_count_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genCountExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = genCountExpanded, onDismissRequest = { genCountExpanded = false }) {
            genCountOptions.forEach { count ->
                DropdownMenuItem(
                    text = { Text(count.toString()) },
                    onClick = {
                        settingsViewModel.saveImageGenCount(count)
                        appViewModel.updateImageGenCountState(count)
                        genCountExpanded = false
                    }
                )
            }
        }
    }

    var promptLangExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = promptLangExpanded,
        onExpandedChange = { promptLangExpanded = !promptLangExpanded }) {
        SelectAllOutlinedTextField(
            value = globalState.promptLangPref.displayName,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_prompt_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(promptLangExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = promptLangExpanded, onDismissRequest = { promptLangExpanded = false }) {
            PromptLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.displayName) },
                    onClick = {
                        settingsViewModel.savePromptLanguagePref(lang)
                        appViewModel.setPromptLanguagePref(lang)
                        promptLangExpanded = false
                    }
                )
            }
        }
    }

    var apiFlavorExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = apiFlavorExpanded,
        onExpandedChange = { apiFlavorExpanded = !apiFlavorExpanded }
    ) {
        val isZh = globalState.languageCode.startsWith("zh")
        val currentFlavorLabel = if (isZh) globalState.apiFlavor.displayNameZh else globalState.apiFlavor.displayNameEn
        SelectAllOutlinedTextField(
            value = currentFlavorLabel,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_api_flavor_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(apiFlavorExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = apiFlavorExpanded, onDismissRequest = { apiFlavorExpanded = false }) {
            ApiFlavor.entries.forEach { flavor ->
                val label = if (isZh) flavor.displayNameZh else flavor.displayNameEn
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        settingsViewModel.saveApiFlavor(flavor)
                        appViewModel.updateApiFlavorState(flavor)
                        apiFlavorExpanded = false
                    }
                )
            }
        }
    }

    var defaultImageModelExpanded by remember { mutableStateOf(false) }
    val specializedImageModels = remember {
        StudioModelResolver.resolveStudioModels()
            .filter { it.group == ModelStudioGroup.IMAGE_SPECIALIZED }
    }
    val autoLabel = stringResource(Res.string.settings_default_image_gen_model_auto)
    val spacing = LocalAppSpacing.current
    val isAutoSwitchEnabled = globalState.isAutoSwitchImageGenModelEnabled

    // 生图意图前端自动切换开关
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = AppShapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = spacing.medium)) {
                Text(
                    text = stringResource(Res.string.settings_auto_switch_image_gen_model_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.settings_auto_switch_image_gen_model_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isAutoSwitchEnabled,
                onCheckedChange = { checked ->
                    settingsViewModel.saveAutoSwitchImageGenModel(checked)
                    appViewModel.updateAutoSwitchImageGenModel(checked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }

    val currentImageModelLabel = if (globalState.defaultImageGenModel.isNullOrBlank()) {
        autoLabel
    } else {
        specializedImageModels.firstOrNull { it.model.modelName == globalState.defaultImageGenModel }?.let {
            "${it.model.displayName} (${it.model.modelName})"
        } ?: globalState.defaultImageGenModel
    }

    ExposedDropdownMenuBox(
        expanded = defaultImageModelExpanded,
        onExpandedChange = { defaultImageModelExpanded = !defaultImageModelExpanded }
    ) {
        SelectAllOutlinedTextField(
            value = currentImageModelLabel,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_default_image_gen_model_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(defaultImageModelExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = defaultImageModelExpanded, onDismissRequest = { defaultImageModelExpanded = false }) {
            DropdownMenuItem(
                text = { Text(autoLabel) },
                onClick = {
                    settingsViewModel.saveDefaultImageGenModel(null)
                    appViewModel.updateDefaultImageGenModel(null)
                    defaultImageModelExpanded = false
                }
            )
            specializedImageModels.forEach { meta ->
                DropdownMenuItem(
                    text = { Text("${meta.model.displayName} (${meta.model.modelName})") },
                    onClick = {
                        settingsViewModel.saveDefaultImageGenModel(meta.model.modelName)
                        appViewModel.updateDefaultImageGenModel(meta.model.modelName)
                        defaultImageModelExpanded = false
                    }
                )
            }
        }
    }

    var maxToolRounds by remember { mutableStateOf(50) }
    var maxToolRoundsInput by remember { mutableStateOf("50") }
    LaunchedEffect(Unit) {
        val loaded = settingsViewModel.getAiAgentMaxToolRounds()
        maxToolRounds = loaded
        maxToolRoundsInput = loaded.toString()
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = spacing.medium)) {
                    Text(
                        text = "AI Agent 最大工具轮询次数",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "单次最大探索与排版推理循环次数（出厂默认 50 次），达到上限时将弹窗询问是否继续",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SelectAllOutlinedTextField(
                    value = maxToolRoundsInput,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() }
                        maxToolRoundsInput = clean
                        clean.toIntOrNull()?.let { num ->
                            if (num in 5..500) {
                                maxToolRounds = num
                                settingsViewModel.saveAiAgentMaxToolRounds(num)
                            }
                        }
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    trailingIcon = {
                        Text(
                            text = "轮",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            }
        }
    }
}