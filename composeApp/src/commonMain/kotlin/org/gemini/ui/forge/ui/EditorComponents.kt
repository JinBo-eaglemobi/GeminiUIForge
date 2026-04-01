package org.gemini.ui.forge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.utils.decodeBase64ToBitmap
import org.jetbrains.compose.resources.stringResource

@Composable
fun PropertyPanel(
    selectedBlock: UIBlock?,
    isGenerating: Boolean,
    onPromptChanged: (String) -> Unit,
    onGenerateRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (selectedBlock == null) {
                Text(stringResource(Res.string.select_block_to_edit), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = "${stringResource(Res.string.edit_block)} ${stringResource(selectedBlock.type.getDisplayNameRes())}", 
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = selectedBlock.userPrompt,
                    onValueChange = onPromptChanged,
                    label = { Text(stringResource(Res.string.style_prompt)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onGenerateRequested,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text(stringResource(Res.string.generate_ideas))
                    }
                }
            }
        }
    }
}

@Composable
fun CandidateGallery(
    candidates: List<String>,
    onImageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (candidates.isEmpty()) return
    
    Surface(
        modifier = modifier.fillMaxWidth().height(240.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(candidates) { base64Data ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(Color.Gray)
                        .clickable { onImageSelected(base64Data) },
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = base64Data.decodeBase64ToBitmap()
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(text = stringResource(Res.string.candidate), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}