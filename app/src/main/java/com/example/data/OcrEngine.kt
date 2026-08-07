package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class OcrEngine(private val context: Context) {
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractRealOcrText(filePath: String): String = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return@withContext ""

        val lowerName = file.name.lowercase()
        var renderedBitmap: Bitmap? = null
        if (lowerName.endsWith(".pdf")) {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val width = page.width
                                val height = page.height
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                renderedBitmap = bitmap
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OcrEngine", "PDF page rendering for OCR failed on $filePath: ${e.message}")
            }
        }

        val imageToProcess = if (renderedBitmap != null) {
            InputImage.fromBitmap(renderedBitmap!!, 0)
        } else {
            try {
                InputImage.fromFilePath(context, android.net.Uri.fromFile(file))
            } catch (e: Exception) {
                null
            }
        }

        if (imageToProcess == null) return@withContext ""

        suspendCancellableCoroutine { continuation ->
            textRecognizer.process(imageToProcess)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e("OcrEngine", "ML Kit text recognition failed on $filePath: ${e.message}")
                    continuation.resume("")
                }
        }
    }
}
