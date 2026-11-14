package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 将当前或指定工程的全部视图页面、图元布局与项目配置原子持久化落盘至本地磁盘的工具。
 *
 * 核心特性：
 * 1. 自动适配 UI Studio 工作台与传统 Slots 模板工程；
 * 2. 具备全工程深度原子刷盘（写入全部页面 JSON、template.json 与工作区布局缓存）；
 * 3. 保存完成后主动清空宿主 isDirty 脏标记，杜绝软件关闭后修改丢失。
 */
class SaveProjectTool(
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "save_project"

    override val description: String =
        "将当前活跃的 UI 工程（包括全部视图页面、图元布局树、引用拓扑与工作区状态）立即完整原子持久化保存到本地磁盘。在执行视图排版 (edit_view_layout)、图元微调或批量修改后，必须显式调用此工具确保所有修改 100% 落盘防丢失。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "可选：目标工程项目唯一名称。省略时自动保存当前工作区打开的活跃工程（推荐）。")
            })
            put("reason", buildJsonObject {
                put("type", "string")
                put("description", "可选：执行保存的原因或修改摘要（例如 '完成底部操作栏按钮排版微调'）。")
            })
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val targetProjectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val reason = arguments["reason"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val startTime = getCurrentTimeMillis()

        onProgress?.invoke(0.2f, "正在定位目标工程并准备持久化...")

        // 1. 优先尝试从活跃 UI Studio 宿主中提取完整运行时状态
        val studioVm = McpUiActionPipeline.getActiveUiStudioViewModel()
        if (studioVm != null) {
            val st = studioVm.state
            val proj = st.project
            if (proj != null && (targetProjectName == null || proj.projectId.equals(targetProjectName, ignoreCase = true))) {
                onProgress?.invoke(0.5f, "正在原子落盘 UI Studio 全部独立页面与项目公用配置...")
                try {
                    repository.saveTemplate(proj.projectId, proj)
                    studioVm.viewport.saveViewportCache(proj.projectId)
                    studioVm.viewport.saveWorkspaceLayout(proj.projectId)
                    // 清空脏状态标记
                    studioVm.saveProject(repository)

                    val elapsed = getCurrentTimeMillis() - startTime
                    val pageCount = proj.studioPages.size
                    val pageNames = proj.studioPages.joinToString(", ") { "${it.nameStr}(${it.id})" }

                    val summary = buildString {
                        append("✅ 工程 '${proj.projectId}' 已成功原子持久化落盘至本地磁盘！\n")
                        append("• 保存页面总数: $pageCount 个 [ $pageNames ]\n")
                        append("• 公用索引文件: template.json 已同步更新\n")
                        append("• 工作区状态: 已重置为已保存 (isDirty = false)\n")
                        if (!reason.isNullOrBlank()) {
                            append("• 保存原因: $reason\n")
                        }
                        append("• 磁盘保存耗时: ${elapsed}ms")
                    }

                    return ToolResult.success(summary)
                } catch (e: Exception) {
                    return ToolResult.error("保存 UI Studio 工程 '${proj.projectId}' 失败: ${e.message}")
                }
            }
        }

        // 2. 尝试从传统 Slots 模板工程中提取完整运行时状态
        val workspaceVm = McpUiActionPipeline.getActiveViewModel()
        if (workspaceVm != null) {
            val st = workspaceVm.state.value
            val proj = st.project
            if (proj != null && (targetProjectName == null || st.projectName.equals(targetProjectName, ignoreCase = true))) {
                onProgress?.invoke(0.5f, "正在原子落盘传统 Slots 模板工程配置...")
                try {
                    repository.saveTemplate(st.projectName, proj)
                    val elapsed = getCurrentTimeMillis() - startTime
                    val summary = "✅ 传统 Slots 工程 '${st.projectName}' 已成功保存至磁盘 (耗时: ${elapsed}ms)"
                    return ToolResult.success(summary)
                } catch (e: Exception) {
                    return ToolResult.error("保存传统 Slots 工程 '${st.projectName}' 失败: ${e.message}")
                }
            }
        }

        // 3. 离线或指定特定工程名保存
        if (!targetProjectName.isNullOrBlank()) {
            onProgress?.invoke(0.5f, "正在从本地仓储读取并二次刷盘目标工程 '$targetProjectName'...")
            try {
                val proj = repository.getTemplateByName(targetProjectName)
                    ?: return ToolResult.error("未在本地磁盘找到名为 '$targetProjectName' 的工程")
                repository.saveTemplate(targetProjectName, proj)
                val elapsed = getCurrentTimeMillis() - startTime
                return ToolResult.success("✅ 磁盘工程 '$targetProjectName' 已成功原子二次刷盘 (耗时: ${elapsed}ms)")
            } catch (e: Exception) {
                return ToolResult.error("落盘磁盘工程 '$targetProjectName' 失败: ${e.message}")
            }
        }

        return ToolResult.error("无法保存工程：当前工作区未打开任何活跃工程，且未传入 'projectName' 参数。")
    }
}
