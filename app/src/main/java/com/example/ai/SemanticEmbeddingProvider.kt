package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Interface for AI Semantic Duplicate Detection. */
interface SemanticEmbeddingProvider {
    val embeddingVersion: Int
    fun isModelLoaded(): Boolean
    suspend fun generateImageEmbedding(file: File): FloatArray?
    suspend fun generateTextEmbedding(text: String): FloatArray?

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

    fun floatArrayToString(vector: FloatArray): String = vector.joinToString(",")

    fun stringToFloatArray(str: String): FloatArray? {
        if (str.isBlank()) return null
        return try {
            str.split(",").map { it.toFloat() }.toFloatArray()
        } catch (_: Exception) {
            null
        }
    }
}

/** Lightweight deterministic on-device feature embedding used when TFLite is unavailable. */
object LightweightEmbeddingEngine {
    private const val DIMENSION = 128

    fun generateTextEmbedding(text: String): FloatArray? {
        if (text.isBlank()) return null
        val vector = FloatArray(DIMENSION)
        val words = text.lowercase().trim()
            .split(Regex("[^a-z0-9_]+"))
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        for (word in words) {
            vector[(word.hashCode() and 0x7FFFFFFF) % DIMENSION] += 2.0f
            if (word.length >= 3) {
                for (i in 0..word.length - 3) {
                    vector[(word.substring(i, i + 3).hashCode() and 0x7FFFFFFF) % DIMENSION] += 1.0f
                }
            }
        }
        return normalize(vector)
    }

    fun generateImageEmbedding(file: File): FloatArray? {
        if (!file.exists() || !file.canRead()) return null
        val vector = FloatArray(DIMENSION)
        return try {
            val lowerName = file.name.lowercase()
            if (isSupportedImage(lowerName)) {
                val bitmap = decodeBitmap(file)
                if (bitmap != null) return extractBitmapFeatures(bitmap, vector)
            }
            generateTextEmbedding("${file.name} ${file.length()}")
        } catch (e: Exception) {
            Log.w(
                "LightweightEmbedding",
                "Image feature extraction failed for ${file.name}: ${e.message}"
            )
            generateTextEmbedding("${file.name} ${file.length()}")
        }
    }

    private fun isSupportedImage(name: String): Boolean =
        name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".bmp")

    private fun decodeBitmap(file: File): Bitmap? =
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = 4
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )

    private fun extractBitmapFeatures(bitmap: Bitmap, vector: FloatArray): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        var idx = 0
        for (x in 0 until 8) {
            for (y in 0 until 8) {
                val pixel = scaled.getPixel(x, y)
                if (idx < DIMENSION - 2) {
                    vector[idx++] += ((pixel shr 16) and 0xFF) / 255.0f
                    vector[idx++] += ((pixel shr 8) and 0xFF) / 255.0f
                    vector[idx++] += (pixel and 0xFF) / 255.0f
                }
            }
        }
        if (scaled != bitmap) scaled.recycle()
        bitmap.recycle()
        return normalize(vector)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sumSq = 0.0f
        for (v in vector) sumSq += v * v
        val norm = sqrt(sumSq.toDouble()).toFloat()
        if (norm > 0.0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}

/** Deterministic fallback provider used when optional TFLite assets are unavailable. */
class FallbackSemanticEmbeddingProvider : SemanticEmbeddingProvider {
    override val embeddingVersion: Int = 1
    override fun isModelLoaded(): Boolean = false
    override suspend fun generateImageEmbedding(file: File): FloatArray? =
        LightweightEmbeddingEngine.generateImageEmbedding(file)
    override suspend fun generateTextEmbedding(text: String): FloatArray? =
        LightweightEmbeddingEngine.generateTextEmbedding(text)
}

class TFLiteSemanticEmbeddingProvider(modelFile: File? = null) : SemanticEmbeddingProvider {
    override val embeddingVersion: Int = 1
    private var interpreter: Interpreter? = null
    private var vocabMap: Map<String, Int>? = null
    private var vectorDimension: Int = 512

    init {
        if (modelFile != null && modelFile.exists() && modelFile.canRead()) {
            loadModelFromFile(modelFile)
        }
    }

    fun loadModelFromFile(modelFile: File): Boolean = try {
        modelFile.inputStream().use { fis ->
            val buffer = fis.channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                0,
                modelFile.length()
            )
            loadModelFromBuffer(buffer)
        }
    } catch (e: Exception) {
        Log.w("TFLiteSemantic", "Failed to load TFLite model: ${e.message}")
        clearModelState()
        false
    }

    fun loadModelFromAssets(
        context: Context,
        assetName: String = "mobile_clip_embedding.tflite",
        vocabAsset: String = "mobile_clip_vocab.txt"
    ): Boolean {
        if (!assetExists(context, assetName) || !assetExists(context, vocabAsset)) {
            clearModelState()
            return false
        }
        val map = readVocabulary(context, vocabAsset) ?: return false
        val loaded = loadModelBytes(context, assetName)
        if (!loaded) return false
        vocabMap = map
        return true
    }

    private fun assetExists(context: Context, assetName: String): Boolean = try {
        context.assets.open(assetName).use { true }
    } catch (e: Exception) {
        Log.d("TFLiteSemantic", "Asset unavailable: $assetName (${e.message})")
        false
    }

    private fun readVocabulary(context: Context, vocabAsset: String): Map<String, Int>? = try {
        val map = mutableMapOf<String, Int>()
        context.assets.open(vocabAsset).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                line.trim().takeIf { it.isNotEmpty() }?.let { map[it] = index }
            }
        }
        map.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.w("TFLiteSemantic", "Failed to read vocabulary: ${e.message}")
        null
    }

    private fun loadModelBytes(context: Context, assetName: String): Boolean = try {
        val bytes = context.assets.open(assetName).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }
        loadModelFromBuffer(buffer)
    } catch (e: Exception) {
        Log.w("TFLiteSemantic", "Failed to read model asset: ${e.message}")
        clearModelState()
        false
    }

    fun loadModelFromBuffer(buffer: ByteBuffer): Boolean = try {
        interpreter?.close()
        interpreter = Interpreter(buffer, Interpreter.Options().apply { setNumThreads(2) })
        true
    } catch (e: Exception) {
        Log.w("TFLiteSemantic", "Failed to load TFLite model: ${e.message}")
        clearModelState()
        false
    }

    private fun clearModelState() {
        interpreter?.close()
        interpreter = null
        vocabMap = null
    }

    override fun isModelLoaded(): Boolean = interpreter != null && !vocabMap.isNullOrEmpty()

    private fun decodeSampledBitmapFromFile(
        file: File,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        BitmapFactory.decodeFile(file.absolutePath, options)
    } catch (e: Exception) {
        Log.e("TFLiteSemantic", "Failed to decode bitmap: ${e.message}")
        null
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (
                halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override suspend fun generateImageEmbedding(file: File): FloatArray? {
        if (!file.exists() || !file.canRead() || file.length() > 50 * 1024 * 1024L) return null
        val activeInterpreter = interpreter ?: return null
        return try {
            val bitmap = decodeSampledBitmapFromFile(file, 224, 224) ?: return null
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)
            val outputArray = Array(1) { FloatArray(vectorDimension) }
            activeInterpreter.run(inputBuffer, outputArray)
            if (resizedBitmap != bitmap) resizedBitmap.recycle()
            bitmap.recycle()
            normalizeEmbedding(outputArray[0])
        } catch (e: OutOfMemoryError) {
            Log.w("TFLiteSemantic", "Out of memory during image embedding")
            null
        } catch (e: Exception) {
            Log.w("TFLiteSemantic", "Image embedding inference failed: ${e.message}")
            null
        }
    }

    override suspend fun generateTextEmbedding(text: String): FloatArray? {
        if (text.isBlank() || !isModelLoaded()) return null
        return try {
            val inputBuffer = convertTextToByteBuffer(text) ?: return null
            val outputArray = Array(1) { FloatArray(vectorDimension) }
            interpreter?.run(inputBuffer, outputArray)
            normalizeEmbedding(outputArray[0])
        } catch (e: Exception) {
            Log.w("TFLiteSemantic", "Text embedding inference failed: ${e.message}")
            null
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer
            .allocateDirect(4 * 224 * 224 * 3)
            .order(ByteOrder.nativeOrder())
        val intValues = IntArray(224 * 224)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until 224) {
            for (j in 0 until 224) {
                val value = intValues[pixel++]
                byteBuffer.putFloat((((value shr 16) and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat((((value shr 8) and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((value and 0xFF) - 127.5f) / 127.5f)
            }
        }
        return byteBuffer
    }

    private fun convertTextToByteBuffer(text: String): ByteBuffer? {
        val map = vocabMap ?: return null
        val maxTokens = 128
        val byteBuffer = ByteBuffer.allocateDirect(4 * maxTokens).order(ByteOrder.nativeOrder())
        val words = text.take(maxTokens * 10).lowercase().split(Regex("\\s+"))
        for (i in 0 until maxTokens) {
            byteBuffer.putFloat(if (i < words.size) (map[words[i]] ?: 0).toFloat() else 0f)
        }
        return byteBuffer
    }

    private fun normalizeEmbedding(vector: FloatArray): FloatArray {
        var sumSquares = 0.0f
        for (v in vector) sumSquares += v * v
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm > 0.0f) {
            for (i in vector.indices) vector[i] /= norm
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
