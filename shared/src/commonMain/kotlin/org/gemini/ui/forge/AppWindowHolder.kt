package org.gemini.ui.forge

import androidx.compose.ui.unit.IntRect

/**
 * 应用主窗口持有器 (expect 单例)
 *
 * 核心职责：
 * 1. 持有桌面端 ComposeWindow 引用，供离屏截图与坐标事件注入使用；
 * 2. captureWindowBytes: 将应用窗口内容离屏重绘为 PNG 字节 (不依赖窗口可见性，
 *    最小化 / 被遮挡 / 后台运行均可截图)，可按窗口像素区域裁剪；
 * 3. tapAt: 在窗口像素坐标系合成真实鼠标点击事件 (完整事件流与命中测试，等效人工点击)。
 *
 * 说明：web / android / ios 平台暂返回 null / false (待适配)。
 */
expect object AppWindowHolder {

    /** 持有主窗口引用 (传入平台真实窗口对象；传 null 释放) */
    fun holdWindow(ref: Any?)

    /**
     * 应用窗口离屏截图
     * @param region 窗口像素裁剪区域 (IntRect)，null 表示整窗
     * @return PNG 字节；窗口未就绪 / 尺寸非法 / 平台不支持时返回 null
     */
    fun captureWindowBytes(region: IntRect?): ByteArray?

    /**
     * 在窗口像素坐标系注入真实点击事件
     * @return 是否成功派发 (窗口未就绪 / 平台不支持返回 false)
     */
    fun tapAt(x: Float, y: Float): Boolean
}
