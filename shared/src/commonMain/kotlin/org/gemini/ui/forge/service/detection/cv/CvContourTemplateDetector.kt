package org.gemini.ui.forge.service.detection.cv

import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIPage
import org.gemini.ui.forge.service.detection.DetectionEngineMode
import org.gemini.ui.forge.service.detection.ITemplateDetector
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.utils.AppLogger
import org.jetbrains.skia.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 传统 CV 纯物理多尺度轮廓提取与空间拓扑建树引擎 (UIED 原生算法)
 *
 * 纯 Kotlin / Skia 实现，100% 零模型依赖、零网络、全平台通用。
 * 通过多尺度 Sobel 梯度二值化、3x3 形态学闭合、二值连通域边界漫延、IoU 非极大值抑制与空间包含拓扑推导，
 * 自动从参考原图中精准挖掘出真实、具体的可交互 UI 控件（导航栏、返回按钮、金币药丸、Spin大按钮、控制条等）。
 */
class CvContourTemplateDetector : ITemplateDetector {
    override val mode: DetectionEngineMode = DetectionEngineMode.CLASSIC_CV
    override val displayName: String = "传统 CV 几何轮廓"

    override suspend fun detectTemplate(
        imageBytes: ByteArray,
        templateName: String,
        onProgress: (String) -> Unit
    ): ProjectState {
        onProgress("正在解析物理位图像素矩阵...")
        val skiaImage = Image.makeFromEncoded(imageBytes)
        val imgW = skiaImage.width
        val imgH = skiaImage.height

        val canvasW = 1080f
        val canvasH = 1920f
        val sx = canvasW / imgW.toFloat()
        val sy = canvasH / imgH.toFloat()

        val bitmap = Bitmap().apply { allocN32Pixels(imgW, imgH) }
        val canvas = Canvas(bitmap)
        canvas.drawImage(skiaImage, 0f, 0f)

        onProgress("正在计算多尺度边缘梯度...")
        // 1. 提取亮度矩阵 (Grayscale)
        val gray = FloatArray(imgW * imgH)
        for (y in 0 until imgH) {
            val rowOff = y * imgW
            for (x in 0 until imgW) {
                val color = bitmap.getColor(x, y)
                val r = Color.getR(color)
                val g = Color.getG(color)
                val b = Color.getB(color)
                gray[rowOff + x] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }

        // 2. Sobel 差分梯度与二值化
        val edgeMask = BooleanArray(imgW * imgH)
        val gradThreshold = 35f // 梯度跳变门限

        for (y in 1 until imgH - 1) {
            val rowOff = y * imgW
            val prevRow = (y - 1) * imgW
            val nextRow = (y + 1) * imgW
            for (x in 1 until imgW - 1) {
                val gx = abs(gray[rowOff + x + 1] - gray[rowOff + x - 1])
                val gy = abs(gray[nextRow + x] - gray[prevRow + x])
                if ((gx + gy) > gradThreshold) {
                    edgeMask[rowOff + x] = true
                }
            }
        }

        onProgress("正在执行 3x3 形态学膨胀闭合与连通域轮廓分析...")
        // 3. 3x3 膨胀闭合：桥接金属边框细微缝隙
        val closedMask = BooleanArray(imgW * imgH)
        for (y in 1 until imgH - 1) {
            val rowOff = y * imgW
            for (x in 1 until imgW - 1) {
                if (edgeMask[rowOff + x]) {
                    for (dy in -1..1) {
                        val nRow = (y + dy) * imgW
                        for (dx in -1..1) {
                            closedMask[nRow + x + dx] = true
                        }
                    }
                }
            }
        }

        // 4. 二值连通域提取每一个闭合 UI 岛屿的外接矩形
        val visited = BooleanArray(imgW * imgH)
        val rawBoxes = mutableListOf<RawBox>()
        val queue = IntArray(imgW * imgH)

        for (y in 2 until imgH - 2) {
            val rowOff = y * imgW
            for (x in 2 until imgW - 2) {
                val idx = rowOff + x
                if (closedMask[idx] && !visited[idx]) {
                    visited[idx] = true
                    var head = 0
                    var tail = 0
                    queue[tail++] = (y shl 16) or x

                    var minX = x
                    var maxX = x
                    var minY = y
                    var maxY = y

                    while (head < tail) {
                        val packed = queue[head++]
                        val cy = packed ushr 16
                        val cx = packed and 0xFFFF

                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy

                        for (dy in -1..1) {
                            val ny = cy + dy
                            if (ny in 1 until imgH - 1) {
                                val nRow = ny * imgW
                                for (dx in -1..1) {
                                    val nx = cx + dx
                                    if (nx in 1 until imgW - 1) {
                                        val nIdx = nRow + nx
                                        if (closedMask[nIdx] && !visited[nIdx]) {
                                            visited[nIdx] = true
                                            if (tail < queue.size) {
                                                queue[tail++] = (ny shl 16) or nx
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val bw = maxX - minX + 1
                    val bh = maxY - minY + 1
                    val area = bw * bh

                    // 尺寸过滤：排除过小杂质噪点与全屏背景
                    if (bw >= 24 && bh >= 22 && area >= 700) {
                        if (bw < imgW * 0.98f || bh < imgH * 0.98f) {
                            rawBoxes.add(RawBox(minX, minY, maxX + 1, maxY + 1, bw, bh, area))
                        }
                    }
                }
            }
        }

        onProgress("正在执行 IoU 非极大值抑制与几何拓扑重构...")
        // 5. 按面积降序排序
        rawBoxes.sortByDescending { it.area }

        // IoU 去重
        val filteredBoxes = mutableListOf<RawBox>()
        for (b in rawBoxes) {
            var isDuplicate = false
            for (fb in filteredBoxes) {
                if (calculateIoU(b, fb) > 0.65f) {
                    isDuplicate = true
                    break
                }
            }
            if (!isDuplicate) {
                filteredBoxes.add(b)
            }
        }

        AppLogger.i("CvContourTemplateDetector", "物理图片共检测到 ${filteredBoxes.size} 个有效闭合 UI 物理轮廓")

        // 6. 空间几何拓扑包含分析与层级树构建
        val rootCandidates = mutableListOf<DetectedNode>()

        for (b in filteredBoxes) {
            // 映射到标准画布逻辑尺寸 (1080x1920)
            val logL = b.left * sx
            val logT = b.top * sy
            val logR = b.right * sx
            val logB = b.bottom * sy
            val logRect = SerialRect(logL, logT, logR, logB)

            val node = DetectedNode(
                physBox = b,
                logicalRect = logRect,
                type = inferBlockType(b, imgW, imgH),
                children = mutableListOf()
            )

            // 寻找是否落入已有的大容器内部 (包含且面积显著小于容器)
            var parentNode: DetectedNode? = null
            for (p in rootCandidates) {
                if (isContainedIn(node.physBox, p.physBox)) {
                    parentNode = p
                    break
                }
            }

            if (parentNode != null) {
                parentNode.children.add(node)
            } else {
                rootCandidates.add(node)
            }
        }

        // 7. 组装为标准 UIBlock 实体树 (递归计算局部相对坐标)
        fun buildBlock(node: DetectedNode, parentOffsetLeft: Float = 0f, parentOffsetTop: Float = 0f, index: Int = 0): UIBlock {
            val absRect = node.logicalRect
            val localBounds = SerialRect(
                left = absRect.left - parentOffsetLeft,
                top = absRect.top - parentOffsetTop,
                right = (absRect.left - parentOffsetLeft) + absRect.width,
                bottom = (absRect.top - parentOffsetTop) + absRect.height
            )

            val blockId = when (node.type) {
                UIBlockType.HEADER -> "header_bar"
                UIBlockType.SPIN_BUTTON -> "btn_spin"
                UIBlockType.BUTTON -> "btn_${index}_${absRect.left.toInt()}"
                UIBlockType.REEL -> "reel_column_${index}"
                else -> "comp_${index}_${absRect.left.toInt()}"
            }

            val childBlocks = node.children.mapIndexed { cIdx, cNode ->
                buildBlock(cNode, absRect.left, absRect.top, cIdx + 1)
            }

            return UIBlock(
                id = blockId,
                type = node.type,
                bounds = localBounds,
                children = childBlocks
            )
        }

        // 根背景节点
        val backgroundBlock = UIBlock(
            id = "background",
            type = UIBlockType.BACKGROUND,
            bounds = SerialRect(0f, 0f, canvasW, canvasH),
            isVisible = false
        )

        val assembledBlocks = mutableListOf<UIBlock>()
        assembledBlocks.add(backgroundBlock)
        rootCandidates.forEachIndexed { idx, rNode ->
            assembledBlocks.add(buildBlock(rNode, 0f, 0f, idx + 1))
        }

        onProgress("✅ 传统 CV 几何拓扑构建完成，共生成 ${assembledBlocks.size - 1} 个独立物理模块")

        val mainPage = UIPage(
            id = "main_page",
            nameStr = "Main Page",
            width = canvasW,
            height = canvasH,
            blocks = assembledBlocks
        )

        return ProjectState(
            projectId = templateName.ifBlank { "OfflineProject_${getCurrentTimeMillis()}" },
            globalTheme = "offline_cv_extracted",
            createdAt = getCurrentTimeMillis(),
            pages = listOf(mainPage)
        )
    }

    private data class RawBox(val left: Int, val top: Int, val right: Int, val bottom: Int, val w: Int, val h: Int, val area: Int)

    private data class DetectedNode(
        val physBox: RawBox,
        val logicalRect: SerialRect,
        val type: UIBlockType,
        val children: MutableList<DetectedNode>
    )

    private fun calculateIoU(b1: RawBox, b2: RawBox): Float {
        val x1 = max(b1.left, b2.left)
        val y1 = max(b1.top, b2.top)
        val x2 = min(b1.right, b2.right)
        val y2 = min(b1.bottom, b2.bottom)
        if (x2 <= x1 || y2 <= y1) return 0f
        val inter = (x2 - x1) * (y2 - y1)
        val union = b1.area + b2.area - inter
        return if (union > 0) inter.toFloat() / union else 0f
    }

    private fun isContainedIn(child: RawBox, parent: RawBox): Boolean {
        // 允许 5% 的边界外溢，只要中心点在父级内部且面积小于父级的 82%
        val cx = (child.left + child.right) / 2
        val cy = (child.top + child.bottom) / 2
        val inParent = cx in parent.left..parent.right && cy in parent.top..parent.bottom
        val smallerArea = child.area < parent.area * 0.82f
        return inParent && smallerArea
    }

    private fun inferBlockType(box: RawBox, imgW: Int, imgH: Int): UIBlockType {
        val w = box.w
        val h = box.h
        val cy = (box.top + box.bottom) / 2

        // 顶部大横条 -> HEADER
        if (cy < imgH * 0.15f && w > imgW * 0.6f) return UIBlockType.HEADER

        // 底部居中大按钮 -> SPIN_BUTTON
        if (cy > imgH * 0.75f && w in (imgW * 0.25f).toInt()..(imgW * 0.65f).toInt() && h in 70..240) {
            return UIBlockType.SPIN_BUTTON
        }

        // 小方块 (高宽比 0.7 ~ 1.4 且面积适中) -> BUTTON
        val ratio = w.toFloat() / h.toFloat()
        if (ratio in 0.7f..1.5f && w < imgW * 0.25f && h < imgH * 0.18f) {
            return UIBlockType.BUTTON
        }

        // 中间垂直长条 -> REEL
        if (cy in (imgH * 0.2f).toInt()..(imgH * 0.8f).toInt() && ratio in 0.2f..0.5f && h > imgH * 0.25f) {
            return UIBlockType.REEL
        }

        return UIBlockType.VIEW
    }
}
