package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.studio.NameConflictPolicy
import org.gemini.ui.forge.service.studio.StudioAssetManagerService
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Base64 图片受控落盘素材库专用工具（100% 复用公用服务 StudioAssetManagerService.saveImageToLibrary）
 */
class SaveImageToAssetLibraryTool(
    private val storage: LocalFileStorage = LocalFileStorage()
) : ToolDefinition {

    override val name: String = "save_image_to_asset_library"

    override val description: String =
        "将图片 Base64 数据受控持久化落盘至当前工程素材库中，并自动登记素材账本。支持指定子目录分类、语义化文件名以及自动离线去背透明化（PNG）处理。执行前必须先征得用户授权；执行后返回 assetHash 与全景装配方案规范指引。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = false
    )

    override val timeoutMs: Long = 60_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("base64Data", buildJsonObject {
                put("type", "string")
                put("description", "待落盘图片的 Base64 数据编码字符串（支持纯 Base64 或带 'data:image/...;base64,' 前缀）")
            })
            put("fileName", buildJsonObject {
                put("type", "string")
                put("description", "AI 根据图片在界面中的实际功能与作用自动命名的文件名（无需文件后缀，如 'bg_beach_soccer' 或 'btn_spin_normal'）")
            })
            put("folder", buildJsonObject {
                put("type", "string")
                put("description", "可选：素材库内部的子文件夹相对路径（例如 'backgrounds'、'icons/buttons'，留空默认存入 'assets/image/' 根目录）")
            })
            put("isTransparent", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否要求为透明图片，默认为 false。若为 true，系统将自动调用本地离线去背算法剔除背景并强制存为 PNG；若为 false，默认保存为高质量 JPG。")
            })
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "可选：目标项目工程名称。若未指定将自动读取当前打开的活跃工作区工程")
            })
        })
        put("required", buildJsonArray {
            add("base64Data")
            add("fileName")
        })
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val rawBase64 = arguments["base64Data"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'base64Data' 不能为空")
        val fileName = arguments["fileName"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.error("参数 'fileName' 不能为空")

        val folder = arguments["folder"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
        val isTransparent = arguments["isTransparent"]?.jsonPrimitive?.booleanOrNull ?: false

        val explicitProject = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.trim()
        val activeProject = McpUiActionPipeline.getActiveUiStudioViewModel()?.state?.project?.projectId?.takeIf { it.isNotBlank() }
        val projectName = explicitProject?.takeIf { it.isNotBlank() } ?: activeProject ?: "_common"

        onProgress?.invoke(0.1f, "正在解析图片数据...")
        val cleanBase64 = if (rawBase64.contains(",")) rawBase64.substringAfter(",") else rawBase64
        val rawBytes = try {
            Base64.decode(cleanBase64.trim())
        } catch (e: Exception) {
            return ToolResult.error("Base64 数据解码失败: ${e.message}")
        }

        val saveRes = StudioAssetManagerService.saveImageToLibrary(
            projectName = projectName,
            imageBytes = rawBytes,
            fileName = fileName,
            folderPath = folder,
            isTransparent = isTransparent,
            autoRemoveBg = isTransparent,
            conflictPolicy = NameConflictPolicy.AUTO_RENAME,
            storage = storage,
            syncActiveWorkspace = true,
            showToast = false,
            onProgress = onProgress
        )

        return saveRes.fold(
            onSuccess = { assetItem ->
                val diskAbsPath = storage.getFilePath(assetItem.relativePath)
                val resultJson = buildJsonObject {
                    put("success", true)
                    put("assetHash", assetItem.hash)
                    put("assetFileName", assetItem.relativePath.substringAfterLast('/'))
                    put("relativePath", assetItem.relativePath)
                    put("fullPath", diskAbsPath)
                    put("width", assetItem.width)
                    put("height", assetItem.height)
                    put("isTransparent", isTransparent)
                    put(
                        "message",
                        "✅ 图片已成功落盘至素材库并登记资产 Hash: ${assetItem.hash}。\n" +
                            "【⚠️ 严格操作门禁】：本步骤仅完成了素材入库。若需要将该素材装配应用到界面画布，严禁擅自直接调用 edit_view_layout！" +
                            "你必须首先基于当前图元树进行人机工程学与层级分析，向用户输出完整的【装配方案卡片】，并在获得用户明确同意后方可执行装配！"
                    )
                }
                ToolResult.text(resultJson.toString())
            },
            onFailure = { err ->
                ToolResult.error(err.message ?: "保存图片至素材库失败")
            }
        )
    }
}
