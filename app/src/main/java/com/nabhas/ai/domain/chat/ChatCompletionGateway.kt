package com.nabhas.ai.domain.chat

import com.nabhas.ai.domain.model.ApiSettings
import com.nabhas.ai.domain.model.ChatMessage
import com.nabhas.ai.domain.model.ModelCompletionResult

/** Provider-independent boundary used by the chat orchestration layer. */
interface ChatCompletionGateway {
    suspend fun complete(
        settings: ApiSettings,
        messages: List<ChatMessage>
    ): ModelCompletionResult
}
