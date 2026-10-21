package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.*

/**
 * 结构化问询弹窗工具（对齐 OpenCode question 风格）：向用户展示带描述的选项卡片、推荐置顶、自定义输入并等待作答
 */
class AskUserQuestionTool : McpToolDefinition {

    override val name: String = "ask_user_question"

    override val description: String =
        "向真实用户展示结构化问询弹窗（对齐 OpenCode question 风格）并挂起等待作答。" +
        "支持多个问题顺序轮播、每个选项带 label 与 description 双行卡片、推荐选项置顶徽章、" +
        "单选/多选模式，以及用户自由输入自定义答案。适用于方案抉择、风格确认、破坏性操作前的意向收集。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val timeoutMs: Long = 300_000L

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
                put("description", "可选：是否采用异步任务模式执行。默认为 true（立即返回 jobId 并通过 get_job_status 长轮询进度，彻底免疫客户端超时）。传入 false 则保持同步阻塞等待。")
            })
        })
        put("required", buildJsonArray {
            add("questions")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val isAsync = arguments["async"]?.jsonPrimitive?.booleanOrNull ?: true
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
            return McpToolResult.text(initialJson)
        }
        return executeInternal(arguments, onProgress)
    }

    private suspend fun executeInternal(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val questionsArray = arguments["questions"]?.jsonArray
            ?: return McpToolResult.error("参数 'questions' 不能为空且必须为数组")
        if (questionsArray.isEmpty()) {
            return McpToolResult.error("questions 至少需要 1 个问题")
        }

        val timeoutSeconds = arguments["timeoutSeconds"]?.jsonPrimitive?.intOrNull ?: 240
        val timeoutMs = timeoutSeconds.coerceIn(5, 300) * 1000L

        val answers = mutableListOf<JsonObject>()
        for ((qIndex, qElement) in questionsArray.withIndex()) {
            val q = qElement.jsonObject
            val question = q["question"]?.jsonPrimitive?.contentOrNull
                ?: return McpToolResult.error("questions[$qIndex].question 不能为空")
            val header = q["header"]?.jsonPrimitive?.contentOrNull ?: ""
            val multiple = q["multiple"]?.jsonPrimitive?.booleanOrNull ?: false
            val allowCustom = q["allowCustomAnswer"]?.jsonPrimitive?.booleanOrNull ?: true

            val optionsList = q["options"]?.jsonArray
                ?.map { it.jsonObject }
                ?.map { opt ->
                    QuestionOption(
                        label = opt["label"]?.jsonPrimitive?.contentOrNull
                            ?: return McpToolResult.error("questions[$qIndex] 存在缺少 label 的选项"),
                        description = opt["description"]?.jsonPrimitive?.contentOrNull ?: "",
                        recommended = opt["recommended"]?.jsonPrimitive?.booleanOrNull ?: false
                    )
                } ?: emptyList()

            if (optionsList.size < 2 || optionsList.size > 8) {
                return McpToolResult.error("questions[$qIndex].options 数量必须为 2~8 项")
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
                return McpToolResult.success(
                    buildJsonObject {
                        put("success", true)
                        put("reason", reason)
                        put("answers", buildJsonArray {})
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

        return McpToolResult.success(
            buildJsonObject {
                put("success", true)
                put("reason", "user_answered")
                put("answers", buildJsonArray { answers.forEach { add(it) } })
            }.toString()
        )
    }
}
