package io.github.t45k.trial.koog.a2a.server

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import io.ktor.server.cio.CIO
import java.util.UUID

suspend fun main() {
    val agentCard = AgentCard(
        name = "Hello World",
        description = "Say Hello World",
        url = "http://localhost:8080/a2a",
        version = "0.0.1",
        capabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = true,
            stateTransitionHistory = true,
        ),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            AgentSkill(
                id = "hello",
                name = "Hello",
                description = "Say Hello World",
                tags = listOf("hello", "world"),
                examples = listOf("Hello World"),
                inputModes = listOf("text"),
                outputModes = listOf("text"),
            )
        ),
    )

    val sayHelloWorldAgentExecutor = SayHelloWorldAgentExecutor()

    val server = A2AServer(
        sayHelloWorldAgentExecutor,
        agentCard,
    )

    HttpJSONRPCServerTransport(server).start(
        engineFactory = CIO,
        port = 8080,
        path = "/a2a",
        wait = true,
        agentCard = agentCard,
    )
}

class SayHelloWorldAgentExecutor : AgentExecutor {
    override suspend fun execute(
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

        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Submitted,
                    message = Message(
                        messageId = UUID.randomUUID().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("Hello World from server")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    )
                ),
                final = false
            )
        )

        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Working,
                    message = Message(
                        messageId = UUID.randomUUID().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("Hello World from server 2")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    )
                ),
                final = false
            )
        )

        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(TaskState.Completed),
                final = true,
            )
        )

        /*
        val response = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart("Hello World from server")),
            contextId = context.contextId,
            taskId = context.taskId
        )

        eventProcessor.sendMessage(response
         */
    }
}
