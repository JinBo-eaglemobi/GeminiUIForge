package org.gemini.ui.forge.ui.dialog.settings


import androidx.compose.foundation.layout.PaddingValues
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.ui.theme.AppShapes


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
    val isCompact = LocalMinimumInteractiveComponentSize.current == 0.dp

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
                , contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else MenuDefaults.DropdownMenuItemContentPadding, modifier = if (isCompact) Modifier.height(32.dp) else Modifier)
            }
        }
    }
}