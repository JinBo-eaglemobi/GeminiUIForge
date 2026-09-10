package org.gemini.ui.forge.model.chat

import kotlinx.serialization.Serializable

/**
 * 通信方向枚举。
 *
 * ★ 规范约束：固定取值的字符串参数一律优先使用枚举固定值，严禁在业务代码中散落魔法字符串
 * （kotlinx-serialization 默认按 name 序列化为 "REQ" / "RESP"，与磁盘文件命名及 JSON 存储天然兼容）。
 */
@Serializable
enum class TrafficDirection {
    /** 发往 AI 的请求原文 */
    REQ,
    /** AI 返回的响应原文 */
    RESP
}

/**
 * 单条原始网络通信档案（未经任何加工的完整报文）。
 *
 * @property seq 会话内递增序号（从 1 开始，与文件名前缀一致，如 0001）
 * @property direction 通信方向（枚举固定值：REQ / RESP）
 * @property timestamp 该报文产生的时间戳（毫秒）
 * @property url 完整请求 URL
 * @property model 模型标识（如 gemini-2.5-flash-image / imagen-x.x）
 * @property body 完整原始报文文本（含未脱敏的 Base64 图片载荷）
 */
@Serializable
data class TrafficRecord(
    val seq: Int,
    val direction: TrafficDirection,
    val timestamp: Long,
    val url: String = "",
    val model: String = "",
    val body: String = ""
)
