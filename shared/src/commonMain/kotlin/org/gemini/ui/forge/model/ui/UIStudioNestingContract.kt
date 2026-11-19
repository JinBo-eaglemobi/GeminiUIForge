package org.gemini.ui.forge.model.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * UI Studio 页面扁平化架构契约注册表（单一事实源）。
 *
 * 规范 S1：界面级复合单元必须拆分为独立页面并通过 targetPageId 引用，
 *          无引用的 COMPONENT 禁止 children（唯一豁免：LIST item 位，见 S2-1）；
 * 规范 S2：仅官方复合组件（BUTTON/PROGRESS_BAR/SLIDER/SCROLL_BAR/LIST）允许携带子节点；
 *          BUTTON/PB/SLIDER/SB 的子节点必须为叶子图元（受限类型另有契约命名约束）；
 *          LIST 支持预置 item（定制数据功能），且 children 与 targetPageId 彻底解耦
 *          （有无引用均可携带），item 可为叶子/引用实例/内联定制容器，孙层封死；
 * 规范 S3：控制器与动效仅允许页面根声明（Root-Only），图元层禁止；
 *          页面根 controllers/transitions 以 JsonObject 原样保真承载，轻量校验防悬空。
 *
 * AI 写入校验、人工编辑拦截、常驻诊断与一键修复全通道统一复用本注册表。
 */
object UIStudioNestingContract {

    /**
     * 契约违规分类枚举
     */
    enum class ViolationType {
        /** 无外连引用的内嵌组件携带子树（界面级复合单元被内联，最严重；不含 LIST item 位豁免） */
        INLINE_PAGE_NESTED,
        /** 非白名单类型的图元携带 children（如 TEXT/IMAGE 内嵌子节点），或内联 item 孙层出现新的内嵌容器 */
        NESTED_CHILD_OUT_OF_CONTRACT,
        /** 契约子节点不是叶子图元（BUTTON/PB/SLIDER/SB 的子节点二次嵌套） */
        CONTRACT_CHILD_NOT_LEAF,
        /** 进度条/滑条/滚动条的子节点命名不在契约集合内 */
        CONTRACT_CHILD_ILLEGAL_NAME,
        /** 契约子节点数量超上限（BUTTON ≤ 8 / LIST 预置 item ≤ 30） */
        CONTRACT_CHILD_OVERFLOW,
        /** 页面根 controllers/transitions 配置非法（缺 name/pages/items 等必填字段） */
        ROOT_CONFIG_INVALID,
        /** transition 动作项的 target 未命中页面内任何图元（动效悬空） */
        TRANSITION_TARGET_MISSING
    }

    /**
     * 单项契约违规记录
     */
    data class Violation(
        val type: ViolationType,
        val pageId: String,
        val blockId: String,
        val blockPath: String,
        val childCount: Int,
        val message: String
    )

    /** BUTTON 契约子节点命名自由（装饰叶子），仅限数量上限 */
    const val FREE_FORM_CHILD_MAX = 8

    /** LIST 预置 item 数量上限（防数据膨胀，对齐导出契约规范） */
    const val LIST_ITEM_MAX = 30

    /** 契约命名受限类型的子节点契约集合（引擎按名装配 bar/grip/title 等） */
    val CONTRACT_CHILD_IDS: Map<UIBlockType, Set<String>> = mapOf(
        UIBlockType.PROGRESS_BAR to setOf("bar", "bar_v", "title"),
        UIBlockType.SLIDER to setOf("bar", "bar_v", "grip", "title"),
        UIBlockType.SCROLL_BAR to setOf("grip", "bar", "arrow1", "arrow2", "title")
    )

    /**
     * 判断图元类型是否属于"允许携带子节点的官方组件白名单"（含 LIST 预置 item 模式）
     */
    fun allowsContractChildren(type: UIBlockType): Boolean = when (type) {
        UIBlockType.BUTTON, UIBlockType.PROGRESS_BAR, UIBlockType.SLIDER,
        UIBlockType.SCROLL_BAR, UIBlockType.LIST -> true
        else -> false
    }

    /**
     * 判断某图元当前是否处于"可被一键修复器自动拆分"的违规嵌套状态。
     *
     * @param isListItemChild 该图元是否处于 LIST 直接子节点（item 位）——
     *        item 位的无引用 COMPONENT 容器是 S2-1 合规定制 item，永不自动拆分
     */
    fun isAutoFixable(block: UIStudioBlock, isListItemChild: Boolean = false): Boolean =
        !isListItemChild && block.targetPageId.isNullOrBlank() &&
            block.type == UIBlockType.COMPONENT && block.children.isNotEmpty()

    /**
     * 整页递归契约扫描。
     *
     * 聚合策略：报最外层违规容器，其子树不再重复报嵌套违规，避免横幅条目爆炸；
     * 深层节点将由修复器迭代收敛拆分。
     *
     * v3 规范：LIST 的 children 合法性与 targetPageId 彻底解耦——无论 LIST 是否引用了
     * 其它页面，均按 S2-1 item 规则校验（引用与 children 共存合法）。
     */
    fun checkPage(page: UIStudioPage): List<Violation> {
        val violations = mutableListOf<Violation>()

        fun scan(block: UIStudioBlock, parentPath: String, isListItemChild: Boolean) {
            val currentPath = if (parentPath.isEmpty()) block.id else "$parentPath > ${block.id}"
            val type = block.type
            val hasChildren = block.children.isNotEmpty()

            // ===== LIST 特权分支（v3：不区分有无引用，children 与 targetPageId 可共存）=====
            if (type == UIBlockType.LIST && hasChildren) {
                if (block.children.size > LIST_ITEM_MAX) {
                    violations.add(
                        Violation(
                            ViolationType.CONTRACT_CHILD_OVERFLOW, page.id, block.id, currentPath,
                            block.children.size,
                            "LIST「$currentPath」预置 item 数量 (${block.children.size}) 超过上限 $LIST_ITEM_MAX"
                        )
                    )
                    return
                }
                // S2-1：孙层封死——item 内不得再嵌无引用 COMPONENT 容器
                val deepContainer = block.children.firstOrNull { item ->
                    item.targetPageId.isNullOrBlank() && item.type == UIBlockType.COMPONENT &&
                        item.children.isNotEmpty() &&
                        item.children.any { grandChild ->
                            grandChild.targetPageId.isNullOrBlank() &&
                                grandChild.type == UIBlockType.COMPONENT &&
                                grandChild.children.isNotEmpty()
                        }
                }
                if (deepContainer != null) {
                    violations.add(
                        Violation(
                            ViolationType.NESTED_CHILD_OUT_OF_CONTRACT, page.id, block.id, currentPath,
                            block.children.size,
                            "LIST 预置 item「$currentPath > ${deepContainer.id}」的孙层出现嵌套容器。" +
                                "孙层封死约束：item 内部只能平铺叶子图元或页面引用实例，item 内的界面级复合单元仍须拆分独立页面"
                        )
                    )
                    return
                }
                // LIST 自身合法：继续递归校验 item 内部结构（标记 isListItemChild = true）
                block.children.forEach { scan(it, currentPath, true) }
                return
            }

            if (hasChildren && block.targetPageId.isNullOrBlank()) {
                when {
                    // S1 核心红线：无引用 COMPONENT 内联子界面（豁免 LIST item 位）
                    type == UIBlockType.COMPONENT && !isListItemChild -> {
                        violations.add(
                            Violation(
                                ViolationType.INLINE_PAGE_NESTED, page.id, block.id, currentPath,
                                block.children.size,
                                "组件「$currentPath」为无外连引用的内嵌界面单元（含 ${block.children.size} 个子图元）。" +
                                    "架构红线：界面级复合单元必须拆分为独立页面并通过 targetPageId 引用，禁止在当前页面内内联子页面数据"
                            )
                        )
                        return // 聚合：不再向其子树递归报嵌套违规
                    }
                    // S2：非白名单类型禁止 children
                    !allowsContractChildren(type) -> {
                        violations.add(
                            Violation(
                                ViolationType.NESTED_CHILD_OUT_OF_CONTRACT, page.id, block.id, currentPath,
                                block.children.size,
                                "类型为 ${type.name} 的图元「$currentPath」不允许内嵌子图元（仅 BUTTON/PROGRESS_BAR/SLIDER/SCROLL_BAR 契约子节点与 LIST 预置 item 允许）"
                            )
                        )
                        return
                    }
                    // S2：BUTTON/PB/SLIDER/SB 契约子节点必须全为叶子
                    block.children.any { it.children.isNotEmpty() } -> {
                        violations.add(
                            Violation(
                                ViolationType.CONTRACT_CHILD_NOT_LEAF, page.id, block.id, currentPath,
                                block.children.size,
                                "官方复合组件「$currentPath」的契约子节点必须为叶子图元，检测到二次嵌套子树"
                            )
                        )
                        return
                    }
                    // S2：契约命名约束（仅 PB/SLIDER/SB 受限类型）
                    CONTRACT_CHILD_IDS.containsKey(type) -> {
                        val legal = CONTRACT_CHILD_IDS.getValue(type)
                        val illegal = block.children.filter { it.id !in legal }
                        if (illegal.isNotEmpty()) {
                            violations.add(
                                Violation(
                                    ViolationType.CONTRACT_CHILD_ILLEGAL_NAME, page.id, block.id, currentPath,
                                    block.children.size,
                                    "复合组件「$currentPath」(${type.name}) 包含非契约子节点: ${illegal.map { it.id }} (合法契约集合: $legal)"
                                )
                            )
                            return
                        }
                    }
                }
                // S2：数量上限（BUTTON ≤ 8）
                if (block.children.size > FREE_FORM_CHILD_MAX) {
                    violations.add(
                        Violation(
                            ViolationType.CONTRACT_CHILD_OVERFLOW, page.id, block.id, currentPath,
                            block.children.size,
                            "组件「$currentPath」子节点数量 (${block.children.size}) 超过上限 $FREE_FORM_CHILD_MAX"
                        )
                    )
                    return
                }
            }
            // 合法容器或叶子：继续递归校验子节点
            block.children.forEach { scan(it, currentPath, type == UIBlockType.LIST) }
        }

        page.blocks.forEach { scan(it, "", false) }
        return violations
    }

    /**
     * S3 页面根 controllers/transitions 轻量校验。
     *
     * ① controller 必须含非空 name 与 pages 数组；
     * ② transition 必须含非空 name 与 items 数组，且每个动作项的 target 命中页面内图元 id（防动效悬空）。
     */
    fun checkPageRootConfigs(page: UIStudioPage): List<Violation> {
        val violations = mutableListOf<Violation>()
        val allBlockIds = mutableSetOf<String>()
        fun collectIds(blocks: List<UIStudioBlock>) {
            blocks.forEach {
                allBlockIds.add(it.id)
                collectIds(it.children)
            }
        }
        collectIds(page.blocks)

        for ((idx, ctrl) in page.controllers.withIndex()) {
            val name = ctrl["name"]?.jsonPrimitive?.contentOrNull
            val pagesArr = ctrl["pages"]?.jsonArray
            if (name.isNullOrBlank() || pagesArr.isNullOrEmpty()) {
                violations.add(
                    Violation(
                        ViolationType.ROOT_CONFIG_INVALID, page.id, "controllers[$idx]", "controllers[$idx]", 0,
                        "页面「${page.id}」根控制器 controllers[$idx] 缺少必填的 name 或 pages 字段（对齐 UIFactory 契约）"
                    )
                )
            }
        }

        for ((tIdx, trans) in page.transitions.withIndex()) {
            val transName = trans["name"]?.jsonPrimitive?.contentOrNull
            if (transName.isNullOrBlank()) {
                violations.add(
                    Violation(
                        ViolationType.ROOT_CONFIG_INVALID, page.id, "transitions[$tIdx]", "transitions[$tIdx]", 0,
                        "页面「${page.id}」根动效 transitions[$tIdx] 缺少必填的 name 字段"
                    )
                )
                continue
            }
            val items = trans["items"]?.jsonArray
            if (items.isNullOrEmpty()) {
                violations.add(
                    Violation(
                        ViolationType.ROOT_CONFIG_INVALID, page.id, "transitions[$tIdx]", transName, 0,
                        "页面「${page.id}」根动效「$transName」的 items 列表为空"
                    )
                )
                continue
            }
            for (itemEl in items) {
                val itemObj = itemEl as? JsonObject ?: continue
                val target = itemObj["target"]?.jsonPrimitive?.contentOrNull
                if (target.isNullOrBlank() || target !in allBlockIds) {
                    violations.add(
                        Violation(
                            ViolationType.TRANSITION_TARGET_MISSING, page.id, "transitions[$tIdx]", transName, 1,
                            "页面「${page.id}」动效「$transName」的动作项 target '$target' 未命中页面内任何图元（动效悬空）"
                        )
                    )
                }
            }
        }

        return violations
    }
}
