package org.gemini.ui.forge.data

import kotlinx.coroutines.flow.Flow
import org.gemini.ui.forge.state.GlobalAppEnv
import kotlinx.serialization.Serializable

/**
 * 模板系统专用的强类型相对路径类。
 * 强制约束：路径必须相对于全局数据根目录 [GlobalAppEnv.currentRootPath]。
 * 
 * 增强特性：
 * 1. 若传入的是相对路径，则直接使用。
 * 2. 若传入的是绝对路径，且该路径位于当前全局根目录下，则自动转换为相对路径。
 * 3. 否则（如指向外部、远程 URL 或 Base64）将抛出 IllegalArgumentException。
 */
@Serializable(with = TemplateFileSerializer::class)
class TemplateFile(path: String) {

    /** 核心属性：归一化后的相对路径 (使用 / 作为分隔符) */
    val relativePath: String

    init {
        val root = GlobalAppEnv.currentRootPath.replace("\\", "/").trimEnd('/')
        val normalized = path.replace("\\", "/").trim('/')
        
        relativePath = when {
            // 情况 1: 已经是相对于 root 的路径
            !isAbsoluteInternal(normalized) -> normalized
            
            // 情况 2: 是绝对路径且位于 root 目录下
            normalized.startsWith(root, ignoreCase = true) -> {
                normalized.removePrefix(root).trim('/')
            }
            
            // 情况 3: 非法路径 (远程、Base64 或工作区外的绝对路径)
            else -> {
                throw IllegalArgumentException(
                    "TemplateFile 路径非法！\n" +
                    "输入路径: $path\n" +
                    "当前根目录: $root\n" +
                    "要求：必须是相对路径，或者是位于根目录下的绝对路径。"
                )
            }
        }
    }

    /** 拼接全局根目录，获取当前的绝对物理路径 */
    fun getAbsolutePath(): String {
        val root = GlobalAppEnv.currentRootPath.replace("\\", "/").trimEnd('/')
        return "$root/$relativePath".replace("//", "/")
    }

    private fun isAbsoluteInternal(path: String): Boolean {
        // Windows 盘符探测 (C:/...)
        if (path.contains(":/")) return true
        // Unix/Linux 绝对路径探测
        if (path.startsWith("/")) return true
        // 协议探测
        if (path.startsWith("http") || path.startsWith("data:")) return true
        return false
    }

    /**
     * 转换为平台原生的 Path 对象。
     */
    fun toPlatformPath(): PlatformPath = resolvePlatformPath(getAbsolutePath())

    /**
     * 将当前文件拷贝到目标模板文件路径。
     */
    suspend fun copyTo(target: TemplateFile): Boolean {
        return copyToInternal(getAbsolutePath(), target.getAbsolutePath())
    }

    /**
     * 将当前文件拷贝到目标字符串路径。
     */
    suspend fun copyTo(targetPath: String): Boolean {
        val resolvedTarget = if (isAbsoluteInternal(targetPath)) {
            targetPath
        } else {
            val root = GlobalAppEnv.currentRootPath.replace("\\", "/").trimEnd('/')
            "$root/$targetPath".replace("//", "/")
        }
        return copyToInternal(getAbsolutePath(), resolvedTarget)
    }

    // --- 跨平台 IO 核心方法 ---

    suspend fun exists(): Boolean = isFileExistsInternal(getAbsolutePath())

    suspend fun readBytes(): ByteArray? = readBytesInternal(getAbsolutePath())

    suspend fun writeBytes(data: ByteArray): Boolean = writeBytesInternal(getAbsolutePath(), data)

    fun readStream(chunkSize: Int = 8192): Flow<ByteArray> = readStreamInternal(getAbsolutePath(), chunkSize)

    suspend fun createParentDirs(): Boolean = createParentDirsInternal(getAbsolutePath())

    suspend fun delete(recursive: Boolean = false): Boolean = deleteInternal(getAbsolutePath(), recursive)

    // --- 模拟 Data Class 行为 ---

    fun copy(path: String = this.relativePath): TemplateFile = TemplateFile(path)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TemplateFile) return false
        return relativePath == other.relativePath
    }

    override fun hashCode(): Int = relativePath.hashCode()

    override fun toString(): String = "TemplateFile(rel=$relativePath)"

    companion object {
        /** 从路径字符串创建 TemplateFile 实例 */
        fun fromPath(path: String): TemplateFile = TemplateFile(path)
    }
}

/** 快速将字符串转换为 TemplateFile 的扩展方法 */
fun String.toTemplateFile(): TemplateFile = TemplateFile(this)

/** 跨平台路径对象的类型占位符 */
expect class PlatformPath

/** 内部桥接方法 */
expect fun resolvePlatformPath(absolutePath: String): PlatformPath
expect suspend fun copyToInternal(sourcePath: String, targetPath: String): Boolean
expect suspend fun isFileExistsInternal(absPath: String): Boolean
expect suspend fun readBytesInternal(absPath: String): ByteArray?
expect suspend fun writeBytesInternal(absPath: String, data: ByteArray): Boolean
expect fun readStreamInternal(absPath: String, chunkSize: Int): Flow<ByteArray>
expect suspend fun createParentDirsInternal(absPath: String): Boolean
expect suspend fun deleteInternal(absPath: String, recursive: Boolean): Boolean
