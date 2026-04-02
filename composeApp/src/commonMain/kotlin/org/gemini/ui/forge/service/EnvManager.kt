package org.gemini.ui.forge.service

/**
 * 环境变量及配置密钥的跨平台管理类
 * 负责在不同平台（如 Android, JVM, iOS）上以安全、持久化的方式存储敏感信息。
 */
expect class EnvManager() {
    /**
     * 将键值对保存到本地安全存储或配置文件中
     * @param keyName 配置键的名称
     * @param keyValue 要保存的值
     */
    fun saveKey(keyName: String, keyValue: String)

    /**
     * 根据键名从本地读取配置值
     * @param keyName 配置键的名称
     * @return 返回读取到的字符串值；如果不存在则返回 null
     */
    fun loadKey(keyName: String): String?
}
