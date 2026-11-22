package com.nabhas.ai.domain.model

data class ApiSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val primaryModel: String = DEFAULT_PRIMARY_MODEL,
    val useFallbackChain: Boolean = true
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://coarena.ai/api/v1"
        const val DEFAULT_PRIMARY_MODEL = "gpt-6-luna"
    }
}
