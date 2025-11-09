package io.github.t45k.trial.koog.a2a.server

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
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
    )
}

class SayHelloWorldAgentExecutor : AgentExecutor {
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        val response = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart("Hello World")),
            contextId = context.contextId,
            taskId = context.taskId
        )

        eventProcessor.sendMessage(response)
    }
}

/*
val apiKey = System.getenv("GEMINI_API_KEY")

private fun prepareAgent(apiKey: String): AIAgent<String, String> = AIAgent(
    simpleGoogleAIExecutor(apiKey),
    GoogleModels.Gemini2_5Flash,
    systemPrompt = """
            You are an **expert debater** and a master of persuasive argumentation.

            Your primary goal is to **construct a robust and convincing counter-argument** to the user's stated position.

            The user will provide an argument in the following format: "Regarding A and B, I support A because [User's reasoning]."

            **Your Task:**
            1.  **Strictly focus on supporting position B.** You must argue *against* the user's stated position (A).
            2.  Analyze the user's stated reasoning for position A.
            3.  Generate **3 to 5 powerful and persuasive counter-arguments** to support position B, aimed at convincing a neutral audience.
            4.  Each point should be distinct, logically sound, and aimed at undermining the validity or desirability of position A, while emphasizing the merits of position B.

            **Format your output as a clear, numbered list of arguments.**
        """.trimIndent(),
)
*/
