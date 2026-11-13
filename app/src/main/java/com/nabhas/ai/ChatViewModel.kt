package com.nabhas.ai

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nabhas.ai.api.ChatMessage
import com.nabhas.ai.api.ChatRequest
import com.nabhas.ai.api.OpenAiApiService
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val apiService = OpenAiApiService.create()

    val messages = mutableStateListOf<ChatMessage>()
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return

        messages.add(ChatMessage(role = "user", content = userInput))
        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            try {
                val response = apiService.chatCompletions(
                    authorization = "Bearer $API_KEY",
                    request = ChatRequest(messages = messages.toList())
                )

                val assistantMessage = response.choices?.firstOrNull()?.message
                if (assistantMessage != null) {
                    messages.add(assistantMessage)
                } else {
                    errorMessage.value = "No response received from the model."
                }
            } catch (e: Exception) {
                errorMessage.value = e.localizedMessage ?: "An unknown error occurred."
            } finally {
                isLoading.value = false
            }
        }
    }

    companion object {
        // Replace with your actual API key or load from secure storage / BuildConfig
        private const val API_KEY = "YOUR_API_KEY_HERE"
    }
}
