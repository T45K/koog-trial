package io.github.t45k.trial.koog.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

suspend fun main() {
    val geminiApiKey = System.getenv("GEMINI_API_KEY")
    val aiAgent: AIAgent<String, String> = AIAgent(
        simpleGoogleAIExecutor(geminiApiKey),
        GoogleModels.Gemini2_5Flash,
        singleRunStrategy(),
        ToolRegistry {
            tool(
                GoogleSearchTool(
                    apiKey = System.getenv("GOOGLE_CUSTOM_SEARCH_API_KEY"),
                    cx = System.getenv("CX"),
                )
            )
        },
    )

    val result = aiAgent.run(
        """
            I want to know the weather, high temperature, and low temperature in Osaka.
            Today is ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}.
            What will tomorrow's weather, high temperature, and low temperature be like in Osaka?
        """.trimIndent()
    )
    println(result)
}

@Serializable
data class GoogleSearchArgs(val query: String, val num: Int = 5)

class GoogleSearchTool(
    private val apiEndpoint: String = "https://www.googleapis.com/customsearch/v1",
    private val apiKey: String,
    private val cx: String
) : Tool<GoogleSearchArgs, String>() {
    override val name: String = "googleSearch"
    override val description: String = "Execute web search and return a summary of top results. { query: string, num?: number }"

    override val argsSerializer: KSerializer<GoogleSearchArgs> = GoogleSearchArgs.serializer()
    override val resultSerializer: KSerializer<String> = String.serializer()

    private val client = HttpClient(CIO)

    override suspend fun execute(args: GoogleSearchArgs): String {
        val response: HttpResponse = client.get(apiEndpoint) {
            parameter("key", apiKey)
            parameter("cx", cx)
            parameter("q", args.query)
            parameter("num", args.num)
        }
        return response.bodyAsText()
    }
}
