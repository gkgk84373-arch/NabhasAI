package com.nabhas.ai.api

import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface OpenAiApiService {

    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ChatResponse

    suspend fun chatCompletionsWithFallback(
        authorization: String,
        request: ChatRequest,
        primaryModel: String = request.model
    ): ModelCompletionResult {
        val normalizedPrimaryModel = primaryModel.trim().ifBlank { "gpt-6-luna" }
        val modelsToTry = (listOf(normalizedPrimaryModel) + ModelFallbackChain.models).distinct()
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

    companion object {
        private const val BASE_URL = "https://coarena.ai/api/v1/"

        fun create(apiKey: String = ""): OpenAiApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
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
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiApiService::class.java)
        }
    }
}
