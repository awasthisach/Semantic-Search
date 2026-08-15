package com.example.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
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
        assertEquals(1.0f, provider.calculateCosineSimilarity(v1, v2), 0.001f)
        assertEquals(0.0f, provider.calculateCosineSimilarity(v1, v3), 0.001f)
        assertEquals(-1.0f, provider.calculateCosineSimilarity(v1, v4), 0.001f)
    }

    @Test
    fun `test fallback provider`() {
        assertFalse(provider.isModelLoaded())
        assertEquals(1, provider.embeddingVersion)
    }

    @Test
    fun `test deterministic tokenizer and lightweight embedding generation`() {
        val emb1 = LightweightEmbeddingEngine.generateTextEmbedding("Quick brown fox")
        val emb2 = LightweightEmbeddingEngine.generateTextEmbedding("quick brown fox")
        val emb3 = LightweightEmbeddingEngine.generateTextEmbedding("Slower blue elephant")
        assertTrue(emb1 != null)
        assertTrue(emb2 != null)
        assertTrue(emb3 != null)
        assertEquals(128, emb1!!.size)
        assertEquals(128, emb2!!.size)
        assertEquals(128, emb3!!.size)
        assertArrayEquals(emb1, emb2!!, 0.00001f)
        assertEquals(1.0f, provider.calculateCosineSimilarity(emb1, emb2), 0.001f)
        assertTrue(provider.calculateCosineSimilarity(emb1, emb3!!) < 0.9f)
    }

    @Test
    fun `test vector L2 normalization`() {
        val vector = floatArrayOf(3.0f, 4.0f)
        var sumSquares = 0.0f
        for (v in vector) sumSquares += v * v
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        val normalized = FloatArray(vector.size) { vector[it] / norm }
        var normalizedSumSq = 0.0f
        for (v in normalized) normalizedSumSq += v * v
        assertEquals(1.0f, sqrt(normalizedSumSq.toDouble()).toFloat(), 0.0001f)
    }

    @Test
    fun `test similarity ranking logic`() {
        val query = floatArrayOf(1.0f, 0.0f)
        val doc1 = floatArrayOf(1.0f, 0.0f)
        val doc2 = floatArrayOf(0.707f, 0.707f)
        val doc3 = floatArrayOf(0.0f, 1.0f)
        val ranked = listOf(
            "doc3" to provider.calculateCosineSimilarity(query, doc3),
            "doc1" to provider.calculateCosineSimilarity(query, doc1),
            "doc2" to provider.calculateCosineSimilarity(query, doc2)
        ).sortedByDescending { it.second }
        assertEquals("doc1", ranked[0].first)
        assertEquals("doc2", ranked[1].first)
        assertEquals("doc3", ranked[2].first)
    }

    @Test
    fun `test fallback when model files are completely missing`() = runBlocking {
        val tfliteProvider = TFLiteSemanticEmbeddingProvider()
        assertFalse(tfliteProvider.isModelLoaded())
        assertNull(tfliteProvider.generateTextEmbedding("test"))
    }
}
