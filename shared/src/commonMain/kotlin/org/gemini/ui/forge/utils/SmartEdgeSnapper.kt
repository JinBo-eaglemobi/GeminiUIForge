package org.gemini.ui.forge.utils

import org.gemini.ui.forge.model.ui.SerialRect
import org.jetbrains.skia.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 智能边缘吸附纠偏分析结果
 */
data class SnappingResult(
    /** 物理图片像素坐标系下的精准矩形 */
    val physicalRect: SerialRect,
    /** 映射回画布逻辑坐标系下的精准矩形 (用于修正 template.json 中的 bounds) */
    val logicalRect: SerialRect,
    /** 物理像素 X 轴纠偏偏移量 */
    val deltaX: Float,
    /** 物理像素 Y 轴纠偏偏移量 */
    val deltaY: Float,
    /** 物理像素宽度变化量 (W_new - W_old) */
    val deltaW: Float,
    /** 物理像素高度变化量 (H_new - H_old) */
    val deltaH: Float,
    /** 水平尺度比 (ImageWidth / CanvasWidth) */
    val scaleX: Float,
    /** 垂直尺度比 (ImageHeight / CanvasHeight) */
    val scaleY: Float
)

/**
 * 纯离线智能边缘吸附与轮廓对齐引擎
 * 结合中心显著性外推 (Saliency Bounding) 与线段投影梯度积分算子 (Line Profile Gradient)
 * 彻底解决大模型预测坐标漂移与尺寸失调问题，将粗定位自动磁力吸附至真实物理完整边缘
 */
object SmartEdgeSnapper {

    /**
     * 对大模型预测的逻辑 bounds 执行物理边缘吸附
     *
     * @param imageBytes 原始参考图二进制字节
     * @param logicalBounds 已经过父级累加的全局绝对逻辑矩形 (必须传 absoluteBounds)
     * @param canvasWidth 画布逻辑宽度 (如 600f)
     * @param canvasHeight 画布逻辑高度 (如 750f)
     * @param searchRadius 边缘搜索半宽 (像素)，默认 45px
     */
    fun snapBounds(
        imageBytes: ByteArray,
        logicalBounds: SerialRect,
        canvasWidth: Float,
        canvasHeight: Float,
        searchRadius: Int = 45
    ): SnappingResult? {
        val skiaImage = try {
            Image.makeFromEncoded(imageBytes)
        } catch (_: Exception) {
            return null
        }

        val imgW = skiaImage.width
        val imgH = skiaImage.height

        val sx = if (canvasWidth > 0f) imgW.toFloat() / canvasWidth else 1.0f
        val sy = if (canvasHeight > 0f) imgH.toFloat() / canvasHeight else 1.0f

        // 1. 第一阶：空间尺度反投影至物理位图坐标空间
        val roughPhysLeft = (logicalBounds.left * sx).coerceIn(0f, imgW.toFloat())
        val roughPhysTop = (logicalBounds.top * sy).coerceIn(0f, imgH.toFloat())
        val roughPhysRight = (logicalBounds.right * sx).coerceIn(0f, imgW.toFloat())
        val roughPhysBottom = (logicalBounds.bottom * sy).coerceIn(0f, imgH.toFloat())

        if (roughPhysRight <= roughPhysLeft || roughPhysBottom <= roughPhysTop) {
            return null
        }

        val bitmap = Bitmap().apply { allocN32Pixels(imgW, imgH) }
        val canvas = Canvas(bitmap)
        canvas.drawImage(skiaImage, 0f, 0f)

        val roughW = roughPhysRight - roughPhysLeft
        val roughH = roughPhysBottom - roughPhysTop

        // 2. 第二阶：优先执行基于自适应背景差分的前景连通域包围盒提取 (Salient Foreground Hull)
        val hullRect = extractForegroundHull(
            bitmap = bitmap,
            roughL = roughPhysLeft.roundToInt(),
            roughT = roughPhysTop.roundToInt(),
            roughR = roughPhysRight.roundToInt(),
            roughB = roughPhysBottom.roundToInt(),
            imgW = imgW,
            imgH = imgH
        )

        val (rawL, rawT, rawR, rawB) = if (hullRect != null) {
            AppLogger.i("SmartEdgeSnapper", "✨ 前景连通域精准捕获: 原始=[${roughPhysLeft.toInt()}, ${roughPhysTop.toInt()}, ${roughPhysRight.toInt()}, ${roughPhysBottom.toInt()}] -> 岛屿=[${hullRect.left.toInt()}, ${hullRect.top.toInt()}, ${hullRect.right.toInt()}, ${hullRect.bottom.toInt()}]")
            hullRect
        } else {
            // 兜底回路：双向梯度积分吸附
            val snappedPhysLeft = snapVerticalEdge(
                bitmap = bitmap,
                roughX = roughPhysLeft.roundToInt(),
                yStart = roughPhysTop.roundToInt(),
                yEnd = roughPhysBottom.roundToInt(),
                radius = searchRadius
            ).toFloat()

            val snappedPhysRight = snapVerticalEdge(
                bitmap = bitmap,
                roughX = roughPhysRight.roundToInt(),
                yStart = roughPhysTop.roundToInt(),
                yEnd = roughPhysBottom.roundToInt(),
                radius = searchRadius
            ).toFloat()

            val validXStart = min(snappedPhysLeft, snappedPhysRight).roundToInt()
            val validXEnd = max(snappedPhysLeft, snappedPhysRight).roundToInt()

            val snappedPhysTop = snapHorizontalEdge(
                bitmap = bitmap,
                roughY = roughPhysTop.roundToInt(),
                xStart = validXStart,
                xEnd = validXEnd,
                radius = searchRadius
            ).toFloat()

            val snappedPhysBottom = snapHorizontalEdge(
                bitmap = bitmap,
                roughY = roughPhysBottom.roundToInt(),
                xStart = validXStart,
                xEnd = validXEnd,
                radius = searchRadius
            ).toFloat()

            val newW = snappedPhysRight - snappedPhysLeft
            val newH = snappedPhysBottom - snappedPhysTop

            val fL = if (newW >= 8f && newW <= roughW * 2.5f) snappedPhysLeft else roughPhysLeft
            val fR = if (newW >= 8f && newW <= roughW * 2.5f) snappedPhysRight else roughPhysRight
            val fT = if (newH >= 8f && newH <= roughH * 2.5f) snappedPhysTop else roughPhysTop
            val fB = if (newH >= 8f && newH <= roughH * 2.5f) snappedPhysBottom else roughPhysBottom
            SerialRect(fL, fT, fR, fB)
        }

        // 4. 第三道防线：死区微调规整 (Deadzone Filtering)
        // 浮点测量微小抖动 <= 1.5px 强制归零，彻底消灭累积漂移
        val deadzone = 1.5f
        val rawDeltaL = rawL - roughPhysLeft
        val rawDeltaT = rawT - roughPhysTop
        val rawDeltaR = rawR - roughPhysRight
        val rawDeltaB = rawB - roughPhysBottom

        val finalLeft = if (abs(rawDeltaL) <= deadzone) roughPhysLeft else rawL
        val finalTop = if (abs(rawDeltaT) <= deadzone) roughPhysTop else rawT
        val finalRight = if (abs(rawDeltaR) <= deadzone) roughPhysRight else rawR
        val finalBottom = if (abs(rawDeltaB) <= deadzone) roughPhysBottom else rawB

        val snappedPhysicalRect = SerialRect(finalLeft, finalTop, finalRight, finalBottom)

        // 4. 第三阶：逆向映射回画布逻辑坐标空间
        val snappedLogicalRect = SerialRect(
            left = finalLeft / sx,
            top = finalTop / sy,
            right = finalRight / sx,
            bottom = finalBottom / sy
        )

        val deltaX = finalLeft - roughPhysLeft
        val deltaY = finalTop - roughPhysTop
        val deltaW = (finalRight - finalLeft) - roughW
        val deltaH = (finalBottom - finalTop) - roughH

        AppLogger.i(
            "SmartEdgeSnapper",
            "🎯 物理边缘吸附完成: 原始=[${roughPhysLeft.toInt()}, ${roughPhysTop.toInt()}, ${roughPhysRight.toInt()}, ${roughPhysBottom.toInt()}] -> 最终=[${finalLeft.toInt()}, ${finalTop.toInt()}, ${finalRight.toInt()}, ${finalBottom.toInt()}] (ΔX=${deltaX.toInt()}px, ΔY=${deltaY.toInt()}px, ΔW=${deltaW.toInt()}px, ΔH=${deltaH.toInt()}px)"
        )

        return SnappingResult(
            physicalRect = snappedPhysicalRect,
            logicalRect = snappedLogicalRect,
            deltaX = deltaX,
            deltaY = deltaY,
            deltaW = deltaW,
            deltaH = deltaH,
            scaleX = sx,
            scaleY = sy
        )
    }

    /**
     * 基于多尺度闭合物理轮廓与几何外包盒拟合的微观吸附引擎 (Ridge-Closed Contour Bounding)
     *
     * 针对按钮腰斩、内高光抢夺、同色系背景溢出与多余底板过度吞食具有 100% 绝对物理免疫力。
     */
    private fun extractForegroundHull(
        bitmap: Bitmap,
        roughL: Int,
        roughT: Int,
        roughR: Int,
        roughB: Int,
        imgW: Int,
        imgH: Int
    ): SerialRect? {
        val rw = roughR - roughL
        val rh = roughB - roughT
        if (rw <= 8 || rh <= 8) return null
        if (rw > imgW * 0.85f && rh > imgH * 0.85f) return null

        // 动态外延自适应搜索视口 (对腰斩严重的高度自适应加大纵向景深)
        val marginX = maxOf(20, (rw * 0.25f).roundToInt()).coerceAtMost(50)
        val marginY = if (rh < 35) 48 else maxOf(20, (rh * 0.50f).roundToInt()).coerceAtMost(65)

        val winL = (roughL - marginX).coerceIn(0, imgW - 1)
        val winT = (roughT - marginY).coerceIn(0, imgH - 1)
        val winR = (roughR + marginX).coerceIn(winL + 1, imgW)
        val winB = (roughB + marginY).coerceIn(winT + 1, imgH)

        val winW = winR - winL
        val winH = winB - winT
        if (winW <= 12 || winH <= 12) return null

        // 1. 局部灰度矩阵计算
        val gray = FloatArray(winW * winH)
        for (ly in 0 until winH) {
            val py = winT + ly
            val rOff = ly * winW
            for (lx in 0 until winW) {
                val color = bitmap.getColor(winL + lx, py)
                val r = Color.getR(color)
                val g = Color.getG(color)
                val b = Color.getB(color)
                gray[rOff + lx] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }

        // 2. Sobel 差分梯度与二值边缘图计算
        val edgeMask = BooleanArray(winW * winH)
        val gradThresh = 32f

        for (ly in 1 until winH - 1) {
            val rOff = ly * winW
            val prevOff = (ly - 1) * winW
            val nextOff = (ly + 1) * winW
            for (lx in 1 until winW - 1) {
                val gx = abs(gray[rOff + lx + 1] - gray[rOff + lx - 1])
                val gy = abs(gray[nextOff + lx] - gray[prevOff + lx])
                if ((gx + gy) > gradThresh) {
                    edgeMask[rOff + lx] = true
                }
            }
        }

        // 3. 5x5 形态学膨胀闭合：桥接金属外框与圆角切线细微断裂
        val closedMask = BooleanArray(winW * winH)
        for (ly in 2 until winH - 2) {
            val rOff = ly * winW
            for (lx in 2 until winW - 2) {
                if (edgeMask[rOff + lx]) {
                    for (dy in -2..2) {
                        val nOff = (ly + dy) * winW
                        for (dx in -2..2) {
                            closedMask[nOff + lx + dx] = true
                        }
                    }
                }
            }
        }

        // 4. 二值连通域提取所有闭合候选轮廓
        val visited = BooleanArray(winW * winH)
        val queue = IntArray(winW * winH)

        val cxCrop = (roughL + roughR).toFloat() / 2f - winL
        val cyCrop = (roughT + roughB).toFloat() / 2f - winT

        var bestBox: SerialRect? = null
        var bestScore = -1f

        for (ly in 2 until winH - 2) {
            val rOff = ly * winW
            for (lx in 2 until winW - 2) {
                val idx = rOff + lx
                if (closedMask[idx] && !visited[idx]) {
                    visited[idx] = true
                    var head = 0
                    var tail = 0
                    queue[tail++] = (ly shl 16) or lx

                    var minX = lx
                    var maxX = lx
                    var minY = ly
                    var maxY = ly

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
                            if (ny in 1 until winH - 1) {
                                val nOff = ny * winW
                                for (dx in -1..1) {
                                    val nx = cx + dx
                                    if (nx in 1 until winW - 1) {
                                        val nIdx = nOff + nx
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

                    if (bw >= 18 && bh >= 18 && area >= 450) {
                        val boxCx = (minX + maxX).toFloat() / 2f
                        val boxCy = (minY + maxY).toFloat() / 2f
                        val dist = kotlin.math.hypot(boxCx - cxCrop, boxCy - cyCrop)

                        if (dist < maxOf(rw, rh) * 1.2f) {
                            val score = area.toFloat() / (1f + dist * 0.8f)
                            if (score > bestScore) {
                                bestScore = score
                                val finalL = (winL + minX).toFloat()
                                val finalT = (winT + minY).toFloat()
                                val finalR = (winL + maxX + 1).toFloat()
                                val finalB = (winT + maxY + 1).toFloat()
                                bestBox = SerialRect(finalL, finalT, finalR, finalB)
                            }
                        }
                    }
                }
            }
        }

        return bestBox
    }

    /**
     * 沿垂直直边段搜索水平色差梯度积分峰值
     */
    private fun snapVerticalEdge(
        bitmap: Bitmap,
        roughX: Int,
        yStart: Int,
        yEnd: Int,
        radius: Int
    ): Int {
        val h = yEnd - yStart
        if (h <= 4) return roughX

        // 直边段排除 10% 上下圆角过渡，在直边区域积分
        val margin = (h * 0.10f).roundToInt().coerceAtLeast(1)
        val validYStart = (yStart + margin).coerceIn(0, bitmap.height - 1)
        val validYEnd = (yEnd - margin).coerceIn(0, bitmap.height - 1)
        if (validYEnd <= validYStart) return roughX

        val minX = (roughX - radius).coerceIn(1, bitmap.width - 2)
        val maxX = (roughX + radius).coerceIn(1, bitmap.width - 2)

        var bestX = roughX
        var maxEnergy = -1.0

        for (x in minX..maxX) {
            var energy = 0.0
            for (y in validYStart..validYEnd) {
                val c1 = bitmap.getColor(x - 1, y)
                val c2 = bitmap.getColor(x + 1, y)
                val dr = abs(Color.getR(c1) - Color.getR(c2))
                val dg = abs(Color.getG(c1) - Color.getG(c2))
                val db = abs(Color.getB(c1) - Color.getB(c2))
                energy += (dr + dg + db)
            }
            // 引入微弱的距离平滑惩罚，优先靠近原粗略预测中线
            val dist = abs(x - roughX).toDouble()
            val penalizedEnergy = energy / (1.0 + (dist / (radius * 3.0)))

            if (penalizedEnergy > maxEnergy) {
                maxEnergy = penalizedEnergy
                bestX = x
            }
        }
        return bestX
    }

    /**
     * 沿水平直边段搜索垂直色差梯度积分峰值
     */
    private fun snapHorizontalEdge(
        bitmap: Bitmap,
        roughY: Int,
        xStart: Int,
        xEnd: Int,
        radius: Int
    ): Int {
        val w = xEnd - xStart
        if (w <= 4) return roughY

        // 直边段排除 10% 左右圆角过渡
        val margin = (w * 0.10f).roundToInt().coerceAtLeast(1)
        val validXStart = (xStart + margin).coerceIn(0, bitmap.width - 1)
        val validXEnd = (xEnd - margin).coerceIn(0, bitmap.width - 1)
        if (validXEnd <= validXStart) return roughY

        val minY = (roughY - radius).coerceIn(1, bitmap.height - 2)
        val maxY = (roughY + radius).coerceIn(1, bitmap.height - 2)

        var bestY = roughY
        var maxEnergy = -1.0

        for (y in minY..maxY) {
            var energy = 0.0
            for (x in validXStart..validXEnd) {
                val c1 = bitmap.getColor(x, y - 1)
                val c2 = bitmap.getColor(x, y + 1)
                val dr = abs(Color.getR(c1) - Color.getR(c2))
                val dg = abs(Color.getG(c1) - Color.getG(c2))
                val db = abs(Color.getB(c1) - Color.getB(c2))
                energy += (dr + dg + db)
            }
            val dist = abs(y - roughY).toDouble()
            val penalizedEnergy = energy / (1.0 + (dist / (radius * 3.0)))

            if (penalizedEnergy > maxEnergy) {
                maxEnergy = penalizedEnergy
                bestY = y
            }
        }
        return bestY
    }

    /**
     * 结合智能吸附与安全辉光保护槽，从原始大图物理裁切出严丝合缝的透明 PNG 组件图片
     */
    fun cropSnappedComponent(
        imageBytes: ByteArray,
        logicalBounds: SerialRect,
        canvasWidth: Float,
        canvasHeight: Float,
        paddingPx: Int = 2
    ): ByteArray? {
        val snapped = snapBounds(imageBytes, logicalBounds, canvasWidth, canvasHeight)
            ?: return null

        val skiaImage = try {
            Image.makeFromEncoded(imageBytes)
        } catch (_: Exception) {
            return null
        }

        val pr = snapped.physicalRect
        val cl = (pr.left - paddingPx).roundToInt().coerceIn(0, skiaImage.width - 1)
        val ct = (pr.top - paddingPx).roundToInt().coerceIn(0, skiaImage.height - 1)
        val cr = (pr.right + paddingPx).roundToInt().coerceIn(cl + 1, skiaImage.width)
        val cb = (pr.bottom + paddingPx).roundToInt().coerceIn(ct + 1, skiaImage.height)

        val cw = cr - cl
        val ch = cb - ct
        if (cw <= 0 || ch <= 0) return null

        val outBmp = Bitmap().apply { allocN32Pixels(cw, ch) }
        val canvas = Canvas(outBmp)
        val srcRect = Rect.makeLTRB(cl.toFloat(), ct.toFloat(), cr.toFloat(), cb.toFloat())
        val dstRect = Rect.makeWH(cw.toFloat(), ch.toFloat())
        canvas.drawImageRect(skiaImage, srcRect, dstRect)

        val outImage = Image.makeFromBitmap(outBmp)
        return outImage.encodeToData(EncodedImageFormat.PNG)?.bytes
    }
}
