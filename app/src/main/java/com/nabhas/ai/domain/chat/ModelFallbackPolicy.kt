package com.nabhas.ai.domain.chat

object ModelFallbackPolicy {
    const val DEFAULT_PRIMARY_MODEL = "gpt-6-luna"

    val fallbackModels: List<String> = listOf(
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

    fun modelsToTry(primaryModel: String, useFallbackChain: Boolean): List<String> {
        val normalizedPrimary = primaryModel.trim().ifBlank { DEFAULT_PRIMARY_MODEL }
        return if (useFallbackChain) {
            (listOf(normalizedPrimary) + fallbackModels).distinct()
        } else {
            listOf(normalizedPrimary)
        }
    }
}
