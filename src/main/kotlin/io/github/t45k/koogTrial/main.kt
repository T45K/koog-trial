package io.github.t45k.koogTrial

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor

suspend fun main() {
    val apiKey = System.getenv("GEMINI_API_KEY")

    val result = prepareAgent(apiKey).run("私は犬と猫だと猫が好きです。なぜなら、可愛いからです")
    println(result)

    val result2 = prepareAgent(apiKey).run(result)
    println(result2)

    val result3 = prepareAgent(apiKey).run(result2)
    println(result3)
}

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
