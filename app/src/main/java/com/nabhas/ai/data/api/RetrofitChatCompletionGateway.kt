package com.nabhas.ai.data.api

import com.nabhas.ai.api.ChatRequest
import com.nabhas.ai.api.OpenAiApiService
import com.nabhas.ai.api.chatCompletionsWithFallback
import com.nabhas.ai.domain.chat.ChatCompletionGateway
import com.nabhas.ai.domain.model.ApiSettings
import com.nabhas.ai.domain.model.ChatMessage
import com.nabhas.ai.domain.model.ModelCompletionResult

/** Existing OpenAI-compatible HTTP path behind a provider-neutral domain contract. */
class RetrofitChatCompletionGateway : ChatCompletionGateway {
    override suspend fun complete(
        settings: ApiSettings,
        messages: List<ChatMessage>
    ): ModelCompletionResult {
        val service = OpenAiApiService.create(baseUrl = settings.baseUrl)
        return service.chatCompletionsWithFallback(
            authorization = "Bearer ${settings.apiKey}",
            request = ChatRequest(
                model = settings.primaryModel,
                messages = messages
            ),
            primaryModel = settings.primaryModel,
            useFallbackChain = settings.useFallbackChain
        )
    }
}
