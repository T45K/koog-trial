package io.github.t45k.trial.koog.a2a.server

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_5Flash
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import io.github.t45k.koog.tool.GoogleSearchTool

fun createWithoutTools(geminiApiKey: String) = create(geminiApiKey) {}

fun createWithGoogleSearchTool(geminiApiKey: String, googleCustomSearchApiKey: String, cx: String) =
    create(geminiApiKey) {
        tool(GoogleSearchTool(apiKey = googleCustomSearchApiKey, cx = cx))
    }

private fun create(geminiApiKey: String, toolRegistry: ToolRegistry.Builder.() -> Unit) = AIAgent(
    simpleGoogleAIExecutor(geminiApiKey),
    Gemini2_5Flash,
    singleRunStrategy(),
    ToolRegistry { toolRegistry() }
)
