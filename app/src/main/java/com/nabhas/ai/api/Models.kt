package com.nabhas.ai.api

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String = "gpt-6-luna",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ChatMessage?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

data class ModelCompletionResult(
    val modelName: String,
    val message: ChatMessage
)

object ModelFallbackChain {
    val models: List<String> = listOf(
        "gpt-6-luna",
        "gemini-3-8-flash",
        "deepseek-v4-1-flash-1017",
        "claude-sonnet-5-5",
        "claude-opus-5-5",
        "claude-fable-5-2",
        "claude-fable-5-1",
        "claude-fable-5",
        "claude-haiku-5-5",
        "gpt-6-2-sol",
        "gpt-6-2-astra",
        "gpt-6-1-terra",
        "gpt-6-1-sol",
        "gpt-6-astra",
        "gemini-4-carbon",
        "gemini-4-argon",
        "kimi-k3-1-pro",
        "grok-4-7",
        "mimo-v2-6-pro",
        "hy4"
    )
}
