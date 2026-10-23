package org.gemini.ui.forge.model.skill

/**
 * 专职解析遵循现代 AI 智能体规范的 Markdown 技能文件（YAML Frontmatter + Markdown Instructions）。
 * 遵循开放标准，解析元数据（id, name, description, triggers, tools, enabled）并提取正文作为纯净 instructions。
 */
object SkillMarkdownParser {
    fun parse(content: String, defaultId: String = "custom_skill"): UiStudioSkill {
        val trimmed = content.trim()
        if (!trimmed.startsWith("---")) {
            // 无 Frontmatter，直接作为纯 Markdown 指令兜底
            return UiStudioSkill(
                id = defaultId,
                name = defaultId,
                description = "",
                instructions = trimmed
            )
        }

        val secondSepIndex = trimmed.indexOf("---", 3)
        if (secondSepIndex == -1) {
            return UiStudioSkill(
                id = defaultId,
                name = defaultId,
                description = "",
                instructions = trimmed
            )
        }

        val frontmatterText = trimmed.substring(3, secondSepIndex).trim()
        val markdownBody = trimmed.substring(secondSepIndex + 3).trim()

        var id = defaultId
        var name = defaultId
        var description = ""
        val triggers = mutableListOf<String>()
        val tools = mutableListOf<String>()
        var enabled = true

        val lines = frontmatterText.lines()
        var currentListTarget: MutableList<String>? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("- ") && currentListTarget != null) {
                val item = line.substring(2).trim().removeSurrounding("\"").removeSurrounding("'")
                if (item.isNotEmpty()) currentListTarget.add(item)
                continue
            }

            val colonIdx = line.indexOf(':')
            if (colonIdx != -1) {
                currentListTarget = null
                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()

                when (key) {
                    "id" -> if (value.isNotEmpty()) id = value.removeSurrounding("\"").removeSurrounding("'")
                    "name" -> if (value.isNotEmpty()) name = value.removeSurrounding("\"").removeSurrounding("'")
                    "description" -> if (value.isNotEmpty()) description = value.removeSurrounding("\"").removeSurrounding("'")
                    "enabled", "isenabled" -> enabled = value.toBooleanStrictOrNull() ?: true
                    "triggers", "triggerkeywords" -> {
                        if (value.startsWith("[") && value.endsWith("]")) {
                            val items = value.removeSurrounding("[", "]").split(",")
                                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                                .filter { it.isNotEmpty() }
                            triggers.addAll(items)
                        } else {
                            currentListTarget = triggers
                        }
                    }
                    "tools", "toolnames" -> {
                        if (value.startsWith("[") && value.endsWith("]")) {
                            val items = value.removeSurrounding("[", "]").split(",")
                                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                                .filter { it.isNotEmpty() }
                            tools.addAll(items)
                        } else {
                            currentListTarget = tools
                        }
                    }
                }
            }
        }

        return UiStudioSkill(
            id = id,
            name = name,
            description = description,
            instructions = markdownBody,
            tools = tools,
            triggers = triggers,
            enabled = enabled
        )
    }
}
