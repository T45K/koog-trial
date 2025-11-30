package io.github.t45k.trial.koog.a2a.client

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
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
    println("=".repeat(60))
    println("Connected to: ${agentCard.name}")
    println("Skills: ${agentCard.skills.joinToString { it.name }}")
    println("=".repeat(60))

    // Test 1: German greeting
    println("\n【Test 1】ドイツ語で挨拶を送る")
    println("-".repeat(60))
    sendMessageAndPrintResponse(client, "Guten Tag! Wie geht es Ihnen?")
    println("→ 期待: Greetings Skillが利用され、ドイツ語で挨拶が返ってくる")

    // Test 2: Weather without location
    println("\n【Test 2】地域を指定せずに天気を訊く")
    println("-".repeat(60))
    sendMessageAndPrintResponse(client, "今日の天気は何ですか")
    println("→ 期待: Weather Search Skillが利用され、地域を指定するように言われる")

    // Test 3: Weather with date and location
    println("\n【Test 3】日付と地域を指定して天気を訊く")
    println("-".repeat(60))
    sendMessageAndPrintResponse(client, "今日の大阪の天気は何ですか")
    println("→ 期待: Weather Search Skillが利用され、天気情報が返ってくる")

    // Test 4: Unrelated question (dinner menu)
    println("\n【Test 4】関係ない質問（晩御飯の献立）を送る")
    println("-".repeat(60))
    sendMessageAndPrintResponse(client, "今日の晩御飯の献立を教えてください")
    println("→ 期待: 挨拶か天気について訊くように言われる")

    println("\n" + "=".repeat(60))
    println("All tests completed!")
    println("=".repeat(60))
}

private suspend fun sendMessageAndPrintResponse(client: A2AClient, text: String) {
    println("📤 送信: $text")
    println()

    val message = Message(
        messageId = UUID.randomUUID().toString(),
        role = Role.User,
        parts = listOf(TextPart(text)),
        contextId = "conversation-${UUID.randomUUID()}"
    )

    val request = Request(data = MessageSendParams(message))
    val response = client.sendMessageStreaming(request)

    response.collect { eventResponse ->
        when (val event = eventResponse.data) {
            is Message -> {
                val responseText = event.parts
                    .filterIsInstance<TextPart>()
                    .joinToString { part -> part.text }
                println("📥 レスポンス (Message): $responseText")
            }

            is TaskStatusUpdateEvent -> {
                event.status.message?.parts
                    ?.filterIsInstance<TextPart>()
                    ?.joinToString { part -> part.text }
                    ?.let { responseText ->
                        println("📥 レスポンス (TaskEvent - ${event.status.state}): $responseText")
                    }

                if (event.final) {
                    println("✅ Task completed")
                }
            }

            else -> {
                println("📥 その他のイベント: $event")
            }
        }
    }
    println()
}
