package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.readLocalFileBytes

/**
 * 设置图元专属生图参考底图工具
 */
class SetBlockReferenceImageTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "set_block_reference_image"

    override val description: String =
        "为指定图元设置专属的生图参考底图。支持两种来源方式：1. 直接绑定物理图片文件或 http/https 网络图片链接；2. 从页面参考大图中按指定区域 bounds 精准物理裁切并绑定为该模块的参考图。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "模板工程名称")
            })
            put("blockId", buildJsonObject {
                put("type", "string")
                put("description", "目标图元 ID")
            })
            put("sourceType", buildJsonObject {
                put("type", "string")
                put("description", "图片来源类型：'file' (本地物理文件或网络图片链接) 或 'pageArea' (从页面大图切片)")
            })
            put("filePath", buildJsonObject {
                put("type", "string")
                put("description", "当 sourceType 为 'file' 时必填：本地物理图片的绝对路径或 http:// / https:// 网络图片资源链接")
            })
            put("cropBounds", buildJsonObject {
                put("type", "object")
                put("description", "当 sourceType 为 'pageArea' 时必填：切片矩形坐标 { left, top, right, bottom }")
                put("properties", buildJsonObject {
                    put("left", buildJsonObject { put("type", "number") })
                    put("top", buildJsonObject { put("type", "number") })
                    put("right", buildJsonObject { put("type", "number") })
                    put("bottom", buildJsonObject { put("type", "number") })
                })
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("blockId")
            add("sourceType")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")
        val blockId = arguments["blockId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'blockId' 不能为空")
        val sourceType = arguments["sourceType"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "file"

        onProgress?.invoke(0.2f, "正在读取工程数据...")
        val templates = repository.getTemplates()
        val match = templates.firstOrNull { it.first.equals(projectName, ignoreCase = true) }
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        val page = state.pages.firstOrNull() ?: return McpToolResult.error("工程未包含页面")

        val targetBytes: ByteArray = when (sourceType) {
            "file", "url", "network" -> {
                val fPath = arguments["filePath"]?.jsonPrimitive?.contentOrNull
                    ?: arguments["url"]?.jsonPrimitive?.contentOrNull
                    ?: return McpToolResult.error("sourceType 为 'file' 时必须提供 'filePath'")
                onProgress?.invoke(0.3f, "正在读取/下载参考图片资源...")
                org.gemini.ui.forge.utils.fetchImageBytes(fPath) ?: return McpToolResult.error("未能获取参考图资源 (支持本地文件、http/https网络链接、Base64): $fPath")
            }
            "pagearea" -> {
                val pageSourceUri = page.sourceImageUri?.getAbsolutePath()
                    ?: return McpToolResult.error("当前页面未设置 sourceImageUri 参考原图")
                val pageBytes = readLocalFileBytes(pageSourceUri)
                    ?: return McpToolResult.error("未能读取页面原图: $pageSourceUri")

                val boundsObj = arguments["cropBounds"]?.let { it as? JsonObject }
                    ?: return McpToolResult.error("sourceType 为 'pageArea' 时必须提供 'cropBounds'")

                val left = boundsObj["left"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0
                val top = boundsObj["top"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0
                val right = boundsObj["right"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 100
                val bottom = boundsObj["bottom"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 100
                val width = (right - left).coerceAtLeast(1)
                val height = (bottom - top).coerceAtLeast(1)

                val rect = org.gemini.ui.forge.model.ui.SerialRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
                onProgress?.invoke(0.4f, "正在执行离线物理边缘梯度吸附与精准裁切 ($left, $top, $width, $height)...")
                val snappedBytes = org.gemini.ui.forge.utils.SmartEdgeSnapper.cropSnappedComponent(
                    imageBytes = pageBytes,
                    logicalBounds = rect,
                    canvasWidth = page.width,
                    canvasHeight = page.height
                )
                snappedBytes ?: cropImage(
                    imageBytes = pageBytes,
                    bounds = rect,
                    originalWidth = page.width,
                    originalHeight = page.height,
                    isPng = true
                ) ?: return McpToolResult.error("裁切图像失败")
            }
            else -> return McpToolResult.error("不支持的 sourceType: $sourceType (仅支持 'file' 或 'pageArea')")
        }

        onProgress?.invoke(0.6f, "正在保存物理切片资产...")
        val savedFile = repository.saveBlockResource(
            templateName = projectName,
            blockId = blockId,
            fileNamePrefix = "crop_ref",
            bytes = targetBytes,
            isPng = true
        )

        var isFound = false
        fun updateRefRecursively(blocks: List<UIBlock>): List<UIBlock> {
            return blocks.map { b ->
                if (b.id == blockId) {
                    isFound = true
                    b.copy(referenceImage = savedFile)
                } else {
                    b.copy(children = updateRefRecursively(b.children))
                }
            }
        }

        onProgress?.invoke(0.8f, "正在更新模块参考图引用...")
        val updatedPage = page.copy(blocks = updateRefRecursively(page.blocks))
        if (!isFound) {
            return McpToolResult.error("在模板 '$projectName' 中未找到 ID 为 '$blockId' 的图元")
        }

        repository.saveTemplate(projectName, state.copy(pages = listOf(updatedPage)))
        onProgress?.invoke(1.0f, "参考底图已绑定")

        val resultJson = buildJsonObject {
            put("success", true)
            put("blockId", blockId)
            put("referenceImageUri", savedFile.getAbsolutePath())
        }.toString()

        return McpToolResult.text(resultJson)
    }
}
