package org.gemini.ui.forge.service

/**
 * 跨平台的本地文件存储管理抽象类
 * 负责处理应用数据的持久化，包括文本、二进制数据的写入、读取，以及目录和文件的操作。
 */
expect class LocalFileStorage() {
    /**
     * 将字符串文本内容保存到指定文件中
     * @param fileName 目标文件名（可包含相对路径）
     * @return 文件的本地绝对路径
     */
    fun saveToFile(fileName: String, content: String): String

    /**
     * 将二进制字节数据保存到指定文件中
     * @param fileName 目标文件名（可包含相对路径）
     * @param bytes 待保存的字节数组
     * @return 文件的本地绝对路径
     */
    fun saveBytesToFile(fileName: String, bytes: ByteArray): String

    /**
     * 从指定文件中读取文本内容
     * @param fileName 文件名（可包含相对路径）
     * @return 文件的文本内容；若文件不存在或读取失败则返回 null
     */
    fun readFromFile(fileName: String): String?

    /**
     * 从指定文件中读取二进制字节数据
     * @param fileName 文件名（可包含相对路径）
     * @return 文件的字节数组；若文件不存在或读取失败则返回 null
     */
    fun readBytesFromFile(fileName: String): ByteArray?

    /**
     * 列出当前存储根目录下的所有文件名称
     * @return 文件名称列表
     */
    fun listFiles(): List<String>

    /**
     * 列出当前存储根目录下的所有子目录名称
     * @return 目录名称列表
     */
    fun listDirectories(): List<String>

    /**
     * 获取指定文件的本地绝对路径
     * @param fileName 文件名称
     * @return 绝对路径字符串
     */
    fun getFilePath(fileName: String): String

    /**
     * 删除指定的文件
     * @param fileName 要删除的文件名
     * @return 删除是否成功
     */
    fun deleteFile(fileName: String): Boolean

    /**
     * 删除指定的目录及其内部所有文件
     * @param dirName 要删除的目录名称
     * @return 删除是否成功
     */
    fun deleteDirectory(dirName: String): Boolean
}
