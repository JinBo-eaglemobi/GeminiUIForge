package org.gemini.ui.forge.utils

/**
 * @ 引用联想模糊子序列匹配器（fzf / VS Code 风格）
 *
 * 纯函数无状态：支持跳字子序列匹配、空格分词 AND 语义、
 * 命中质量评分排序与命中索引提取（供 UI 高亮）。
 */
object MentionFuzzyMatcher {

    /** 单字段匹配结果；[score] 越大越相关，null 表示未命中 */
    data class FieldMatch(val score: Int, val hitIndices: List<Int>)

    /**
     * 对单个字段执行子序列匹配。
     * @param query 已分词的单个关键词（内部做 lowercase 归一）
     * @param target 候选字段文本（title 或 subtitle）
     */
    fun matchField(query: String, target: String): FieldMatch? {
        if (query.isEmpty()) return FieldMatch(0, emptyList())
        val q = query.lowercase()
        val t = target.lowercase()
        val hits = ArrayList<Int>(q.length)
        var score = 0
        var qi = 0
        var prevHit = -2
        for (ti in t.indices) {
            if (qi >= q.length) break
            if (t[ti] != q[qi]) continue
            // 1. 命中质量评分
            score += when {
                ti == prevHit + 1 -> 8                                    // 连续命中段
                isWordBoundary(t, ti) -> 6                                // 词边界（snake/camel/路径分隔）
                else -> 2                                                 // 普通分散命中
            }
            if (ti == 0) score += 4                                       // 首字符前缀亲和
            if (prevHit >= 0) score -= (ti - prevHit - 1)                 // 间隙惩罚：跳字越多分越低
            hits.add(ti)
            prevHit = ti
            qi++
        }
        if (qi < q.length) return null                                    // 未消耗完 query → 子序列不成立
        return FieldMatch(score, hits)
    }

    /** 判断 target[ti] 是否处于词边界（前一字符为分隔符，或驼峰小写→大写转折） */
    private fun isWordBoundary(t: String, index: Int): Boolean {
        if (index == 0) return true
        val prev = t[index - 1]
        val cur = t[index]
        return prev in "_-./ \\(" || (prev.isLowerCase() && cur.isUpperCase())
    }

    /**
     * 空格分词 AND 语义的整体匹配：
     * 每个 token 在 [title]（满分）或 [subtitle]（分数减半）上任一字段命中即可，
     * 全部 token 命中才保留该候选（AND 语义）；总分 = 各 token 最佳分之和。
     */
    fun matchTokens(
        tokens: List<String>,
        title: String,
        subtitle: String?
    ): Pair<Int, List<Int>>? {
        if (tokens.isEmpty()) return 0 to emptyList()
        var total = 0
        var titleHits: List<Int> = emptyList()
        for (token in tokens) {
            val titleMatch = matchField(token, title)
            val subMatch = subtitle?.let { matchField(token, it) }?.let { it.copy(score = it.score / 2) }
            val best = when {
                titleMatch != null && subMatch != null ->
                    if (titleMatch.score >= subMatch.score) titleMatch else subMatch
                titleMatch != null -> titleMatch
                subMatch != null -> subMatch
                else -> return null // 任一 token 失败 → 整体失败（AND）
            }
            if (best === titleMatch && titleHits.isEmpty()) {
                titleHits = titleMatch.hitIndices
            }
            total += best.score
        }
        return total to titleHits
    }
}
