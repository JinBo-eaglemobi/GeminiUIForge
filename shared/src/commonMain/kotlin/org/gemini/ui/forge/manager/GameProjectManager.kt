package org.gemini.ui.forge.manager

import org.gemini.ui.forge.getCurrentTimeMillis

import kotlinx.serialization.encodeToString
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.gameproject.GameProjectInfo
import org.gemini.ui.forge.model.gameproject.GameProjectRegistry
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.looseJson
import kotlin.random.Random

/**
 * 游戏项目管理器。
 * 负责已纳管项目注册表（gameProjects/registry.json）的持久化与增删改查，
 * 以及 git 令牌的独立安全存储（通过 ConfigManager，键名 git_token_<id>）。
 */
class GameProjectManager(
    private val storage: LocalFileStorage,
    private val configManager: ConfigManager
) {

    /**
     * 读取注册表文件并反序列化；文件不存在时返回空注册表。
     */
    private suspend fun loadRegistry(): GameProjectRegistry {
        val text = storage.readFromFile(REGISTRY_FILE) ?: return GameProjectRegistry()
        return try {
            looseJson.decodeFromString(text)
        } catch (e: Exception) {
            AppLogger.e(TAG, "注册表解析失败，将重建", e)
            GameProjectRegistry()
        }
    }

    /**
     * 序列化并写入注册表文件。
     */
    private suspend fun storeRegistry(registry: GameProjectRegistry) {
        storage.saveToFile(REGISTRY_FILE, looseJson.encodeToString(registry))
    }

    /**
     * 列出全部已纳管项目（按最近打开时间倒序）。
     */
    suspend fun listProjects(): List<GameProjectInfo> {
        return loadRegistry().projects.sortedByDescending { it.lastOpenedAt }
    }

    /**
     * 新增或更新项目信息（按 id 匹配 upsert）。
     */
    suspend fun saveProject(info: GameProjectInfo) {
        val registry = loadRegistry()
        val others = registry.projects.filterNot { it.id == info.id }
        storeRegistry(GameProjectRegistry(projects = others + info))
    }

    /**
     * 仅移除项目注册记录（不删除本地文件）。
     * @return 被移除的项目信息；id 不存在时返回 null
     */
    suspend fun deleteProject(id: String, deleteFiles: Boolean = false): GameProjectInfo? {
        val registry = loadRegistry()
        val target = registry.projects.firstOrNull { it.id == id } ?: return null
        storeRegistry(GameProjectRegistry(projects = registry.projects.filterNot { it.id == id }))
        // 同步清除独立保存的 git 令牌
        saveGitToken(id, null)
        // 按需删除磁盘上的项目目录（localPath 为绝对路径；deleteDirectory 对绝对路径入参可正确解析）
        if (deleteFiles) {
            storage.deleteDirectory(target.localPath)
        }
        return target
    }

    /**
     * 更新项目最近打开时间。
     */
    suspend fun updateLastOpened(id: String) {
        val registry = loadRegistry()
        val updated = registry.projects.map {
            if (it.id == id) it.copy(lastOpenedAt = getCurrentTimeMillis()) else it
        }
        storeRegistry(GameProjectRegistry(projects = updated))
    }

    /**
     * 判断项目名字是否已被占用（创建向导查重用）。
     */
    suspend fun isNameExists(name: String): Boolean {
        return loadRegistry().projects.any { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * 计算项目最终落盘目录：基础目录下追加一层安全化的项目名字。
     * 统一规则确保最终路径始终包含项目名字，避免多个项目共用同一目录产生冲突。
     * @param baseDir 用户指定的基础保存目录；为空时使用默认的应用数据 gameProjects 根目录
     * @param name 项目名字（内部会做目录名安全化处理）
     */
    suspend fun resolveProjectDir(baseDir: String, name: String): String {
        val base = baseDir.trim().ifBlank { storage.getFilePath(PROJECTS_ROOT) }
        return "${base.trimEnd('/', '\\')}/${sanitizeDirName(name)}"
    }

    /**
     * 判断指定绝对路径目录是否已存在（创建向导目录冲突检测用）。
     */
    suspend fun dirExists(path: String): Boolean = storage.exists(path)

    /**
     * 删除指定绝对路径的目录及其全部内容（覆盖重建前清场用）。
     */
    suspend fun deleteDirectory(path: String): Boolean = storage.deleteDirectory(path)

    /**
     * 保存项目的 git 访问令牌到安全配置存储。
     * @param token 令牌内容；传入 null 表示清除
     */
    suspend fun saveGitToken(id: String, token: String?) {
        if (token.isNullOrBlank()) {
            // ConfigManager 无删除接口，以空字符串覆盖实现清除语义
            configManager.saveKey(tokenKeyName(id), "")
        } else {
            configManager.saveKey(tokenKeyName(id), token)
        }
    }

    /**
     * 读取项目保存的 git 访问令牌；未保存时返回 null。
     */
    suspend fun loadGitToken(id: String): String? {
        return configManager.loadKey(tokenKeyName(id))?.takeIf { it.isNotBlank() }
    }

    /**
     * 生成新的项目唯一标识。
     */
    fun newProjectId(): String {
        val timePart = getCurrentTimeMillis().toString(36)
        val randomPart = List(4) { Random.nextInt(36).toString(36) }.joinToString("")
        return "gp_$timePart$randomPart"
    }

    /**
     * 将项目名转换为安全的目录名（去除路径非法字符与空白）。
     */
    private fun sanitizeDirName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").trim('_')
        return cleaned.ifBlank { "project" }
    }

    /**
     * 令牌在 ConfigManager 中的键名。
     */
    private fun tokenKeyName(id: String) = "git_token_$id"

    private companion object {
        /** 日志标签 */
        const val TAG = "GameProjectManager"

        /** 注册表文件相对路径（基于应用数据根目录） */
        const val REGISTRY_FILE = "gameProjects/registry.json"

        /** 项目默认保存根目录（基于应用数据根目录） */
        const val PROJECTS_ROOT = "gameProjects"
    }
}
