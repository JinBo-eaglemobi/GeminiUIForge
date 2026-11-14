package org.gemini.ui.forge.service.tool.shared

import androidx.compose.runtime.getValue
import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.asset.AssetFileNameGuard
import org.gemini.ui.forge.utils.calculateShortFileHash
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Base64 图片受控落盘素材库专用工具
 *
 * 【双重限制防御体系 - 工具层刚性约束】：
 * 1. 专职且仅负责将 Base64 数据写入项目素材库 (templates/{proj}/assets/image/...) 并自动登记资产账本；
 * 2. 彻底剥离任何画布修改与图元装配旁路（严禁直接修改当前页面 UIBlock）；
 * 3. 工具执行成功后，返回不可变的 assetHash，并返回【全景方案先行】的红线锚定提示，
 *    严格阻断 AI 未经二次确认擅自调用 edit_view_layout。
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

        val folder = arguments["folder"]?.jsonPrimitive?.contentOrNull?.trim()?.trim('/', '\\') ?: ""
        val isTransparent = arguments["isTransparent"]?.jsonPrimitive?.booleanOrNull ?: false

        val explicitProject = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.trim()
        val activeVm = McpUiActionPipeline.getActiveUiStudioViewModel()
        val activeProject = activeVm?.state?.project?.projectId?.takeIf { it.isNotBlank() }
        val projectName = explicitProject?.takeIf { it.isNotBlank() } ?: activeProject ?: "_common"

        onProgress?.invoke(0.1f, "正在解析图片数据...")
        val cleanBase64 = if (rawBase64.contains(",")) {
            rawBase64.substringAfter(",")
        } else {
            rawBase64
        }.trim()

        val rawBytes = try {
            Base64.decode(cleanBase64)
        } catch (e: Exception) {
            return ToolResult.error("Base64 数据解码失败: ${e.message}")
        }

        if (rawBytes.isEmpty()) {
            return ToolResult.error("解码后的图片数据为空")
        }

        // 1. 若要求透明，调用本地离线去背服务
        val processedBytes = if (isTransparent) {
            onProgress?.invoke(0.3f, "正在调用本地 Python 抠图引擎进行背景去除...")
            val mattingService = LocalMattingService(storage)
            val noBg = mattingService.removeBackground(rawBytes) { log ->
                onProgress?.invoke(0.5f, log)
            }
            if (noBg != null && noBg.isNotEmpty()) {
                noBg
            } else {
                AppLogger.w("SaveImageToAssetLibraryTool", "⚠️ 本地去背未能成功生成，保留原始字节流并存为 PNG")
                rawBytes
            }
        } else {
            rawBytes
        }

        onProgress?.invoke(0.7f, "正在持久化落盘到素材库...")
        val ext = if (isTransparent) "png" else "jpg"
        val cleanName = if (fileName.endsWith(".$ext", ignoreCase = true)) fileName else "$fileName.$ext"
        val targetRelDir = if (folder.isBlank()) {
            "templates/$projectName/assets/image"
        } else {
            "templates/$projectName/assets/image/$folder"
        }
        val targetFullPath = "$targetRelDir/$cleanName"

        // 2. 防重名检测与保存
        val targetDir = targetFullPath.substringBeforeLast('/')
        val targetFile = targetFullPath.substringAfterLast('/')
        val effectivePath = if (AssetFileNameGuard.isFileNameTakenInDir(storage, targetDir, targetFile)) {
            val baseWithoutExt = cleanName.substringBeforeLast('.')
            val uniqueName = "${baseWithoutExt}_${org.gemini.ui.forge.getCurrentTimeMillis()}.$ext"
            "$targetDir/$uniqueName"
        } else {
            targetFullPath
        }

        val savedPath = try {
            storage.saveBytesToFile(effectivePath, processedBytes)
        } catch (e: Exception) {
            return ToolResult.error("保存图片至磁盘失败: ${e.message}")
        } ?: return ToolResult.error("存储服务返回空路径")

        // 3. 计算图片规格与短哈希
        val (width, height) = try {
            val img = Image.makeFromEncoded(processedBytes)
            img.width to img.height
        } catch (_: Exception) {
            100 to 100
        }
        val assetHash = calculateShortFileHash(effectivePath) ?: effectivePath.hashCode().toString(16)

        // 4. 同步登记并刷新 UI Studio 素材库
        if (activeVm != null && projectName == activeProject) {
            try {
                activeVm.assetLibrary.refreshAssetLibrary(focusAssetPath = effectivePath)
            } catch (e: Throwable) {
                AppLogger.w("SaveImageToAssetLibraryTool", "同步刷新工作区素材库轻量警告", e)
            }
        }

        onProgress?.invoke(1.0f, "素材入库完成")
        val diskAbsPath = storage.getFilePath(effectivePath)

        val resultJson = buildJsonObject {
            put("success", true)
            put("assetHash", assetHash)
            put("assetFileName", effectivePath.substringAfterLast('/'))
            put("relativePath", effectivePath)
            put("fullPath", diskAbsPath)
            put("width", width)
            put("height", height)
            put("isTransparent", isTransparent)
            put(
                "message",
                "✅ 图片已成功落盘至素材库并登记资产 Hash: $assetHash。\n" +
                "【⚠️ 严格操作门禁】：本步骤仅完成了素材入库。若需要将该素材装配应用到界面画布，严禁擅自直接调用 edit_view_layout！" +
                "你必须首先基于当前图元树进行人机工程学与层级分析，向用户输出完整的【装配方案卡片】（说明是替换现有图元还是新建图元、所属父容器、层级深度、目标尺寸与语义），" +
                "并在获得用户明确同意后方可执行装配！"
            )
        }

        return ToolResult.text(resultJson.toString())
    }
}
