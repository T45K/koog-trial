package io.github.t45k.trial.koog.a2a.server

import ai.koog.agents.core.agent.AIAgent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RequestClassifierAgent(private val aiAgent: AIAgent<String, String>) {

    suspend fun execute(request: String): RequestClassification {
        val classificationPrompt = """
            You are an assistant that classifies input.
            Analyze the user input below and return the result in JSON format.
            
            Classification Rules:
            1. For greetings (Hello, Hi, Good morning, こんにちは, Guten Tag, etc.), use type: "GREETING"
            2. For weather-related questions, use type: "WEATHER"
               - Weather questions require date and location
               - If a date is included, set date: "date value"
               - If a location is included, set location: "location name"
               - If not included, set them to null
            3. For everything else, use type: "OTHER"
            
            Return only JSON. Do not include any other text.
            
            Examples:
            Input: "Guten Tag"
            Output: {"type": "GREETING", "date": null, "location": null}
            
            Input: "今日の天気は何ですか"
            Output: {"type": "WEATHER", "date": "今日", "location": null}
            
            Input: "今日の大阪の天気は何ですか"
            Output: {"type": "WEATHER", "date": "今日", "location": "大阪"}
            
            Input: "晩御飯の献立を教えて"
            Output: {"type": "OTHER", "date": null, "location": null}
            
            User Input: "$request"
        """.trimIndent()

        val result = aiAgent.run(classificationPrompt)

        return try {
            val jsonString = result.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val aiAgentOutput = Json.decodeFromString<AIAgentOutput>(jsonString)

            when (aiAgentOutput.type) {
                "GREETING" -> RequestClassification.Greeting(request)
                "WEATHER" -> RequestClassification.WeatherSearch(aiAgentOutput.date, aiAgentOutput.location)
                else -> RequestClassification.Other // Including "OTHER" type
            }
        } catch (_: Exception) {
            RequestClassification.Other
        }
    }
}

@Serializable
private data class AIAgentOutput(
    val type: String,
    val date: String? = null,
    val location: String? = null
)
