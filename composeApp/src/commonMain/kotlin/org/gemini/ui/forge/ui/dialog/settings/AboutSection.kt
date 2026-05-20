package org.gemini.ui.forge.ui.dialog.settings


import androidx.compose.foundation.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ProjectConfig
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.ui.theme.AppShapes
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator


/**
 * “关于”设置区块
 *
 * 展示应用图标、名称、当前版本号、版权信息以及更新检测界面。
 * 支持手动检测更新和下载更新。
 *
 * @param updateStatus 当前更新状态（空闲、检查中、有可用更新、下载中等）
 * @param onCheckUpdate 触发检查更新的回调
 * @param onStartUpdate 触发开始下载更新的回调
 */
@Composable
fun AboutSection(
    updateStatus: UpdateStatus,
    onCheckUpdate: () -> Unit,
    onStartUpdate: (UpdateInfo) -> Unit
) {

    SettingSectionTitle(stringResource(Res.string.settings_category_about))

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                null,
                modifier = Modifier.size(LocalAppSpacing.current.extraLarge),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        // 获取应用名称和系统版本
        val appName = stringResource(Res.string.app_name)
        val currentProjectVersion = ProjectConfig.VERSION

        Text(
            appName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(Res.string.about_version, currentProjectVersion),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(LocalAppSpacing.current.medium))

        // 更新检测区域
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = AppShapes.medium
        ) {
            Column(
                Modifier.padding(LocalAppSpacing.current.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (updateStatus) {
                    is UpdateStatus.Idle -> {
                        Button(onClick = onCheckUpdate, shape = AppShapes.medium) {
                            Icon(Icons.Default.SystemUpdateAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(LocalAppSpacing.current.small))
                            Text(stringResource(Res.string.update_check_action))
                        }
                    }

                    is UpdateStatus.Checking -> {
                        CircularProgressIndicator(Modifier.size(LocalAppSpacing.current.large))
                        Text(
                            stringResource(Res.string.update_checking),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = LocalAppSpacing.current.small)
                        )
                    }

                    is UpdateStatus.Available -> {
                        Text(
                            stringResource(Res.string.update_available_title, updateStatus.info.version),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            updateStatus.info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = LocalAppSpacing.current.small)
                        )
                        Button(onClick = { onStartUpdate(updateStatus.info) }, shape = AppShapes.medium) {
                            Text(stringResource(Res.string.update_action_now))
                        }
                    }

                    is UpdateStatus.Downloading -> {
                        LinearProgressIndicator(
                            progress = { updateStatus.progress },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .height(LocalAppSpacing.current.small).clip(CircleShape)
                        )
                        Text(
                            stringResource(Res.string.update_downloading, (updateStatus.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = LocalAppSpacing.current.small)
                        )
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        CircularProgressIndicator(
                            Modifier.size(LocalAppSpacing.current.large),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(Res.string.update_preparing),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = LocalAppSpacing.current.small)
                        )
                    }

                    is UpdateStatus.UpToDate -> {
                        Text(
                            stringResource(Res.string.update_latest),
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = onCheckUpdate,
                            modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall)
                        ) {
                            Text(
                                stringResource(Res.string.update_check_action),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    is UpdateStatus.Error -> {
                        Text(updateStatus.message, color = MaterialTheme.colorScheme.error)
                        Button(
                            onClick = onCheckUpdate,
                            modifier = Modifier.padding(top = LocalAppSpacing.current.small),
                            shape = AppShapes.medium
                        ) { Text(stringResource(Res.string.update_check_action)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(LocalAppSpacing.current.medium))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = AppShapes.medium
        ) {
            Text(
                text = stringResource(Res.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(LocalAppSpacing.current.medium))
        Text(
            stringResource(Res.string.about_copyright),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}