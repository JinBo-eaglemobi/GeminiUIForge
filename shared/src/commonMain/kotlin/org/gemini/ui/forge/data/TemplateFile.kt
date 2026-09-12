package org.gemini.ui.forge.data

import kotlinx.coroutines.flow.Flow
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import kotlinx.io.files.SystemTemporaryDirectory
import org.gemini.ui.forge.appDir
import org.gemini.ui.forge.runDir
import org.gemini.ui.forge.userHomePath
import org.gemini.ui.forge.utils.GlobalAppEnv
import kotlinx.serialization.Serializable

/**
 * 模板系统专用的强类型相对路径类。
 * 强制约束：路径必须相对于全局数据根目录 [GlobalAppEnv.currentRootPath] 或指定的自定义根目录 [customRootPath]。
 *
 * 增强特性：
 * 1. 若传入的是相对路径，则直接使用；
 * 2. 若传入的是绝对路径，且该路径位于基准根目录下，则自动转换为相对路径；
 * 3. 否则（如指向外部、远程 URL 或 Base64）将抛出 IllegalArgumentException；
 * 4. 根目录未初始化时自动兜底：若既未传入 [customRootPath] 且全局根目录为空，将自动回退为用户主目录；
 * 5. 支持独立自定义根目录 [customRootPath]：摆脱唯一依赖全局单例的限制，适用于单测、离线脚本及多工程并发。
 *
 * 【重要序列化语义说明】
 * 本类序列化时严格遵循纯净的相对路径规范，持久化 JSON 中【仅保存 [relativePath] 相对路径字符串】，
 * 传入的 [customRootPath] 属于仅在当前内存生命周期有效的物理锚点，【不会被序列化保存到 JSON 中】。
 * 当反序列化读取数据时，将自动重新挂靠到当前运行环境下的全局根目录。
 *
 * @param path 相对路径、位于根目录下的绝对路径（远程 URL 与 Base64 不受支持）
 * @param customRootPath 可选的独立自定义根目录绝对路径（为 null 时缺省使用 [GlobalAppEnv.ensureInitialized]）
 */
@Serializable(with = TemplateFileSerializer::class)
class TemplateFile(
    path: String,
    val customRootPath: String? = null
) {

    /** 核心属性：归一化后的相对路径 (使用 / 作为分隔符) */
    val relativePath: String

    init {
        // 基准根目录优先使用自定义根，未提供时由全局环境确保初始化兜底
        val root = (customRootPath ?: GlobalAppEnv.ensureInitialized()).replace("\\", "/").trimEnd('/')
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

    /** 拼接基准根目录，获取当前的绝对物理路径（优先自定义根，缺省回退全局根） */
    fun getAbsolutePath(): String {
        val root = (customRootPath ?: GlobalAppEnv.ensureInitialized()).trimEnd {
            it == '/' || it == '\\'
        }
        return "$root$SystemPathSeparator$relativePath"
    }

    /**
     * 判断内部路径字符串是否为绝对路径形态。
     * 依次探测：Windows 盘符 (C:/...)、Unix 根路径 (/...)、网络协议 (http/https) 与 Base64 (data:)。
     */
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
     *
     * @return 各平台实际的路径类型实例（JVM 为 java.nio/IO 包装，详见 actual 实现）
     */
    fun toPlatformPath(): PlatformPath = resolvePlatformPath(getAbsolutePath())

    /**
     * 将当前文件拷贝到目标模板文件路径（目标必须是位于根目录下的合法 TemplateFile）。
     *
     * @param target 拷贝目标（相对路径实体）
     * @return 拷贝成功返回 true；源不存在、IO 失败等返回 false
     */
    suspend fun copyTo(target: TemplateFile): Boolean {
        return copyToInternal(getAbsolutePath(), target.getAbsolutePath())
    }

    /**
     * 将当前文件拷贝到目标字符串路径。
     *
     * @param targetPath 目标路径：支持绝对路径；相对路径会自动拼接全局根目录后解析
     * @return 拷贝成功返回 true；源不存在、IO 失败等返回 false
     */
    suspend fun copyTo(targetPath: String): Boolean {
        val resolvedTarget = if (isAbsoluteInternal(targetPath)) {
            targetPath
        } else {
            val root = (customRootPath ?: GlobalAppEnv.ensureInitialized()).replace("\\", "/").trimEnd('/')
            "$root/$targetPath".replace("//", "/")
        }
        return copyToInternal(getAbsolutePath(), resolvedTarget)
    }

    // --- 跨平台 IO 核心方法（内部统一以绝对物理路径桥接到各平台 actual 实现） ---

    /**
     * 判断当前文件是否真实存在于磁盘。
     *
     * @return 文件存在返回 true；不存在、无权限或 IO 异常返回 false
     */
    suspend fun exists(): Boolean = isFileExistsInternal(getAbsolutePath())

    /**
     * 读取当前文件的全部字节内容。
     *
     * @return 读取成功返回字节数组；文件不存在或读取失败返回 null
     */
    suspend fun readBytes(): ByteArray? = readBytesInternal(getAbsolutePath())

    /**
     * 将字节数组覆写写入当前文件（文件不存在时自动创建，父目录需已存在或先调 [createParentDirs]）。
     *
     * @param data 待写入的完整字节数据
     * @return 写入成功返回 true；IO 失败返回 false
     */
    suspend fun writeBytes(data: ByteArray): Boolean = writeBytesInternal(getAbsolutePath(), data)

    /**
     * 以流式分块读取当前文件内容，适用于大文件场景（避免一次性载入内存）。
     *
     * @param chunkSize 每个数据块的字节大小，默认 8192 (8KB)
     * @return 按顺序发射各数据块的冷流；流关闭由收集方控制
     */
    fun readStream(chunkSize: Int = 8192): Flow<ByteArray> = readStreamInternal(getAbsolutePath(), chunkSize)

    /**
     * 递归创建当前文件的全部缺失父目录。
     *
     * @return 目录创建成功（或已存在）返回 true；创建失败返回 false
     */
    suspend fun createParentDirs(): Boolean = createParentDirsInternal(getAbsolutePath())

    /**
     * 删除当前文件（或目录）。
     *
     * @param recursive 当目标为目录时是否递归删除全部子内容；仅删除单个文件时忽略
     * @return 删除成功（或目标本就不存在）返回 true；删除失败返回 false
     */
    suspend fun delete(recursive: Boolean = false): Boolean = deleteInternal(getAbsolutePath(), recursive)

    // --- 模拟 Data Class 行为（因包含非构造属性 relativePath，无法直接声明为 data class） ---

    /**
     * 以指定路径创建副本实例（行为对齐 data class copy，自动沿用当前实例绑定的自定义根目录）。
     *
     * @param path 新副本使用的路径字符串，默认沿用当前的归一化相对路径
     * @param root 新副本绑定的自定义根目录，默认沿用当前的 [customRootPath]
     */
    fun copy(path: String = this.relativePath, root: String? = this.customRootPath): TemplateFile = TemplateFile(path, root)

    /** 判等规则：仅比较归一化后的相对路径（与 data class 语义一致） */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TemplateFile) return false
        return relativePath == other.relativePath
    }

    /** 哈希值：基于归一化相对路径，与 equals 规则配套 */
    override fun hashCode(): Int = relativePath.hashCode()

    /** 调试友好的字符串表示（仅暴露相对路径，避免泄漏根目录信息） */
    override fun toString(): String = "TemplateFile(rel=$relativePath)"

    companion object {

        /**
         * 通用基础工厂方法：创建 TemplateFile 实例。
         *
         * @param path 目标文件路径（相对路径、或位于根目录下的绝对路径）
         * @param root 可选的自定义根目录绝对路径（若为 null，则自动使用全局环境根目录 [GlobalAppEnv.ensureInitialized]）
         * @return 初始化完成的 [TemplateFile] 强类型文件实体
         */
        fun of(path: String, root: String? = null): TemplateFile = TemplateFile(path, customRootPath = root)

        /**
         * 快捷工厂方法：以应用程序工程/安装根目录 [appDir] 为基准创建实例。
         *
         * 适用于直接访问项目内置脚本、模板预设、核心源码等存放在工程根目录下的资源：
         * 无论在 IDE 开发调试还是在生产安装运行，均自动绑定正确的应用根目录。
         *
         * @param relativePath 相对于应用程序根目录的相对路径
         */
        fun inAppDir(relativePath: String): TemplateFile = TemplateFile(relativePath, customRootPath = appDir)

        /**
         * 快捷工厂方法：以当前启动/运行工作目录 [runDir] 为基准创建实例。
         *
         * 适用于处理当前命令行工作路径下的外部传入文件或输入输出：
         * 自动绑定当前进程的工作目录（Working Directory）。
         *
         * @param relativePath 相对于当前运行工作目录的相对路径
         */
        fun inRunDir(relativePath: String): TemplateFile = TemplateFile(relativePath, customRootPath = runDir)

        /**
         * 快捷工厂方法：以操作系统当前用户主目录 [userHomePath] 为基准创建实例。
         *
         * 适用于读写全局配置文件、用户持久化缓存（如 `~/.geminiuiforge/`）：
         * 自动绑定当前用户的主目录（User Home）。
         *
         * @param relativePath 相对于当前用户主目录的相对路径
         */
        fun inUserHome(relativePath: String): TemplateFile = TemplateFile(relativePath, customRootPath = userHomePath)
    }
}

/** 快速将字符串转换为 TemplateFile 的扩展方法（支持可选自定义根目录） */
fun String.toTemplateFile(root: String? = null): TemplateFile = TemplateFile(this, customRootPath = root)

/**
 * 跨平台路径对象的类型占位符。
 * 各平台 actual 指向真实的原生路径类型（如 JVM 的 [kotlinx.io.files.Path] 包装等）。
 */
expect class PlatformPath

/**
 * 内部桥接方法：将绝对物理路径解析为平台原生 Path 对象。
 *
 * @param absolutePath 绝对物理路径字符串
 * @return 平台原生路径实例
 */
expect fun resolvePlatformPath(absolutePath: String): PlatformPath

/**
 * 内部桥接方法：将源文件拷贝到目标路径（目标父目录需存在）。
 *
 * @param sourcePath 源文件的绝对物理路径
 * @param targetPath 目标位置的绝对物理路径
 * @return 拷贝成功返回 true，失败返回 false
 */
expect suspend fun copyToInternal(sourcePath: String, targetPath: String): Boolean

/**
 * 内部桥接方法：探测指定绝对路径的文件是否真实存在。
 *
 * @param absPath 待探测的绝对物理路径
 * @return 存在返回 true，不存在或探测异常返回 false
 */
expect suspend fun isFileExistsInternal(absPath: String): Boolean

/**
 * 内部桥接方法：读取指定绝对路径文件的全部字节。
 *
 * @param absPath 待读取的绝对物理路径
 * @return 读取成功返回字节数组；不存在或失败返回 null
 */
expect suspend fun readBytesInternal(absPath: String): ByteArray?

/**
 * 内部桥接方法：将字节数组写入指定绝对路径文件（不存在时创建，存在时覆写）。
 *
 * @param absPath 目标文件的绝对物理路径
 * @param data 待写入的完整字节数据
 * @return 写入成功返回 true，失败返回 false
 */
expect suspend fun writeBytesInternal(absPath: String, data: ByteArray): Boolean

/**
 * 内部桥接方法：以固定块大小流式读取指定绝对路径文件。
 *
 * @param absPath 待读取的绝对物理路径
 * @param chunkSize 每个数据块的字节大小
 * @return 依次发射数据块的冷流
 */
expect fun readStreamInternal(absPath: String, chunkSize: Int): Flow<ByteArray>

/**
 * 内部桥接方法：为指定绝对路径递归创建全部缺失的父目录。
 *
 * @param absPath 目标文件的绝对物理路径
 * @return 创建成功（或已存在）返回 true，失败返回 false
 */
expect suspend fun createParentDirsInternal(absPath: String): Boolean

/**
 * 内部桥接方法：删除指定绝对路径的文件或目录。
 *
 * @param absPath 待删除的绝对物理路径
 * @param recursive 目标为目录时是否递归删除全部子内容
 * @return 删除成功（或目标不存在）返回 true，失败返回 false
 */
expect suspend fun deleteInternal(absPath: String, recursive: Boolean): Boolean
