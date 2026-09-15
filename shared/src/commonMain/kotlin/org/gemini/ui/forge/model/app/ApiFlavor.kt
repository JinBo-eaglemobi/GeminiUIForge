package org.gemini.ui.forge.model.app

/**
 * AI 底层通信协议驱动架构
 */
enum class ApiFlavor(val displayNameZh: String, val displayNameEn: String) {
    /** 经典稳定协议：generateContent / streamGenerateContent (Base64 多模态请求体) */
    GENERATE_CONTENT("generateContent (传统兼容)", "generateContent (Legacy)"),
    
    /** 新一代交互协议：Interactions API (支持 previous_interaction_id 链式免传历史参考图) */
    INTERACTIONS("Interactions API (新一代链式交互)", "Interactions API (Next-Gen)")
}
