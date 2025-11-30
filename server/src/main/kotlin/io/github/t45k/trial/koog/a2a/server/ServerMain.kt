package io.github.t45k.trial.koog.a2a.server

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
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
import io.ktor.server.cio.CIO as ServerCIO
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.util.UUID

suspend fun main() {
    val agentCard = AgentCard(
        name = "Greeting and Weather Agent",
        description = "An agent that can greet you or search for weather information",
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
                id = "greetings",
                name = "Greetings",
                description = "Returns appropriate greetings when greeted",
                tags = listOf("greeting", "hello", "hi"),
                examples = listOf("Hello", "Hi", "Good morning", "Guten Tag"),
                inputModes = listOf("text"),
                outputModes = listOf("text"),
            ),
            AgentSkill(
                id = "weather-search",
                name = "Weather Search",
                description = "Searches for weather information for a specific date and location",
                tags = listOf("weather", "forecast", "天気"),
                examples = listOf("今日の大阪の天気は?", "明日の東京の天気を教えて"),
                inputModes = listOf("text"),
                outputModes = listOf("text"),
            )
        ),
    )

    val greetingAndWeatherAgentExecutor = GreetingAndWeatherAgentExecutor()

    val server = A2AServer(
        greetingAndWeatherAgentExecutor,
        agentCard,
    )

    HttpJSONRPCServerTransport(server).start(
        engineFactory = ServerCIO,
        port = 8080,
        path = "/a2a",
        wait = true,
        agentCard = agentCard,
    )
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

enum class InputType {
    GREETING,
    WEATHER,
    OTHER;

    companion object {
        fun fromString(value: String): InputType = when (value.uppercase()) {
            "GREETING" -> GREETING
            "WEATHER" -> WEATHER
            else -> OTHER
        }
    }
}

@Serializable
data class ClassificationResult(
    val type: String,
    val hasDate: Boolean = false,
    val hasLocation: Boolean = false,
    val date: String = "",
    val location: String = ""
) {
    val inputType: InputType
        get() = InputType.fromString(type)
}

class GreetingAndWeatherAgentExecutor : AgentExecutor {
    private val geminiApiKey = System.getenv("GEMINI_API_KEY") ?: ""
    private val googleSearchApiKey = System.getenv("GOOGLE_CUSTOM_SEARCH_API_KEY") ?: ""
    private val cx = System.getenv("CX") ?: ""

    private fun createClassifierAgent(): AIAgent<String, String> {
        return AIAgent(
            simpleGoogleAIExecutor(geminiApiKey),
            GoogleModels.Gemini2_5Flash,
            singleRunStrategy(),
            ToolRegistry { },
        )
    }

    private fun createGreetingAgent(): AIAgent<String, String> {
        return AIAgent(
            simpleGoogleAIExecutor(geminiApiKey),
            GoogleModels.Gemini2_5Flash,
            singleRunStrategy(),
            ToolRegistry { },
        )
    }

    private fun createWeatherAgent(): AIAgent<String, String> {
        return AIAgent(
            simpleGoogleAIExecutor(geminiApiKey),
            GoogleModels.Gemini2_5Flash,
            singleRunStrategy(),
            ToolRegistry {
                tool(GoogleSearchTool(apiKey = googleSearchApiKey, cx = cx))
            },
        )
    }

    private suspend fun classifyInput(userInput: String): ClassificationResult {
        val classifierAgent = createClassifierAgent()
        val classificationPrompt = """
            あなたは入力を分類するアシスタントです。
            以下のユーザー入力を分析し、JSON形式で結果を返してください。
            
            分類ルール:
            1. 挨拶（Hello, Hi, Good morning, こんにちは, Guten Tag など）の場合は type: "GREETING"
            2. 天気に関する質問の場合は type: "WEATHER"
               - 天気の質問には日付と地域が必要です
               - 日付が含まれている場合は hasDate: true, date: "日付"
               - 地域が含まれている場合は hasLocation: true, location: "地域名"
            3. それ以外は type: "OTHER"
            
            JSONのみを返してください。他のテキストは含めないでください。
            
            例:
            入力: "Guten Tag"
            出力: {"type": "GREETING", "hasDate": false, "hasLocation": false, "date": "", "location": ""}
            
            入力: "今日の天気は何ですか"
            出力: {"type": "WEATHER", "hasDate": true, "hasLocation": false, "date": "今日", "location": ""}
            
            入力: "今日の大阪の天気は何ですか"
            出力: {"type": "WEATHER", "hasDate": true, "hasLocation": true, "date": "今日", "location": "大阪"}
            
            入力: "晩御飯の献立を教えて"
            出力: {"type": "OTHER", "hasDate": false, "hasLocation": false, "date": "", "location": ""}
            
            ユーザー入力: "$userInput"
        """.trimIndent()

        val result = classifierAgent.run(classificationPrompt)
        
        return try {
            val jsonString = result.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            kotlinx.serialization.json.Json.decodeFromString<ClassificationResult>(jsonString)
        } catch (e: Exception) {
            ClassificationResult(type = "OTHER")
        }
    }

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        val userMessage = context.params.message
        val userInput = userMessage.parts
            .filterIsInstance<TextPart>()
            .joinToString(" ") { part -> part.text }

        val classification = classifyInput(userInput)

        when (classification.inputType) {
            InputType.GREETING -> handleGreeting(context, eventProcessor, userInput)
            InputType.WEATHER -> handleWeather(context, eventProcessor, userInput, classification)
            InputType.OTHER -> handleOther(context, eventProcessor)
        }
    }

    private suspend fun handleGreeting(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
        userInput: String
    ) {
        val greetingAgent = createGreetingAgent()
        val greetingPrompt = """
            あなたは親切なアシスタントです。
            ユーザーからの挨拶に対して、同じ言語で適切な挨拶を返してください。
            
            ユーザーの挨拶: "$userInput"
        """.trimIndent()

        val response = greetingAgent.run(greetingPrompt)

        val message = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart("[Greetings Skill] $response")),
            contextId = context.contextId,
            taskId = context.taskId
        )

        eventProcessor.sendMessage(message)
    }

    private suspend fun handleWeather(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
        userInput: String,
        classification: ClassificationResult
    ) {
        eventProcessor.sendTaskEvent(
            Task(
                id = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(TaskState.Submitted),
            )
        )

        if (!classification.hasDate || !classification.hasLocation) {
            val missingInfo = mutableListOf<String>()
            if (!classification.hasDate) missingInfo.add("日付")
            if (!classification.hasLocation) missingInfo.add("地域名")

            eventProcessor.sendTaskEvent(
                TaskStatusUpdateEvent(
                    taskId = context.taskId,
                    contextId = context.contextId,
                    status = TaskStatus(
                        state = TaskState.Completed,
                        message = Message(
                            messageId = UUID.randomUUID().toString(),
                            role = Role.Agent,
                            parts = listOf(TextPart("[Weather Search Skill] 天気を検索するには${missingInfo.joinToString("と")}を指定してください。例: 「今日の大阪の天気は何ですか」")),
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
                        parts = listOf(TextPart("[Weather Search Skill] ${classification.date}の${classification.location}の天気を検索します...")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    )
                ),
                final = false
            )
        )

        val weatherAgent = createWeatherAgent()
        val weatherPrompt = """
            あなたは天気情報を検索するアシスタントです。
            GoogleSearchToolを使って、以下の天気情報を検索してください。
            
            検索クエリには必ず "site:weathernews.jp" を含めてください。
            
            日付: ${classification.date}
            地域: ${classification.location}
            
            検索結果をもとに、天気情報を簡潔にまとめて返答してください。
        """.trimIndent()

        val weatherResult = weatherAgent.run(weatherPrompt)

        eventProcessor.sendTaskEvent(
            TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Completed,
                    message = Message(
                        messageId = UUID.randomUUID().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("[Weather Search Skill] $weatherResult")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    )
                ),
                final = true
            )
        )
    }

    private suspend fun handleOther(
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
                    state = TaskState.Completed,
                    message = Message(
                        messageId = UUID.randomUUID().toString(),
                        role = Role.Agent,
                        parts = listOf(TextPart("申し訳ありませんが、そのリクエストには対応できません。挨拶をするか、天気について質問してください。")),
                        contextId = context.contextId,
                        taskId = context.taskId
                    )
                ),
                final = true
            )
        )
    }
}
