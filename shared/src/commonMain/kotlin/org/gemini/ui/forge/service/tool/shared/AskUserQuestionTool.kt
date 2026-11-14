package org.gemini.ui.forge.service.tool.shared

import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.*

/**
 * 结构化决策问询弹窗工具：向用户展示带详细描述的方案选项卡片、推荐置顶、自定义输入并同步等待作答
 */
class AskUserQuestionTool : ToolDefinition {

    override val name: String = "ask_user_question"

    override val description: String =
        "向真实用户展示结构化决策问询弹窗并同步挂起等待用户作答。" +
        "支持长篇技术方案大纲展示、多个选项带 label 与 description 双行卡片、推荐选项置顶徽章、" +
        "单选/多选模式、自定义输入答案，并在底部内置安全退出通道（结束任务）。" +
        "适用于方案提呈、重大排版授权、风格抉择与破坏性操作前的意向收集。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val timeoutMs: Long = Long.MAX_VALUE

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("questions", buildJsonObject {
                put("type", "array")
                put("description", "问题列表（1~3 个，顺序轮播逐题作答）")
                put("items", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("question", buildJsonObject {
                            put("type", "string")
                            put("description", "完整问题正文")
                        })
                        put("header", buildJsonObject {
                            put("type", "string")
                            put("description", "弹窗短标题（建议 ≤30 字符，如：'文档拆分方案'）")
                        })
                        put("options", buildJsonObject {
                            put("type", "array")
                            put("description", "选项卡片列表（2~8 项；每项 label 为 1-5 词短标签，description 解释影响）")
                            put("items", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("label", buildJsonObject {
                                        put("type", "string")
                                        put("description", "选项短标签（1-5 词）")
                                    })
                                    put("description", buildJsonObject {
                                        put("type", "string")
                                        put("description", "选项详细描述：选择后的影响、后果或适用场景")
                                    })
                                    put("recommended", buildJsonObject {
                                        put("type", "boolean")
                                        put("description", "是否为推荐选项（将置顶并显示推荐徽章），默认 false")
                                    })
                                })
                                put("required", buildJsonArray {
                                    add("label")
                                })
                            })
                        })
                        put("multiple", buildJsonObject {
                            put("type", "boolean")
                            put("description", "是否允许多选，默认 false")
                        })
                        put("allowCustomAnswer", buildJsonObject {
                            put("type", "boolean")
                            put("description", "是否附加自由输入入口，默认 true")
                        })
                    })
                    put("required", buildJsonArray {
                        add("question")
                        add("options")
                    })
                })
            })
            put("timeoutSeconds", buildJsonObject {
                put("type", "integer")
                put("description", "单题等待超时秒数（5~300，默认 240）")
            })
            put("async", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否采用异步任务模式执行。默认为 false（保持同步阻塞挂起协程，等待真实用户在界面卡片中做出选择并确认后方可返回结果）。仅当外部客户端有极短超时限制时方可传入 true。")
            })
        })
        put("required", buildJsonArray {
            add("questions")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val isAsync = arguments["async"]?.jsonPrimitive?.booleanOrNull ?: false
        if (isAsync) {
            val jobId = McpJobManager.submitJob(name) { progressReporter ->
                executeInternal(arguments, progressReporter)
            }
            val initialJson = buildJsonObject {
                put("success", true)
                put("jobId", jobId)
                put("status", "PENDING")
                put("message", "任务已提交至后台异步执行，请调用 'get_job_status' 配合 waitSeconds 参数长轮询获取结果")
            }.toString()
            return ToolResult.text(initialJson)
        }
        return executeInternal(arguments, onProgress)
    }

    private suspend fun executeInternal(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val questionsArray = arguments["questions"]?.jsonArray
            ?: return ToolResult.error("参数 'questions' 不能为空且必须为数组")
        if (questionsArray.isEmpty()) {
            return ToolResult.error("questions 至少需要 1 个问题")
        }

        // 遵循人机决策阻塞铁律：默认无限挂起等待真实用户在界面卡片上做出决断，绝不因 240 秒超时而擅自跳过用户授权
        val timeoutMs = Long.MAX_VALUE

        val answers = mutableListOf<JsonObject>()
        for ((qIndex, qElement) in questionsArray.withIndex()) {
            val q = qElement.jsonObject
            val question = q["question"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.error("questions[$qIndex].question 不能为空")
            val header = q["header"]?.jsonPrimitive?.contentOrNull ?: ""
            val multiple = q["multiple"]?.jsonPrimitive?.booleanOrNull ?: false
            val allowCustom = q["allowCustomAnswer"]?.jsonPrimitive?.booleanOrNull ?: true

            val optionsList = q["options"]?.jsonArray
                ?.map { it.jsonObject }
                ?.map { opt ->
                    QuestionOption(
                        label = opt["label"]?.jsonPrimitive?.contentOrNull
                            ?: return ToolResult.error("questions[$qIndex] 存在缺少 label 的选项"),
                        description = opt["description"]?.jsonPrimitive?.contentOrNull ?: "",
                        recommended = opt["recommended"]?.jsonPrimitive?.booleanOrNull ?: false
                    )
                } ?: emptyList()

            if (optionsList.size < 2 || optionsList.size > 8) {
                return ToolResult.error("questions[$qIndex].options 数量必须为 2~8 项")
            }

            onProgress?.invoke(
                qIndex.toFloat() / questionsArray.size,
                "正在等待用户回答第 ${qIndex + 1}/${questionsArray.size} 题..."
            )

            val answer = McpInteractionBridge.requestQuestion(
                header = header,
                question = question,
                options = optionsList,
                multiple = multiple,
                allowCustomAnswer = allowCustom,
                timeoutMs = timeoutMs
            )

            if (answer == null) {
                val reason = if (!McpInteractionBridge.isUiReachable()) {
                    "ui_unreachable"
                } else {
                    "user_cancelled_or_timeout"
                }
                return ToolResult.success(
                    buildJsonObject {
                        put("success", true)
                        put("reason", reason)
                        put("answers", buildJsonArray {})
                    }.toString()
                )
            }

            if (answer.isTerminated) {
                return ToolResult.success(
                    buildJsonObject {
                        put("success", true)
                        put("reason", "user_terminated")
                        put("userMessage", "用户已明确拒绝当前方案并要求结束任务，中止后续所有活动，不要继续执行任何修改或排版。")
                        put("terminated", true)
                    }.toString()
                )
            }

            answers.add(
                buildJsonObject {
                    put("questionIndex", qIndex)
                    if (answer.isCustom) {
                        put("type", "custom")
                        put("customText", answer.customText.orEmpty())
                    } else {
                        put("type", "options")
                        put("selectedIndices", buildJsonArray {
                            answer.selectedIndices.forEach { add(it) }
                        })
                        put("selectedLabels", buildJsonArray {
                            answer.selectedLabels.forEach { add(it) }
                        })
                        val firstSelected = answer.selectedIndices.firstOrNull() ?: -1
                        put(
                            "followedRecommendation",
                            optionsList.getOrNull(firstSelected)?.recommended == true
                        )
                    }
                }
            )
        }

        return ToolResult.success(
            buildJsonObject {
                put("success", true)
                put("reason", "user_answered")
                put("answers", buildJsonArray { answers.forEach { add(it) } })
            }.toString()
        )
    }
}
