package org.gemini.ui.forge.ui.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import org.gemini.ui.forge.model.app.UIModule
import org.gemini.ui.forge.ui.theme.AppShapes

@Composable
fun ModuleCard(module: UIModule, onEditLayout: () -> Unit, onGenerateUI: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.size(280.dp, 400.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val title = if (module.nameRes != null) stringResource(module.nameRes) else module.nameStr ?: "Unknown"

            val coverUrl = module.projectState?.pages?.firstOrNull()?.sourceImageUri
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover for $title",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No Preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val rawTitle = if (module.nameRes != null) stringResource(module.nameRes) else module.nameStr ?: "Unknown"
                val displayTitle = if (rawTitle.length > 20) rawTitle.take(20) + "..." else rawTitle

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )

                    val createdAt = module.projectState?.createdAt ?: 0L
                    if (createdAt > 0L) {
                        Text(
                            text = org.gemini.ui.forge.formatTimestamp(createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (module.absolutePath != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Template",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onEditLayout,
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                    shape = AppShapes.medium,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(stringResource(Res.string.action_edit_layout), maxLines = 1, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }

                Button(
                    onClick = onGenerateUI,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    shape = AppShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(stringResource(Res.string.action_generate_ui), maxLines = 1, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
