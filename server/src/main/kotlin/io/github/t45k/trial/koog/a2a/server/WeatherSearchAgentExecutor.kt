package io.github.t45k.trial.koog.a2a.server

import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.agents.core.agent.AIAgent
import java.util.UUID

class WeatherSearchAgentExecutor(private val googleSearchToolAgent: AIAgent<String, String>) {
    companion object {
        val skill = AgentSkill(
            id = "weather-search",
            name = "Weather Search",
            description = "Searches for weather information for a specific date and location",
            tags = listOf("weather", "forecast", "天気"),
            examples = listOf("今日の大阪の天気は?", "明日の東京の天気を教えて"),
            inputModes = listOf("text"),
            outputModes = listOf("text"),
        )
    }

    suspend fun execute(
        request: RequestClassification.WeatherSearch,
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        eventProcessor.sendTaskEvent(
            Task(
                id = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(TaskState.Submitted),
            )
        )

        if (request.date == null || request.location == null) {
            val missingInfo = buildList {
                if (request.date == null) add("日付")
                if (request.location == null) add("地域名")
            }

            eventProcessor.sendTaskEvent(
                TaskStatusUpdateEvent(
                    taskId = context.taskId,
                    contextId = context.contextId,
                    status = TaskStatus(
                        state = TaskState.Completed,
                        message = Message(
                            messageId = UUID.randomUUID().toString(),
                            role = Role.Agent,
                            parts = listOf(TextPart("天気を検索するには${missingInfo.joinToString("と")}を指定してください。例: 「今日の大阪の天気は何ですか」")),
                            contextId = context.contextId,
                            taskId = context.taskId
                        )
                    ),
                    final = true
                )
            )
            return
        }

        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Working,
                    message = Message(
                        messageId = UUID.randomUUID().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("${request.date}の${request.location}の天気を検索します...")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    )
                ),
                final = false
            )
        )

        val weatherPrompt = """
            You are an assistant that searches for weather information.
            Please use the GoogleSearchTool to search for the following weather information.
            
            Make sure to include "site:weathernews.jp" in your search query.
            
            Date: ${request.date}
            Location: ${request.location}
            
            Based on the search results, please provide a concise summary of the weather information.
        """.trimIndent()

        val weatherResult = googleSearchToolAgent.run(weatherPrompt)

        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Completed,
                    message = Message(
                        messageId = UUID.randomUUID().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("$weatherResult")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    )
                ),
                final = true
            )
        )
    }
}
