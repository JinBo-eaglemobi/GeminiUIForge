package org.gemini.ui.forge.data

import kotlinx.coroutines.flow.Flow
import org.gemini.ui.forge.state.GlobalAppEnv
import kotlinx.serialization.Serializable

/**
 * 模板系统专用的强类型相对路径类。
 * 强制约束：路径必须相对于全局数据根目录 [GlobalAppEnv.currentRootPath]。
 * 严禁传入带盘符或以斜杠开头的绝对路径。
 */
@Serializable(with = TemplateFileSerializer::class)
data class TemplateFile(val relativePath: String) {
    
    init {
        val normalized = relativePath.replace("\\", "/")
        require(!isAbsolute(normalized)) {
            "TemplateFile 必须使用相对路径！非法路径输入: $relativePath"
        }
    }

    /** 拼接全局根目录，获取当前的绝对物理路径 */
    fun getAbsolutePath(): String {
        val root = GlobalAppEnv.currentRootPath
        return if (root.isBlank()) relativePath else "$root/$relativePath".replace("//", "/")
    }

    /**
     * 判断路径是否为绝对路径的辅助方法。
     */
    private fun isAbsolute(path: String): Boolean {
        // Windows 盘符探测 (C:/...)
        if (path.contains(":/")) return true
        // Unix/Linux 根目录探测
        if (path.startsWith("/")) return true
        // 协议探测
        if (path.startsWith("http") || path.startsWith("data:")) return true
        return false
    }

    /**
     * 转换为平台原生的 Path 对象。
     * 每次调用都会基于最新的 [getAbsolutePath] 重新创建。
     */
    fun toPlatformPath(): Any = resolvePlatformPath(getAbsolutePath())

    // --- 跨平台 IO 核心方法 (具体实现在各 platform 模块中) ---

    suspend fun exists(): Boolean = isFileExistsInternal(getAbsolutePath())

    suspend fun readBytes(): ByteArray? = readBytesInternal(getAbsolutePath())

    suspend fun writeBytes(data: ByteArray): Boolean = writeBytesInternal(getAbsolutePath(), data)

    /** 流式读取文件内容，分块返回，内存安全 */
    fun readStream(chunkSize: Int = 8192): Flow<ByteArray> = readStreamInternal(getAbsolutePath(), chunkSize)

    /** 如果父目录不存在，则递归创建所有父目录 */
    suspend fun createParentDirs(): Boolean = createParentDirsInternal(getAbsolutePath())

    /** 删除文件或目录。若 recursive 为 true，则删除目录及其所有子文件 */
    suspend fun delete(recursive: Boolean = false): Boolean = deleteInternal(getAbsolutePath(), recursive)
}

/** 内部桥接方法：解析平台原生 Path */
expect fun resolvePlatformPath(absolutePath: String): Any

/** 内部桥接方法：判断存在 */
expect suspend fun isFileExistsInternal(absPath: String): Boolean

/** 内部桥接方法：读取字节 */
expect suspend fun readBytesInternal(absPath: String): ByteArray?

/** 内部桥接方法：写入字节 */
expect suspend fun writeBytesInternal(absPath: String, data: ByteArray): Boolean

/** 内部桥接方法：流式读取 */
expect fun readStreamInternal(absPath: String, chunkSize: Int): Flow<ByteArray>

/** 内部桥接方法：创建目录 */
expect suspend fun createParentDirsInternal(absPath: String): Boolean

/** 内部桥接方法：删除 */
expect suspend fun deleteInternal(absPath: String, recursive: Boolean): Boolean
