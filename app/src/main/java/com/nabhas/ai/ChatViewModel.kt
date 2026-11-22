package com.nabhas.ai

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nabhas.ai.api.ChatMessage
import com.nabhas.ai.api.ChatRequest
import com.nabhas.ai.api.OpenAiApiService
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ApiSettings(
    val baseUrl: String = "https://coarena.ai/api/v1",
    val apiKey: String = "",
    val primaryModel: String = "gpt-6-luna",
    val useFallbackChain: Boolean = true
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = application.getSharedPreferences(
        PREFERENCES_NAME,
        Application.MODE_PRIVATE
    )
    private val mainHandler = Handler(Looper.getMainLooper())

    val messages = mutableStateListOf<ChatMessage>()
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    val apiSettings = mutableStateOf(loadApiSettings())
    val agentMode = mutableStateOf(false)
    val isListening = mutableStateOf(false)
    val voiceStatus = mutableStateOf("Voice input is idle.")
    val speakResponses = mutableStateOf(false)

    private var chatJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTextToSpeechReady = false

    private val restartListening = Runnable {
        if (isListening.value) {
            startRecognitionSession()
        }
    }

    init {
        textToSpeech = TextToSpeech(application) { status ->
            isTextToSpeechReady = status == TextToSpeech.SUCCESS
            if (isTextToSpeechReady) {
                textToSpeech?.language = Locale.getDefault()
            }
        }
    }

    fun saveApiSettings(settings: ApiSettings) {
        val sanitizedSettings = settings.copy(
            baseUrl = settings.baseUrl.trim().ifBlank { DEFAULT_BASE_URL },
            apiKey = settings.apiKey.trim(),
            primaryModel = settings.primaryModel.trim().ifBlank { DEFAULT_PRIMARY_MODEL }
        )

        preferences.edit()
            .putString(KEY_BASE_URL, sanitizedSettings.baseUrl)
            .putString(KEY_API_KEY, sanitizedSettings.apiKey)
            .putString(KEY_PRIMARY_MODEL, sanitizedSettings.primaryModel)
            .putBoolean(KEY_USE_FALLBACK_CHAIN, sanitizedSettings.useFallbackChain)
            .apply()

        apiSettings.value = sanitizedSettings
    }

    fun setAgentMode(enabled: Boolean) {
        agentMode.value = enabled
    }

    fun setSpeakResponses(enabled: Boolean) {
        speakResponses.value = enabled
        if (!enabled) {
            textToSpeech?.stop()
        }
    }

    fun sendMessage(userInput: String) {
        val content = userInput.trim()
        if (content.isEmpty() || isLoading.value) return

        val settings = apiSettings.value
        if (settings.apiKey.isBlank()) {
            errorMessage.value = "Add your API key in API settings before sending a message."
            return
        }

        messages.add(ChatMessage(role = "user", content = content))
        isLoading.value = true
        errorMessage.value = null

        val requestMessages = buildList {
            if (agentMode.value) {
                add(
                    ChatMessage(
                        role = "system",
                        content = AGENT_MODE_INSTRUCTION
                    )
                )
            }
            addAll(messages)
        }

        chatJob = viewModelScope.launch {
            try {
                val apiService = OpenAiApiService.create(baseUrl = settings.baseUrl)
                val result = apiService.chatCompletionsWithFallback(
                    authorization = "Bearer ${settings.apiKey}",
                    request = ChatRequest(
                        model = settings.primaryModel,
                        messages = requestMessages
                    ),
                    primaryModel = settings.primaryModel,
                    useFallbackChain = settings.useFallbackChain
                )

                val assistantMessage = result.message
                messages.add(
                    assistantMessage.copy(
                        content = "[Model: ${result.modelName}]\n${assistantMessage.content}"
                    )
                )
                speak(assistantMessage.content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value =
                    e.localizedMessage ?: "An unknown error occurred."
            } finally {
                isLoading.value = false
                chatJob = null
            }
        }
    }

    fun startContinuousListening() {
        if (isListening.value) return

        val application = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            voiceStatus.value = "Microphone permission is required for voice input."
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(application)) {
            voiceStatus.value = "Speech recognition is not available on this device."
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application).apply {
                setRecognitionListener(createRecognitionListener())
            }
            isListening.value = true
            voiceStatus.value = "Starting voice input..."
            startRecognitionSession()
        } catch (e: Exception) {
            isListening.value = false
            speechRecognizer?.destroy()
            speechRecognizer = null
            voiceStatus.value =
                e.localizedMessage ?: "Could not start voice input."
        }
    }

    fun stopContinuousListening() {
        isListening.value = false
        mainHandler.removeCallbacks(restartListening)
        speechRecognizer?.cancel()
        voiceStatus.value = "Voice input is idle."
    }

    fun clearChat() {
        chatJob?.cancel()
        chatJob = null
        messages.clear()
        errorMessage.value = null
        isLoading.value = false
    }

    private fun loadApiSettings(): ApiSettings {
        val storedModel = preferences.getString(KEY_PRIMARY_MODEL, null)
            ?: preferences.getString(KEY_LEGACY_MODEL, DEFAULT_PRIMARY_MODEL)
                .orEmpty()

        return ApiSettings(
            baseUrl = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL)
                .orEmpty()
                .ifBlank { DEFAULT_BASE_URL },
            apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
            primaryModel = storedModel
                .takeUnless { it == "default" }
                ?.ifBlank { DEFAULT_PRIMARY_MODEL }
                ?: DEFAULT_PRIMARY_MODEL,
            useFallbackChain = preferences.getBoolean(KEY_USE_FALLBACK_CHAIN, true)
        )
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (isListening.value) {
                    voiceStatus.value = "Listening..."
                }
            }

            override fun onBeginningOfSpeech() {
                if (isListening.value) {
                    voiceStatus.value = "Hearing speech..."
                }
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (isListening.value) {
                    voiceStatus.value = "Processing speech..."
                }
            }

            override fun onError(error: Int) {
                if (!isListening.value) return

                if (isRecoverableSpeechError(error)) {
                    voiceStatus.value = "Listening..."
                    mainHandler.removeCallbacks(restartListening)
                    mainHandler.postDelayed(restartListening, LISTENING_RESTART_DELAY_MS)
                } else {
                    isListening.value = false
                    voiceStatus.value = speechErrorMessage(error)
                    speechRecognizer?.cancel()
                }
            }

            override fun onResults(results: Bundle?) {
                if (!isListening.value) return

                val recognizedText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()

                if (!recognizedText.isNullOrBlank()) {
                    sendMessage(recognizedText)
                }

                voiceStatus.value = "Listening..."
                mainHandler.removeCallbacks(restartListening)
                mainHandler.postDelayed(restartListening, LISTENING_RESTART_DELAY_MS)
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
    }

    private fun startRecognitionSession() {
        if (!isListening.value) return

        try {
            speechRecognizer?.startListening(createRecognitionIntent())
                ?: run {
                    isListening.value = false
                    voiceStatus.value = "Voice input is not initialized."
                }
        } catch (e: Exception) {
            isListening.value = false
            voiceStatus.value =
                e.localizedMessage ?: "Could not continue voice input."
        }
    }

    private fun createRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    private fun isRecoverableSpeechError(error: Int): Boolean {
        return error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
    }

    private fun speechErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is required for voice input."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Network error during speech recognition."
            SpeechRecognizer.ERROR_AUDIO ->
                "Audio recording failed."
            SpeechRecognizer.ERROR_CLIENT ->
                "Speech recognition stopped unexpectedly."
            SpeechRecognizer.ERROR_SERVER ->
                "The speech recognition service encountered an error."
            else ->
                "Speech recognition failed (error $error)."
        }
    }

    private fun speak(text: String) {
        if (!speakResponses.value || !isTextToSpeechReady || text.isBlank()) return

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "assistant-response-${System.currentTimeMillis()}"
        )
    }

    override fun onCleared() {
        stopContinuousListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onCleared()
    }

    companion object {
        private const val PREFERENCES_NAME = "nabhas_ai_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PRIMARY_MODEL = "primary_model"
        private const val KEY_LEGACY_MODEL = "model"
        private const val KEY_USE_FALLBACK_CHAIN = "use_fallback_chain"
        private const val DEFAULT_BASE_URL = "https://coarena.ai/api/v1"
        private const val DEFAULT_PRIMARY_MODEL = "gpt-6-luna"
        private const val LISTENING_RESTART_DELAY_MS = 500L

        private const val AGENT_MODE_INSTRUCTION =
            "Act as a careful, goal-oriented assistant. Break complex tasks into " +
                "clear steps, check your reasoning, and provide a useful final answer."
    }
}
