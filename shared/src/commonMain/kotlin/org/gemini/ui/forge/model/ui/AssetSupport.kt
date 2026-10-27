package org.gemini.ui.forge.model.ui

import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.app.PromptLanguage

/**
 * 资产生成支持协议接口。
 * 允许具有特殊资产状态的组件向生图控制面板提供自适应读写、解绑和提示词配置。
 * 仅由传统 Slots 图元 [UIBlock] 实现；UI Studio 专用图元不参与 AI 生图，不实现本协议。
 */
interface AssetSupport {
    /** 资产的状态列表。如果为空表示是普通单态组件 */
    val assetStates: List<AssetState>

    /** 获取当前状态绑定的图片路径 */
    fun getCurrentImageUri(stateIndex: Int): TemplateFile?

    /** 获取当前状态的历史后缀 */
    fun getHistoricalIdSuffix(stateIndex: Int): String

    /** 清除/解绑当前状态的图片，返回更新后的 UIBlock */
    fun clearImageUri(stateIndex: Int): UIBlock

    /** 获取当前状态指定语言的提示词 */
    fun getPrompt(stateIndex: Int, effectiveLang: PromptLanguage): String

    /** 获取当前状态非选中语言的提示词（作 placeholder） */
    fun getOtherPrompt(stateIndex: Int, effectiveLang: PromptLanguage): String

    /** 更新当前状态指定语言的提示词，返回更新后的 UIBlock */
    fun updatePrompt(stateIndex: Int, effectiveLang: PromptLanguage, newValue: String): UIBlock
}
