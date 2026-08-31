package org.gemini.ui.forge.service

/**
 * .gitignore 语法匹配引擎（用于解析仓库下载过滤配置 `.downloadignore`）。
 *
 * 支持 gitignore 语法子集（行为对齐 git）：
 * - `#` 开头的行视为注释，空行跳过；行首 `\#` / `\!` 表示字面量字符
 * - 行尾 `/` 表示仅匹配目录（目录命中时其下全部内容一并剔除）
 * - 行首 `!` 表示反选（从前次剔除中保留例外）
 * - `*` 匹配任意字符但不跨路径段，`?` 匹配单个字符，`**` 作为独立路径段时跨任意层级
 * - 模式不含 `/`（结尾 `/` 除外）时匹配任意层级的同名文件或目录；
 *   以 `/` 开头或含中间 `/` 时锚定仓库根的相对路径
 * - 后匹配优先：按行序依次判定，最后一条命中的规则（含反选）决定最终去留
 *
 * 说明：不支持 `[a-z]` 字符组语法（下载过滤场景中极少使用）。
 */
internal object GitIgnoreMatcher {

    /**
     * 单条过滤规则（编译后的正则与元信息）。
     * @param regex 编译后的整串匹配正则
     * @param dirOnly 是否仅匹配目录
     * @param negated 是否为反选规则（`!` 前缀）
     * @param raw 原始行文本（日志排查用）
     */
    data class Rule(
        val regex: Regex,
        val dirOnly: Boolean,
        val negated: Boolean,
        val raw: String
    )

    /**
     * 解析过滤配置内容为规则清单。
     * 注释、空行自动跳过，无法产生有效模式的行忽略。
     */
    fun parse(content: String): List<Rule> = content.lines().mapNotNull { rawLine ->
        var line = rawLine.trim()
        // 空行与注释行直接跳过
        if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
        // 行首转义：\# 与 \! 表示字面量字符（对齐 git 行为），去掉反斜杠保留原字符
        if (line.startsWith("\\#") || line.startsWith("\\!")) line = line.substring(1)
        // 反选规则
        val negated = line.startsWith("!")
        if (negated) line = line.substring(1)
        // 仅目录匹配（行尾斜杠）
        val dirOnly = line.endsWith("/")
        if (dirOnly) line = line.trimEnd('/')
        if (line.isEmpty()) return@mapNotNull null
        // 含斜杠（前导或中间）时锚定仓库根，否则匹配任意层级
        val anchored = line.startsWith("/") || line.contains('/')
        val body = line.trimStart('/')
        if (body.isEmpty()) return@mapNotNull null
        Rule(compileRegex(body, anchored, dirOnly), dirOnly, negated, rawLine.trim())
    }

    /**
     * 判定文件路径是否应被剔除。
     * 按规则顺序依次匹配，最后一条命中的规则决定结果（与 git 后匹配优先语义一致）。
     * @param path 仓库内相对路径（如 `build/assets/gold.png`）
     * @return true 表示剔除
     */
    fun isExcluded(path: String, rules: List<Rule>): Boolean {
        var excluded = false
        for (rule in rules) {
            if (rule.regex.matches(path)) excluded = !rule.negated
        }
        return excluded
    }

    /**
     * 把 gitignore 模式主体编译为整串匹配的正则。
     * @param body 已剥离前导 `/`、结尾 `/` 与 `!` 的模式主体
     * @param anchored 是否锚定仓库根
     * @param dirOnly 是否仅匹配目录（命中后连带其下全部内容）
     */
    private fun compileRegex(body: String, anchored: Boolean, dirOnly: Boolean): Regex {
        val sb = StringBuilder("^")
        // 非锚定模式：允许出现在任意层级（前缀为零个或多个完整路径段）
        if (!anchored) sb.append("(?:.*/)?")
        val segments = body.split('/')
        segments.forEachIndexed { index, seg ->
            when {
                // `**` 独立段且为尾段：吞掉剩余全部路径（含与上一段之间的分隔斜杠）
                seg == "**" && index == segments.lastIndex -> {
                    if (index > 0 && !sb.endsWith('/')) sb.append('/')
                    sb.append(".*")
                }
                // `**` 独立段（首段或中段）：吞掉零个或多个中间层级，自带结尾分隔斜杠
                seg == "**" -> sb.append("(?:.*/)?")
                else -> {
                    sb.append(escapeSegment(seg))
                    // 下一段不是会自带分隔斜杠的 `**` 时补路径分隔符
                    if (index != segments.lastIndex && segments[index + 1] != "**") sb.append('/')
                }
            }
        }
        // 目录模式：命中目录本身或其下任意内容
        if (dirOnly) sb.append("(?:/.*)?")
        sb.append("$")
        return Regex(sb.toString())
    }

    /**
     * 转义单个路径段：`*` 转为不跨段的通配，`?` 转为单字符通配，其余正则特殊字符原样转义。
     */
    private fun escapeSegment(segment: String): String = buildString {
        for (ch in segment) {
            when (ch) {
                '*' -> append("[^/]*")
                '?' -> append("[^/]")
                else -> {
                    if (ch in "\\.[]{}()+-^$|") append('\\')
                    append(ch)
                }
            }
        }
    }
}
