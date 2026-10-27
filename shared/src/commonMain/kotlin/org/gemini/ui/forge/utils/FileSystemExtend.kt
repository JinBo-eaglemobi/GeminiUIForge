package org.gemini.ui.forge.utils

/**
 * 素材库支持的图片文件扩展名集合（统一小写，不含点号前缀）。
 */
val SUPPORTED_IMAGE_EXTENSIONS: Set<String> = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

/**
 * 判断给定文件路径是否为受支持的图片文件（按扩展名后缀判定，大小写不敏感）。
 */
fun isSupportedImageFile(path: String): Boolean {
    val extension = path.substringAfterLast('.', "").lowercase()
    return extension in SUPPORTED_IMAGE_EXTENSIONS
}

/**
 * 跨平台递归扫描目录下的全部文件（含所有层级子目录）。
 * 主要服务于素材库「外部文件夹批量导入」场景，需支持扫描用户任意的绝对路径目录。
 *
 * @param dirPath 目录绝对路径（外部磁盘目录）。
 * @param maxFiles 最多返回的文件数量上限（物理安全夹具，防止超大目录拖垮导入流程）。
 * @return 文件绝对路径列表（目录不存在或当前平台不支持时返回空列表）。
 */
expect suspend fun listFilesRecursively(dirPath: String, maxFiles: Int = 5000): List<String>
