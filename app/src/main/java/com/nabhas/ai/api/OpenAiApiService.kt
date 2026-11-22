package com.nabhas.ai.api

import com.nabhas.ai.domain.chat.ModelFallbackPolicy
import com.nabhas.ai.domain.model.ModelCompletionResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAiApiService {

    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ChatResponse

    companion object {
        private const val BASE_URL = "https://coarena.ai/api/v1"

        fun create(baseUrl: String = BASE_URL): OpenAiApiService {
            val normalizedBaseUrl = baseUrl.trim().let { value ->
                if (value.endsWith("/")) value else "$value/"
            }

            // Do not log HTTP headers or bodies: requests contain bearer credentials and private chat text.
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Content-Type", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiApiService::class.java)
        }
    }
}

/** Tries the configured primary model and, when enabled, the remaining fallback models. */
suspend fun OpenAiApiService.chatCompletionsWithFallback(
    authorization: String,
    request: ChatRequest,
    primaryModel: String = request.model,
    useFallbackChain: Boolean = true
): ModelCompletionResult {
    val modelsToTry = ModelFallbackPolicy.modelsToTry(primaryModel, useFallbackChain)
    var lastFailure: Exception? = null

    for (modelName in modelsToTry) {
        try {
            val response = chatCompletions(
                authorization = authorization,
                request = request.copy(model = modelName)
            )

            val message = response.choices
                ?.firstNotNullOfOrNull { choice ->
                    choice.message?.takeIf { it.content.isNotBlank() }
                }

            if (message != null) {
                return ModelCompletionResult(
                    modelName = modelName,
                    message = message
                )
            }

            lastFailure = IOException("Model $modelName returned an empty response.")
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            lastFailure = exception
        }
    }

    throw lastFailure ?: IOException("All configured models returned empty responses.")
}
