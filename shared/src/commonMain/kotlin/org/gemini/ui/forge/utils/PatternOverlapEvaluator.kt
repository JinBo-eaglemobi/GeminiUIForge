package org.gemini.ui.forge.utils

import org.jetbrains.skia.*
import kotlin.math.*

/**
 * 图像图案重叠综合评估结果
 *
 * @property zeroDiffRate 纯黑差值消隐率 (0.0 ~ 1.0)
 * @property meanAbsoluteError 平均绝对误差 MAE (0.0 ~ 255.0)
 * @property ssim 结构相似度 SSIM (0.0 ~ 1.0)
 * @property edgeIoU Sobel 边缘轮廓交并比 (0.0 ~ 1.0)
 * @property nccScore 归一化互相关匹配得分 (0.0 ~ 1.0)
 * @property compositeAlignmentScore 综合对齐评分 CAS (0.0 ~ 1.0)
 * @property rating 评级：PERFECT (>=0.90), GOOD (0.78~0.89), DEVIATED (<0.78)
 * @property driftVectorX 推荐自愈平移补偿 Δx (像素)
 * @property driftVectorY 推荐自愈平移补偿 Δy (像素)
 * @property diffImageBytes 差值残差高亮可视化图像 (WebP 字节数组)
 */
data class OverlapEvaluationResult(
    val zeroDiffRate: Float,              // 纯黑差值消隐率 (0.0 ~ 1.0)
    val meanAbsoluteError: Float,          // 平均绝对误差 MAE (0.0 ~ 255.0)
    val ssim: Float,                       // 结构相似度 SSIM (0.0 ~ 1.0)
    val edgeIoU: Float,                    // Sobel 边缘轮廓交并比 (0.0 ~ 1.0)
    val nccScore: Float,                   // 归一化互相关匹配得分 (0.0 ~ 1.0)
    val compositeAlignmentScore: Float,    // 综合对齐评分 CAS (0.0 ~ 1.0)
    val rating: String,                    // 评级：PERFECT (>=0.90), GOOD (0.78~0.89), DEVIATED (<0.78)
    val driftVectorX: Int,                 // 推荐自愈平移补偿 Δx (像素)
    val driftVectorY: Int,                 // 推荐自愈平移补偿 Δy (像素)
    val diffImageBytes: ByteArray? = null  // 差值残差高亮可视化图像 (WebP)
)

/**
 * 纯 Kotlin / Skia 原生算法驱动的计算机视觉多维图案重叠对齐评估器。
 * 零新增外部依赖，内存零拷贝极速运算，专为游戏 UI 透明通道 (Alpha) 像素级对齐量身定制。
 */
object PatternOverlapEvaluator {

    /**
     * 对实机渲染截图与参考设计原图进行全维度计算机视觉重叠分析。
     *
     * @param renderBytes 实机渲染局部截图数据
     * @param referenceBytes 参考设计底图对应切片数据
     * @param maxDriftWindow 局部滑动微观互相关搜索半径（默认 12 像素）
     * @param generateDiffImage 是否生成差值残差热力图
     */
    fun evaluate(
        renderBytes: ByteArray,
        referenceBytes: ByteArray,
        maxDriftWindow: Int = 12,
        generateDiffImage: Boolean = true
    ): OverlapEvaluationResult {
        val renderImg = Image.makeFromEncoded(renderBytes)
        val refImg = Image.makeFromEncoded(referenceBytes)

        val targetW = min(renderImg.width, refImg.width).coerceAtLeast(1)
        val targetH = min(renderImg.height, refImg.height).coerceAtLeast(1)

        // 1. 将两幅图规整至统一尺寸的 Skia Bitmap 内存缓冲区
        val renderBmp = Bitmap().apply { allocN32Pixels(targetW, targetH) }
        val renderCanvas = Canvas(renderBmp)
        renderCanvas.drawImageRect(
            renderImg,
            Rect.makeWH(renderImg.width.toFloat(), renderImg.height.toFloat()),
            Rect.makeWH(targetW.toFloat(), targetH.toFloat())
        )

        val refBmp = Bitmap().apply { allocN32Pixels(targetW, targetH) }
        val refCanvas = Canvas(refBmp)
        refCanvas.drawImageRect(
            refImg,
            Rect.makeWH(refImg.width.toFloat(), refImg.height.toFloat()),
            Rect.makeWH(targetW.toFloat(), targetH.toFloat())
        )

        // 2. 提取像素及灰度数组
        val totalPixels = targetW * targetH
        val grayRender = FloatArray(totalPixels)
        val grayRef = FloatArray(totalPixels)
        val alphaMask = BooleanArray(totalPixels)

        var validPixelCount = 0
        var zeroDiffCount = 0
        var totalAbsDiff = 0.0

        for (y in 0 until targetH) {
            val rowOffset = y * targetW
            for (x in 0 until targetW) {
                val idx = rowOffset + x
                val c1 = renderBmp.getColor(x, y)
                val c2 = refBmp.getColor(x, y)

                val a1 = Color.getA(c1)
                val r1 = Color.getR(c1)
                val g1 = Color.getG(c1)
                val b1 = Color.getB(c1)

                val a2 = Color.getA(c2)
                val r2 = Color.getR(c2)
                val g2 = Color.getG(c2)
                val b2 = Color.getB(c2)

                // 亮度 Y 换算
                grayRender[idx] = 0.299f * r1 + 0.587f * g1 + 0.114f * b1
                grayRef[idx] = 0.299f * r2 + 0.587f * g2 + 0.114f * b2

                // Alpha 蒙版：只要有一方具有不透明内容 (alpha >= 15)，即计入有效几何区域
                val isVisible = (a1 >= 15 || a2 >= 15)
                alphaMask[idx] = isVisible

                if (isVisible) {
                    validPixelCount++
                    val dr = abs(r1 - r2)
                    val dg = abs(g1 - g2)
                    val db = abs(b1 - b2)
                    val da = abs(a1 - a2)
                    val diff = (dr + dg + db + da) / 4.0

                    totalAbsDiff += diff
                    // 容许抗锯齿与有损压缩微小扰动 (<= 14) 视为完全消隐
                    if (diff <= 14.0) {
                        zeroDiffCount++
                    }
                }
            }
        }

        if (validPixelCount == 0) {
            validPixelCount = totalPixels
        }

        val zeroDiffRate = (zeroDiffCount.toFloat() / validPixelCount).coerceIn(0f, 1f)
        val mae = (totalAbsDiff / validPixelCount).toFloat().coerceIn(0f, 255f)

        // 3. 多尺度结构相似性 (SSIM) 计算
        val ssimScore = calculateSSIM(grayRender, grayRef, alphaMask, targetW, targetH)

        // 4. Sobel 边缘交并比 (Edge IoU) 计算
        val edgeIoUScore = calculateSobelEdgeIoU(grayRender, grayRef, targetW, targetH)

        // 5. 归一化互相关 (NCC) 及微观平移自愈向量 (Drift Vector) 探测
        val (nccScore, driftX, driftY) = calculateNCCAndDrift(
            grayRender,
            grayRef,
            targetW,
            targetH,
            maxDriftWindow
        )

        // 6. 综合加权对齐评估指数 (Composite Alignment Score, CAS)
        // 权重分配：差值消隐 30%, 结构相似性 30%, 边缘贴合 20%, 互相关 20%
        val cas = (0.30f * zeroDiffRate + 0.30f * ssimScore + 0.20f * edgeIoUScore + 0.20f * nccScore)
            .coerceIn(0f, 1f)

        val rating = when {
            cas >= 0.90f -> "PERFECT"
            cas >= 0.78f -> "GOOD"
            else -> "DEVIATED"
        }

        // 7. 生成差值残差可视化热力图 (Difference Map)
        val diffImageBytes = if (generateDiffImage) {
            generateDifferenceBitmap(renderBmp, refBmp, targetW, targetH)
        } else null

        return OverlapEvaluationResult(
            zeroDiffRate = roundTo3(zeroDiffRate),
            meanAbsoluteError = roundTo3(mae),
            ssim = roundTo3(ssimScore),
            edgeIoU = roundTo3(edgeIoUScore),
            nccScore = roundTo3(nccScore),
            compositeAlignmentScore = roundTo3(cas),
            rating = rating,
            driftVectorX = driftX,
            driftVectorY = driftY,
            diffImageBytes = diffImageBytes
        )
    }

    /**
     * 计算两图的结构相似性 (SSIM)
     */
    private fun calculateSSIM(
        img1: FloatArray,
        img2: FloatArray,
        mask: BooleanArray,
        w: Int,
        h: Int
    ): Float {
        val c1 = 6.5025f   // (0.01 * 255)^2
        val c2 = 58.5225f  // (0.03 * 255)^2

        val blockSize = 8
        var totalSsim = 0.0
        var blockCount = 0

        for (by in 0 until h step blockSize) {
            val curH = min(blockSize, h - by)
            for (bx in 0 until w step blockSize) {
                val curW = min(blockSize, w - bx)
                val curN = curW * curH
                if (curN < 16) continue

                var sum1 = 0.0
                var sum2 = 0.0
                var activeCount = 0

                for (y in 0 until curH) {
                    val row = (by + y) * w
                    for (x in 0 until curW) {
                        val idx = row + (bx + x)
                        if (mask[idx]) {
                            sum1 += img1[idx]
                            sum2 += img2[idx]
                            activeCount++
                        }
                    }
                }

                // 块内有效像素不足时不计入
                if (activeCount < (curN / 2)) continue

                val mu1 = sum1 / activeCount
                val mu2 = sum2 / activeCount

                var var1 = 0.0
                var var2 = 0.0
                var covar = 0.0

                for (y in 0 until curH) {
                    val row = (by + y) * w
                    for (x in 0 until curW) {
                        val idx = row + (bx + x)
                        if (mask[idx]) {
                            val d1 = img1[idx] - mu1
                            val d2 = img2[idx] - mu2
                            var1 += d1 * d1
                            var2 += d2 * d2
                            covar += d1 * d2
                        }
                    }
                }

                val sigma1Sq = var1 / activeCount
                val sigma2Sq = var2 / activeCount
                val sigma12 = covar / activeCount

                val numerator = (2.0 * mu1 * mu2 + c1) * (2.0 * sigma12 + c2)
                val denominator = (mu1 * mu1 + mu2 * mu2 + c1) * (sigma1Sq + sigma2Sq + c2)

                val blockSsim = (numerator / denominator).coerceIn(0.0, 1.0)
                totalSsim += blockSsim
                blockCount++
            }
        }

        return if (blockCount > 0) (totalSsim / blockCount).toFloat() else 1.0f
    }

    /**
     * 使用 Sobel 算子提取边缘并计算交并比 (Edge IoU)
     */
    private fun calculateSobelEdgeIoU(
        img1: FloatArray,
        img2: FloatArray,
        w: Int,
        h: Int
    ): Float {
        if (w < 3 || h < 3) return 1.0f

        var intersection = 0
        var union = 0
        val edgeThreshold = 38.0f // 边缘梯度模长阈值

        for (y in 1 until h - 1) {
            val rPrev = (y - 1) * w
            val rCur = y * w
            val rNext = (y + 1) * w

            for (x in 1 until w - 1) {
                // Sobel 算子卷积计算 img1
                val gx1 = (img1[rPrev + x + 1] + 2f * img1[rCur + x + 1] + img1[rNext + x + 1]) -
                        (img1[rPrev + x - 1] + 2f * img1[rCur + x - 1] + img1[rNext + x - 1])
                val gy1 = (img1[rNext + x - 1] + 2f * img1[rNext + x] + img1[rNext + x + 1]) -
                        (img1[rPrev + x - 1] + 2f * img1[rPrev + x] + img1[rPrev + x + 1])
                val mag1 = sqrt(gx1 * gx1 + gy1 * gy1)
                val isEdge1 = mag1 >= edgeThreshold

                // Sobel 算子卷积计算 img2
                val gx2 = (img2[rPrev + x + 1] + 2f * img2[rCur + x + 1] + img2[rNext + x + 1]) -
                        (img2[rPrev + x - 1] + 2f * img2[rCur + x - 1] + img2[rNext + x - 1])
                val gy2 = (img2[rNext + x - 1] + 2f * img2[rNext + x] + img2[rNext + x + 1]) -
                        (img2[rPrev + x - 1] + 2f * img2[rPrev + x] + img2[rPrev + x + 1])
                val mag2 = sqrt(gx2 * gx2 + gy2 * gy2)
                val isEdge2 = mag2 >= edgeThreshold

                if (isEdge1 || isEdge2) {
                    union++
                    if (isEdge1 && isEdge2) {
                        intersection++
                    }
                }
            }
        }

        return if (union > 0) (intersection.toFloat() / union).coerceIn(0f, 1f) else 1.0f
    }

    /**
     * 归一化互相关 (NCC) 与滑动自愈位移探测
     */
    private fun calculateNCCAndDrift(
        img1: FloatArray,
        img2: FloatArray,
        w: Int,
        h: Int,
        searchRadius: Int
    ): Triple<Float, Int, Int> {
        val baseScore = computeNCCAtOffset(img1, img2, w, h, 0, 0)
        var bestScore = baseScore
        var bestDx = 0
        var bestDy = 0

        // 仅在微观平移范围内探测最优峰值
        val step = 1
        for (dy in -searchRadius..searchRadius step step) {
            for (dx in -searchRadius..searchRadius step step) {
                if (dx == 0 && dy == 0) continue
                val score = computeNCCAtOffset(img1, img2, w, h, dx, dy)
                if (score > bestScore) {
                    bestScore = score
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        // 只有当偏移处的得分比 (0,0) 高出 0.04 时，才断定存在物理位移漂移
        val finalDriftX = if (bestScore - baseScore > 0.04f) bestDx else 0
        val finalDriftY = if (bestScore - baseScore > 0.04f) bestDy else 0

        return Triple(baseScore.coerceIn(0f, 1f), finalDriftX, finalDriftY)
    }

    /**
     * 计算特定平移位移 (dx, dy) 下的归一化互相关得分
     */
    private fun computeNCCAtOffset(
        img1: FloatArray,
        img2: FloatArray,
        w: Int,
        h: Int,
        dx: Int,
        dy: Int
    ): Float {
        val startX = max(0, -dx)
        val endX = min(w, w - dx)
        val startY = max(0, -dy)
        val endY = min(h, h - dy)

        val overlapW = endX - startX
        val overlapH = endY - startY
        if (overlapW <= 4 || overlapH <= 4) return 0f

        var sum1 = 0.0
        var sum2 = 0.0
        val n = overlapW * overlapH

        for (y in startY until endY) {
            val r1 = y * w
            val r2 = (y + dy) * w
            for (x in startX until endX) {
                sum1 += img1[r1 + x]
                sum2 += img2[r2 + (x + dx)]
            }
        }

        val mu1 = sum1 / n
        val mu2 = sum2 / n

        var numerator = 0.0
        var denom1 = 0.0
        var denom2 = 0.0

        for (y in startY until endY) {
            val r1 = y * w
            val r2 = (y + dy) * w
            for (x in startX until endX) {
                val d1 = img1[r1 + x] - mu1
                val d2 = img2[r2 + (x + dx)] - mu2
                numerator += d1 * d2
                denom1 += d1 * d1
                denom2 += d2 * d2
            }
        }

        val denom = sqrt(denom1 * denom2)
        return if (denom > 1e-6) (numerator / denom).toFloat().coerceIn(0f, 1f) else 0f
    }

    /**
     * 生成差值残差可视化热力图（完美重叠处纯黑消隐，错位边缘高亮红色警戒显示）
     */
    private fun generateDifferenceBitmap(
        bmp1: Bitmap,
        bmp2: Bitmap,
        w: Int,
        h: Int
    ): ByteArray? {
        return try {
            val surface = Surface.makeRasterN32Premul(w, h)
            val canvas = surface.canvas
            val paint = Paint()
            canvas.clear(Color.makeARGB(0, 0, 0, 0))

            for (y in 0 until h) {
                var runStartX = 0
                var runColor: Int? = null

                for (x in 0 until w) {
                    val c1 = bmp1.getColor(x, y)
                    val c2 = bmp2.getColor(x, y)

                    val a1 = Color.getA(c1)
                    val a2 = Color.getA(c2)

                    val color = if (a1 < 10 && a2 < 10) {
                        Color.makeARGB(0, 0, 0, 0)
                    } else {
                        val dr = abs(Color.getR(c1) - Color.getR(c2))
                        val dg = abs(Color.getG(c1) - Color.getG(c2))
                        val db = abs(Color.getB(c1) - Color.getB(c2))
                        val avgDiff = (dr + dg + db) / 3

                        when {
                            avgDiff <= 14 -> Color.makeARGB(255, 12, 14, 18)
                            avgDiff <= 35 -> Color.makeARGB(255, 20, 60, 120)
                            else -> {
                                val intensity = (avgDiff * 2).coerceAtMost(255)
                                Color.makeARGB(255, intensity, 30, 45)
}


                        }
                    }

                    if (runColor == null) {
                        runColor = color
                        runStartX = x
                    } else if (runColor != color) {
                        if (Color.getA(runColor) > 0) {
                            paint.color = runColor
                            canvas.drawRect(Rect.makeLTRB(runStartX.toFloat(), y.toFloat(), x.toFloat(), (y + 1).toFloat()), paint)
                        }
                        runColor = color
                        runStartX = x
                    }
                }
                if (runColor != null && Color.getA(runColor) > 0) {
                    paint.color = runColor
                    canvas.drawRect(Rect.makeLTRB(runStartX.toFloat(), y.toFloat(), w.toFloat(), (y + 1).toFloat()), paint)
                }
            }

            val img = surface.makeImageSnapshot()
            img.encodeToData(EncodedImageFormat.WEBP, 90)?.bytes
        } catch (_: Exception) {
            null
        }
    }

    private fun roundTo3(v: Float): Float = (round(v * 1000.0) / 1000.0).toFloat()
}
