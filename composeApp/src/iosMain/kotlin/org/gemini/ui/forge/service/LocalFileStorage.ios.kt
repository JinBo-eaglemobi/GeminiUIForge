package org.gemini.ui.forge.service

import platform.Foundation.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual class LocalFileStorage {
    
    private val fileManager = NSFileManager.defaultManager
    private var dataDir: String = ""

    init {
        // 使用 Document 目录
        val urls = fileManager.URLsForDirectory(NSDocumentDirectory, inDomains = NSUserDomainMask)
        if (urls.isNotEmpty()) {
            val documentUrl = urls.first() as NSURL
            dataDir = documentUrl.path + "/templates"
            
            var isDir = BooleanArray(1)
            if (!fileManager.fileExistsAtPath(dataDir, isDir.refTo(0)) || !isDir[0]) {
                fileManager.createDirectoryAtPath(dataDir, withIntermediateDirectories = true, attributes = null, error = null)
            }
        }
    }

    actual suspend fun updateDataDir(newPath: String): Boolean {
        // iOS 沙盒机制，不建议随意修改外部目录，所以这里暂且忽略或只在 Documents 内部处理
        return false
    }

    actual suspend fun getDataDir(): String = dataDir

    actual suspend fun saveToFile(fileName: String, content: String): String {
        val targetPath = "$dataDir/$fileName"
        val nsString = content as NSString
        
        // 确保父目录存在
        val parentPath = (targetPath as NSString).stringByDeletingLastPathComponent
        fileManager.createDirectoryAtPath(parentPath, withIntermediateDirectories = true, attributes = null, error = null)
        
        nsString.writeToFile(targetPath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        return targetPath
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun saveBytesToFile(fileName: String, bytes: ByteArray): String {
        val targetPath = "$dataDir/$fileName"
        
        val parentPath = (targetPath as NSString).stringByDeletingLastPathComponent
        fileManager.createDirectoryAtPath(parentPath, withIntermediateDirectories = true, attributes = null, error = null)
        
        val data = if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
            }
        } else {
            NSData.data()
        }
        
        data.writeToFile(targetPath, atomically = true)
        return targetPath
    }

    actual suspend fun readFromFile(fileName: String): String? {
        val targetPath = "$dataDir/$fileName"
        if (!fileManager.fileExistsAtPath(targetPath)) return null
        return NSString.stringWithContentsOfFile(targetPath, encoding = NSUTF8StringEncoding, error = null) as String?
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun readBytesFromFile(fileName: String): ByteArray? {
        val targetPath = "$dataDir/$fileName"
        if (!fileManager.fileExistsAtPath(targetPath)) return null
        
        val data = NSData.dataWithContentsOfFile(targetPath) ?: return null
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
        return bytes
    }

    actual suspend fun listFiles(): List<String> {
        val files = fileManager.contentsOfDirectoryAtPath(dataDir, error = null) as? List<String> ?: emptyList()
        return files.filter { it.endsWith(".json") }
    }

    actual suspend fun listDirectories(parentDir: String?): List<String> {
        val targetDir = if (parentDir == null) dataDir else "$dataDir/$parentDir"
        val items = fileManager.contentsOfDirectoryAtPath(targetDir, error = null) as? List<String> ?: emptyList()
        return items.filter { item ->
            val fullPath = "$targetDir/$item"
            var isDir = BooleanArray(1)
            fileManager.fileExistsAtPath(fullPath, isDir.refTo(0)) && isDir[0]
        }
    }

    actual suspend fun exists(fileName: String): Boolean {
        val targetPath = "$dataDir/$fileName"
        return fileManager.fileExistsAtPath(targetPath)
    }

    actual suspend fun deleteFile(fileName: String): Boolean {
        val targetPath = "$dataDir/$fileName"
        return fileManager.removeItemAtPath(targetPath, error = null)
    }

    actual suspend fun deleteDirectory(dirName: String): Boolean {
        val targetPath = "$dataDir/$dirName"
        return fileManager.removeItemAtPath(targetPath, error = null)
    }

    actual suspend fun getFilePath(fileName: String): String {
        return "$dataDir/$fileName"
    }
}
