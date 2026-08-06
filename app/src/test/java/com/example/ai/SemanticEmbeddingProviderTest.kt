package com.example.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SemanticEmbeddingProviderTest {

    private val provider = FallbackSemanticEmbeddingProvider()

    @Test
    fun `test float array to string and back`() {
        val original = floatArrayOf(0.1f, 0.5f, -0.2f)
        val str = provider.floatArrayToString(original)
        val reconstructed = provider.stringToFloatArray(str)
        
        assertTrue(reconstructed != null)
        assertArrayEquals(original, reconstructed!!, 0.0001f)
    }

    @Test
    fun `test cosine similarity`() {
        val v1 = floatArrayOf(1.0f, 0.0f)
        val v2 = floatArrayOf(1.0f, 0.0f)
        val v3 = floatArrayOf(0.0f, 1.0f)
        val v4 = floatArrayOf(-1.0f, 0.0f)

        val simSame = provider.calculateCosineSimilarity(v1, v2)
        assertEquals(1.0f, simSame, 0.001f)

        val simOrthogonal = provider.calculateCosineSimilarity(v1, v3)
        assertEquals(0.0f, simOrthogonal, 0.001f)

        val simOpposite = provider.calculateCosineSimilarity(v1, v4)
        assertEquals(-1.0f, simOpposite, 0.001f)
    }

    @Test
    fun `test fallback provider`() {
        assertFalse(provider.isModelLoaded())
        assertEquals(1, provider.embeddingVersion)
    }
}
