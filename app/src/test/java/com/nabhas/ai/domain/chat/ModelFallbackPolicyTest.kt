package com.nabhas.ai.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelFallbackPolicyTest {
    @Test
    fun `fallback chain starts with normalized primary and does not duplicate it`() {
        val models = ModelFallbackPolicy.modelsToTry("  gemini-4-argon  ", useFallbackChain = true)

        assertEquals("gemini-4-argon", models.first())
        assertEquals(models.size, models.distinct().size)
        assertEquals("gpt-6-luna", models[1])
    }

    @Test
    fun `disabled fallback returns only normalized primary`() {
        assertEquals(
            listOf("custom-model"),
            ModelFallbackPolicy.modelsToTry(" custom-model ", useFallbackChain = false)
        )
    }

    @Test
    fun `blank primary uses default model`() {
        assertEquals(
            listOf(ModelFallbackPolicy.DEFAULT_PRIMARY_MODEL),
            ModelFallbackPolicy.modelsToTry("  ", useFallbackChain = false)
        )
    }
}
