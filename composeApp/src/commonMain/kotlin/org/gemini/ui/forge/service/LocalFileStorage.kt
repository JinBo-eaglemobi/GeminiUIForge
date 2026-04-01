package org.gemini.ui.forge.service

expect class LocalFileStorage() {
    fun saveToFile(fileName: String, content: String): String
    fun saveBytesToFile(fileName: String, bytes: ByteArray): String
    fun readFromFile(fileName: String): String?
    fun readBytesFromFile(fileName: String): ByteArray?
    fun listFiles(): List<String>
    fun listDirectories(): List<String>
    fun getFilePath(fileName: String): String
    fun deleteFile(fileName: String): Boolean
    fun deleteDirectory(dirName: String): Boolean
}

