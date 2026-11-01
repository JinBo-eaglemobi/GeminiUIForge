package org.gemini.ui.forge.utils.asset

import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 素材库文件名守卫工具。
 *
 * 职责：
 * 1. 文件名清洗：剔除文件系统非法字符，保证落盘安全；
 * 2. 目录内重名检测：不区分大小写比对目标目录下的已有文件名，
 *    任意入口在写盘/改名前必须调用本工具预检。
 *
 * 背景：UIAssetItem 的显示名 = 磁盘文件名（name 为只读计算属性），
 * 因此"文件名唯一"即"素材库名字唯一"，守卫文件名即可守住显示名。
 */
object AssetFileNameGuard {

    /** Windows / 通用文件系统非法字符集 */
    private val ILLEGAL_CHARS = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

    /**
     * 清洗文件名：非法字符 \ / : * ? " < > | → '_'；去除首尾空白与结尾 '.' / 空格。
     * 结果可能为空串（调用方需自行兜底，如回退 id / hash）。
     */
    fun sanitizeFileName(raw: String): String {
        var result = raw.trim()
        for (c in ILLEGAL_CHARS) result = result.replace(c, '_')
        return result.trimEnd('.', ' ')
    }

    /**
     * 判断目标目录下是否已存在同名文件（不区分大小写）。
     *
     * @param excludeFileName 需排除的文件名（重命名场景排除自身原文件名），null 不排除
     * @return true = 已存在同名（大小写无关），应拒绝或走冲突决策
     */
    suspend fun isFileNameTakenInDir(
        storage: LocalFileStorage,
        dirRelPath: String,
        fileName: String,
        excludeFileName: String? = null,
    ): Boolean {
        if (fileName.isBlank()) return false
        val existing = runCatching { storage.listFiles(dirRelPath) }.getOrNull() ?: return false
        val target = fileName.lowercase()
        for (name in existing) {
            if (excludeFileName != null && name.equals(excludeFileName, ignoreCase = true)) continue
            if (name.lowercase() == target) return true
        }
        return false
    }
}
