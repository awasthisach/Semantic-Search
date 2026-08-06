package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Interface for AI Semantic Duplicate Detection.
 * Generates vector embeddings for images, documents, and text.
 */
interface SemanticEmbeddingProvider {
    val embeddingVersion: Int
    fun isModelLoaded(): Boolean
    suspend fun generateImageEmbedding(file: File): FloatArray?
    suspend fun generateTextEmbedding(text: String): FloatArray?

    /**
     * Calculates cosine similarity between two float vector embeddings (range -1.0 to 1.0).
     */
    fun calculateCosineSimilarity(emb1: FloatArray, emb2: FloatArray): Float {
        if (emb1.size != emb2.size || emb1.isEmpty()) return 0.0f
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in emb1.indices) {
            dotProduct += emb1[i] * emb2[i]
            normA += emb1[i] * emb1[i]
            normB += emb2[i] * emb2[i]
        }
        if (normA == 0.0f || normB == 0.0f) return 0.0f
        return (dotProduct / (sqrt(normA.toDouble()) * sqrt(normB.toDouble()))).toFloat()
    }

    /**
     * Converts FloatArray embedding vector to comma-separated String for Room database storage.
     */
    fun floatArrayToString(vector: FloatArray): String {
        return vector.joinToString(",")
    }

    /**
     * Parses comma-separated String from Room database into FloatArray embedding vector.
     */
    fun stringToFloatArray(str: String): FloatArray? {
        if (str.isBlank()) return null
        return try {
            str.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Memory-safe Fallback Semantic Embedding Provider used when TFLite model is missing.
 * Avoids any JNI, Native Library loads, or ashmem operations.
 */
class FallbackSemanticEmbeddingProvider : SemanticEmbeddingProvider {
    override val embeddingVersion: Int = 1
    override fun isModelLoaded(): Boolean = false
    override suspend fun generateImageEmbedding(file: File): FloatArray? = null
    override suspend fun generateTextEmbedding(text: String): FloatArray? = null
}

/**
 * Real TFLite / On-Device AI Embedding Engine Implementation (Step 6).
 * Manages TFLite Interpreter inference pipeline with graceful fallback when model is missing.
 */
class TFLiteSemanticEmbeddingProvider(
    modelFile: File? = null
) : SemanticEmbeddingProvider {
    override val embeddingVersion: Int = 1

    private var interpreter: Interpreter? = null
    private var vectorDimension: Int = 512

    init {
        if (modelFile != null && modelFile.exists() && modelFile.canRead()) {
            loadModelFromFile(modelFile)
        }
    }

    /**
     * Safely attempts to initialize the TFLite Interpreter from a model file.
     * Prevents any runtime crashes if the file is invalid or unsupported.
     * Bypasses ashmem memory pinning by loading model into a direct ByteBuffer.
     */
    fun loadModelFromFile(modelFile: File): Boolean {
        return try {
            val bytes = modelFile.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            loadModelFromBuffer(buffer)
        } catch (e: Exception) {
            Log.w("TFLiteSemantic", "Failed to load TFLite model from file: ${e.message}")
            interpreter = null
            false
        }
    }

    /**
     * Safely attempts to initialize the TFLite Interpreter from an Android Asset file.
     * Guarantees no crashes if the asset is missing or corrupted.
     * Bypasses ashmem memory pinning on Android Q+ by loading model into a direct ByteBuffer.
     */
    fun loadModelFromAssets(context: Context, assetName: String = "mobile_clip_embedding.tflite"): Boolean {
        return try {
            val inputStream = context.assets.open(assetName)
            val bytes = inputStream.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            loadModelFromBuffer(buffer)
        } catch (e: Exception) {
            Log.i("TFLiteSemantic", "TFLite model asset '$assetName' not found in assets (optional feature): ${e.message}")
            interpreter = null
            false
        }
    }

    fun loadModelFromBuffer(buffer: ByteBuffer): Boolean {
        return try {
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter?.close()
            interpreter = Interpreter(buffer, options)
            Log.i("TFLiteSemantic", "TFLite Model loaded successfully from buffer")
            true
        } catch (e: Exception) {
            Log.w("TFLiteSemantic", "Failed to load TFLite model from buffer: ${e.message}")
            interpreter = null
            false
        }
    }

    override fun isModelLoaded(): Boolean {
        return interpreter != null
    }

    private fun decodeSampledBitmapFromFile(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            Log.e("TFLiteSemantic", "Failed to decode sampled bitmap: ${e.message}")
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override suspend fun generateImageEmbedding(file: File): FloatArray? {
        if (!file.exists() || !file.canRead()) return null
        if (file.length() > 50 * 1024 * 1024L) {
            Log.w("TFLiteSemantic", "Image file exceeds 50MB limit, skipping embedding to prevent OOM: ${file.name}")
            return null
        }
        val activeInterpreter = interpreter ?: return null

        return try {
            val bitmap = decodeSampledBitmapFromFile(file, 224, 224) ?: return null
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)

            val outputArray = Array(1) { FloatArray(vectorDimension) }
            activeInterpreter.run(inputBuffer, outputArray)

            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            bitmap.recycle()

            normalizeEmbedding(outputArray[0])
        } catch (e: OutOfMemoryError) {
            Log.e("TFLiteSemantic", "OutOfMemoryError during image embedding inference for ${file.name}: ${e.message}")
            System.gc()
            null
        } catch (e: java.io.IOException) {
            Log.e("TFLiteSemantic", "IOException reading image file ${file.name}: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w("TFLiteSemantic", "Error during image embedding inference: ${e.message}")
            null
        }
    }

    override suspend fun generateTextEmbedding(text: String): FloatArray? {
        if (text.isBlank()) return null
        val activeInterpreter = interpreter ?: return null

        return try {
            val inputBuffer = convertTextToByteBuffer(text)
            val outputArray = Array(1) { FloatArray(vectorDimension) }

            activeInterpreter.run(inputBuffer, outputArray)
            normalizeEmbedding(outputArray[0])
        } catch (e: Exception) {
            Log.w("TFLiteSemantic", "Error during text embedding inference: ${e.message}")
            null
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(224 * 224)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until 224) {
            for (j in 0 until 224) {
                val valValue = intValues[pixel++]
                byteBuffer.putFloat(((valValue shr 16 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((valValue shr 8 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((valValue and 0xFF) - 127.5f) / 127.5f)
            }
        }
        return byteBuffer
    }

    private fun convertTextToByteBuffer(text: String): ByteBuffer {
        val maxTokens = 128
        val byteBuffer = ByteBuffer.allocateDirect(4 * maxTokens)
        byteBuffer.order(ByteOrder.nativeOrder())
        val words = text.take(maxTokens * 10).split(Regex("\\s+"))
        for (i in 0 until maxTokens) {
            val tokenVal = if (i < words.size) (words[i].hashCode() and 0x7FFFFFFF) % 10000 else 0
            byteBuffer.putFloat(tokenVal.toFloat())
        }
        return byteBuffer
    }

    private fun normalizeEmbedding(vector: FloatArray): FloatArray {
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm > 0.0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
        return vector
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.w("TFLiteSemantic", "Error closing interpreter: ${e.message}")
        }
    }
}
