package com.example.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Native TFLite model-loading tests. These require an Android runtime with TFLite native libraries. */
@RunWith(AndroidJUnit4::class)
class SemanticEmbeddingProviderTfliteTest {

    @Test
    fun invalidModelFileIsRejectedWithoutCrashing() {
        val tempFile = File.createTempFile("invalid_model", ".tflite")
        try {
            FileOutputStream(tempFile).use { it.write(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)) }
            val provider = TFLiteSemanticEmbeddingProvider()
            assertFalse(provider.loadModelFromFile(tempFile))
            assertFalse(provider.isModelLoaded())
            provider.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun invalidModelBufferIsRejectedWithoutCrashing() {
        val buffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        buffer.put(ByteArray(16)).rewind()
        val provider = TFLiteSemanticEmbeddingProvider()
        assertFalse(provider.loadModelFromBuffer(buffer))
        assertFalse(provider.isModelLoaded())
        provider.close()
    }
}
