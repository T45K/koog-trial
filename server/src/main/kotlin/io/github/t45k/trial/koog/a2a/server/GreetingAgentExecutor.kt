package io.github.t45k.trial.koog.a2a.server

import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.agents.core.agent.AIAgent
import java.util.UUID

class GreetingAgentExecutor(private val aiAgent: AIAgent<String, String>) {
    companion object {
        val skill = AgentSkill(
            id = "greetings",
            name = "Greetings",
            description = "Returns appropriate greetings when greeted",
            tags = listOf("greeting", "hello", "hi"),
            examples = listOf("Hello", "Hi", "Good morning", "Guten Tag"),
            inputModes = listOf("text"),
            outputModes = listOf("text"),
        )
    }

    suspend fun execute(
        request: RequestClassification.Greeting,
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        val greetingPrompt = """
            You are a helpful assistant.
            When greeted by a user, respond with an appropriate greeting in the same language.
            
            User's greeting: "${request.message}"
        """.trimIndent()

        val response = aiAgent.run(greetingPrompt)

        val message = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart(response)),
            contextId = context.contextId,
            taskId = context.taskId
        )

        eventProcessor.sendMessage(message)
    }
}
