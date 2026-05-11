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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettings(
    currentApiKey: String,
    currentMaxRetries: Int,
    currentImageGenCount: Int,
    currentPromptLang: PromptLanguage,
    onApiKeySaved: (String) -> Unit,
    onMaxRetriesSaved: (Int) -> Unit,
    onImageGenCountSaved: (Int) -> Unit,
    onPromptLangSelected: (PromptLanguage) -> Unit
) {
    val isCompact = LocalMinimumInteractiveComponentSize.current == 0.dp

    SettingSectionTitle(stringResource(Res.string.settings_category_ai))

    var keyInput by remember { mutableStateOf(currentApiKey) }
    SelectAllOutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it; onApiKeySaved(it) },
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
            value = currentMaxRetries.toString(),
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
                    onClick = { onMaxRetriesSaved(count); retriesExpanded = false }
                )
            }
        }
    }

    var genCountExpanded by remember { mutableStateOf(false) }
    val genCountOptions = listOf(1, 2, 4, 8, 12, 16)

    ExposedDropdownMenuBox(expanded = genCountExpanded, onExpandedChange = { genCountExpanded = !genCountExpanded }) {
        SelectAllOutlinedTextField(
            value = currentImageGenCount.toString(),
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
                    onClick = { onImageGenCountSaved(count); genCountExpanded = false }
                )
            }
        }
    }

    var promptLangExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = promptLangExpanded,
        onExpandedChange = { promptLangExpanded = !promptLangExpanded }) {
        SelectAllOutlinedTextField(
            value = currentPromptLang.displayName,
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
                    onClick = { onPromptLangSelected(lang); promptLangExpanded = false }
                , contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else MenuDefaults.DropdownMenuItemContentPadding, modifier = if (isCompact) Modifier.height(32.dp) else Modifier)
            }
        }
    }
}