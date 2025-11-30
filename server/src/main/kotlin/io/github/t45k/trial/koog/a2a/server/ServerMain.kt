package io.github.t45k.trial.koog.a2a.server

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import java.util.UUID
import io.ktor.server.cio.CIO as ServerCIO

private const val PORT = 8080
private const val A2A_ENDPOINT = "/a2a"

suspend fun main() {
    val agentCard = AgentCard(
        name = "Greeting and Weather Search Agent",
        description = "An agent that can greet you or search for weather information",
        url = "http://localhost:$PORT$A2A_ENDPOINT",
        version = "0.0.1",
        capabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = true,
            stateTransitionHistory = true,
        ),
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(
            GreetingAgentExecutor.skill,
            WeatherSearchAgentExecutor.skill,
        ),
    )

    val geminiApiKey = System.getenv("GEMINI_API_KEY") ?: ""
    val googleCustomSearchApiKey = System.getenv("GOOGLE_CUSTOM_SEARCH_API_KEY") ?: ""
    val cx = System.getenv("CX") ?: ""

    val requestClassifierAgent = RequestClassifierAgent(createWithoutTools(geminiApiKey))
    val greetingAgentExecutor = GreetingAgentExecutor(createWithoutTools(geminiApiKey))
    val weatherSearchAgentExecutor = WeatherSearchAgentExecutor(createWithGoogleSearchTool(geminiApiKey, googleCustomSearchApiKey, cx))
    val greetingAndWeatherSearchAgentExecutor = GreetingAndWeatherSearchAgentExecutor(
        requestClassifierAgent,
        greetingAgentExecutor,
        weatherSearchAgentExecutor,
    )

    val server = A2AServer(
        greetingAndWeatherSearchAgentExecutor,
        agentCard,
    )

    HttpJSONRPCServerTransport(server).start(
        engineFactory = ServerCIO,
        port = PORT,
        path = A2A_ENDPOINT,
        wait = true,
        agentCard = agentCard,
    )
}

class GreetingAndWeatherSearchAgentExecutor(
    private val requestClassifierAgent: RequestClassifierAgent,
    private val greetingAgentExecutor: GreetingAgentExecutor,
    private val weatherSearchAgentExecutor: WeatherSearchAgentExecutor,
) : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        val userMessage = context.params.message
        val userInput = userMessage.parts
            .filterIsInstance<TextPart>()
            .joinToString(" ") { it.text }

        when (val classification = requestClassifierAgent.execute(userInput)) {
            is RequestClassification.Greeting -> greetingAgentExecutor.execute(classification, context, eventProcessor)
            is RequestClassification.WeatherSearch -> weatherSearchAgentExecutor.execute(classification, context, eventProcessor)
            RequestClassification.Other -> {
                val message = Message(
                    messageId = UUID.randomUUID().toString(),
                    role = Role.Agent,
                    parts = listOf(TextPart("申し訳ありませんが、そのリクエストには対応できません。挨拶をするか、天気について質問してください。")),
                    contextId = context.contextId,
                    taskId = context.taskId
                )

                eventProcessor.sendMessage(message)
            }
        }
    }
}
