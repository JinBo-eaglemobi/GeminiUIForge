package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpCenter
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.ui.theme.AppShapes

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HelpCenter, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.help_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                // Content (Simplified Markdown View)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.verticalScroll(scrollState).padding(end = 8.dp)) {
                        HelpSection("1. 核心模式介绍", "本工具包含首页管理、布局编辑（UI 结构定义）及资源生成（AI 生图）三大核心模式。")
                        HelpItem("双击模块", "进入该组的隔离编辑模式，专注于局部微调。")
                        HelpItem("双击空白", "退出隔离模式，返回上级。")
                        HelpItem("拖拽与缩放", "支持画布自由平移、缩放以及模块坐标的精准修改。")
                        
                        HelpSection("2. AI 与环境配置", "生图功能依赖 Google Gemini API。本地抠图（背景移除）需要本地安装 Python 3.9+ 及其相关库。")
                        HelpItem("环境自检", "您可以在设置页面一键检测并自动安装缺失的 Python 依赖。")
                        HelpItem("视觉引导重塑", "支持通过框选参考图中的特定区域，由 AI 辅助识别并重塑组件。")

                        HelpSection("3. 常用快捷键", "熟练使用快捷键将大幅提升您的开发效率。")
                        HelpItem("Ctrl + Z / Y", "撤销与重做。")
                        HelpItem("Ctrl + S", "快速保存当前布局。")
                        HelpItem("Delete", "删除当前选中的模块。")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Footer Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { getPlatform().openInBrowser("https://github.com/JinBo-eaglemobi/GeminiUIForge/blob/main/HELP.md") },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.medium
                    ) {
                        Icon(Icons.Default.OpenInBrowser, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.help_action_open_browser))
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.medium
                    ) {
                        Text(stringResource(Res.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, desc: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    }
}

@Composable
private fun HelpItem(label: String, content: String) {
    Row(Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
        Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
