package io.github.t45k.trial.koog.a2a.client

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import java.util.UUID

suspend fun main() {
    val transport = HttpJSONRPCClientTransport(url = "http://localhost:8080/a2a")

    val agentCardResolver = UrlAgentCardResolver(
        baseUrl = "http://localhost:8080",
    )

    val client = A2AClient(transport, agentCardResolver)

    client.connect()
    val agentCard = client.cachedAgentCard()
    println("Connected to: ${agentCard.name}")

    val message = Message(
        messageId = UUID.randomUUID().toString(),
        role = Role.User,
        parts = listOf(TextPart("Hello, agent!")),
        contextId = "conversation-1"
    )

    val request = Request(data = MessageSendParams(message))
    val response = client.sendMessageStreaming(request)

    response.collect {
        when (val event = it.data) {
            is Message -> {
                val text = event.parts
                    .filterIsInstance<TextPart>()
                    .joinToString { it.text }
                println(text)
            }

            is TaskStatusUpdateEvent -> {
                event.status.message?.parts
                    ?.filterIsInstance<TextPart>()
                    ?.joinToString { it.text }
                    ?.let { println(it) }

                if (event.final) {
                    println("\nTask completed")
                }
            }

            else -> {
            }
        }
    }
}
