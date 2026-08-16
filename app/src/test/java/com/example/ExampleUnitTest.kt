package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.TFLiteSemanticEmbeddingProvider
import com.example.security.KeystoreVaultManager
import com.example.storage.StorageScanner
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleUnitTest {
  @org.junit.Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("vvf_vault_prefs", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test fun addition_isCorrect() { assertEquals(4, 2 + 2) }

  @Test fun cosineSimilarity_identicalVectors_returnsOne() {
    val provider = TFLiteSemanticEmbeddingProvider()
    val vec = floatArrayOf(0.5f, 0.2f, -0.1f, 0.9f)
    assertEquals(1.0f, provider.calculateCosineSimilarity(vec, vec), 0.001f)
  }

  @Test fun cosineSimilarity_orthogonalVectors_returnsZero() {
    val provider = TFLiteSemanticEmbeddingProvider()
    assertEquals(0.0f, provider.calculateCosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.001f)
  }

  @Test fun tfliteProvider_missingModel_handlesGracefullyWithoutCrash() = runBlocking {
    val provider = TFLiteSemanticEmbeddingProvider(java.io.File("/non_existent_model.tflite"))
    assertFalse(provider.isModelLoaded())
    assertNull(provider.generateImageEmbedding(java.io.File("/non_existent_file.jpg")))
    assertNull(provider.generateTextEmbedding("test query"))
  }

  @Test fun hammingDistance_sameHash_returnsZero() {
    val scanner = StorageScanner(ApplicationProvider.getApplicationContext())
    assertEquals(0, scanner.calculateHammingDistance("a1b2c3d4e5f60718", "a1b2c3d4e5f60718"))
  }

  @Test fun hammingDistance_differentHash_calculatesBits() {
    val scanner = StorageScanner(ApplicationProvider.getApplicationContext())
    assertEquals(1, scanner.calculateHammingDistance("a1b2c3d4e5f60718", "a1b2c3d4e5f60719"))
  }

  @Test fun isImageFile_identifiesSupportedFormats() {
    val scanner = StorageScanner(ApplicationProvider.getApplicationContext())
    assertTrue(scanner.isImageFile("photo.jpg")); assertTrue(scanner.isImageFile("photo.jpeg")); assertTrue(scanner.isImageFile("photo.png")); assertTrue(scanner.isImageFile("photo.webp")); assertTrue(scanner.isImageFile("photo.heic"))
    assertFalse(scanner.isImageFile("document.pdf")); assertFalse(scanner.isImageFile("archive.zip"))
  }

  @Test fun isVideoFile_identifiesSupportedVideoFormats() {
    val scanner = StorageScanner(ApplicationProvider.getApplicationContext())
    assertTrue(scanner.isVideoFile("clip.mp4")); assertTrue(scanner.isVideoFile("movie.mkv")); assertTrue(scanner.isVideoFile("video.avi")); assertTrue(scanner.isVideoFile("recorded.webm"))
    assertFalse(scanner.isVideoFile("image.png")); assertFalse(scanner.isVideoFile("doc.pdf"))
  }

  @Test fun computeVideoDHash_nonExistentFile_returnsEmptyHash() = runBlocking {
    val scanner = StorageScanner(ApplicationProvider.getApplicationContext())
    assertEquals("", scanner.computeVideoDHash(java.io.File("/non_existent_video.mp4")))
  }

  @Test fun isPdfFile_identifiesPdfFiles() {
    val scanner = StorageScanner(ApplicationProvider.getApplicationContext())
    assertTrue(scanner.isPdfFile("document.pdf")); assertTrue(scanner.isPdfFile("REPORT.PDF")); assertFalse(scanner.isPdfFile("document.docx")); assertFalse(scanner.isPdfFile("photo.png"))
  }

  @Test fun isDocumentFile_identifiesDocumentFormats() {
    val scanner = StorageScanner(ApplicationProvider.getApplicationContext())
    assertTrue(scanner.isDocumentFile("file.pdf")); assertTrue(scanner.isDocumentFile("file.doc")); assertTrue(scanner.isDocumentFile("file.docx")); assertTrue(scanner.isDocumentFile("file.txt")); assertTrue(scanner.isDocumentFile("file.xlsx"))
    assertFalse(scanner.isDocumentFile("file.mp3")); assertFalse(scanner.isDocumentFile("file.mp4"))
  }

  @Test fun computeDocumentFingerprint_nonExistentFile_returnsEmpty() = runBlocking {
    val scanner = StorageScanner(ApplicationProvider.getApplicationContext())
    assertEquals("", scanner.computeDocumentFingerprint(java.io.File("/non_existent_doc.pdf")))
  }

  @Test fun vaultPin_hashAndVerification_areDeterministicAndRejectWrongPin() {
    val manager = KeystoreVaultManager(); val storedHash = manager.hashPin("1234")
    assertTrue(manager.verifyPin("1234", storedHash)); assertFalse(manager.verifyPin("0000", storedHash)); assertFalse(manager.verifyPin("123", storedHash))
  }

  @Test fun vaultPin_hashesUseUniqueSalts() {
    val manager = KeystoreVaultManager(); val first = manager.hashPin("1234"); val second = manager.hashPin("1234")
    assertNotEquals(first, second); assertTrue(manager.verifyPin("1234", first)); assertTrue(manager.verifyPin("1234", second))
  }

  @Test fun vectorSerialization_roundtrip_isAccurate() {
    val provider = TFLiteSemanticEmbeddingProvider(); val original = floatArrayOf(0.123f, -0.456f, 0.789f)
    val parsed = provider.stringToFloatArray(provider.floatArrayToString(original)); assertNotNull(parsed); assertArrayEquals(original, parsed!!, 0.0001f)
  }

  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  @Test fun test_duplicateDetectionEngine_lsh_visualDuplicates() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>(); val scanner = StorageScanner(context); val provider = TFLiteSemanticEmbeddingProvider(); val engine = com.example.data.DuplicateDetectionEngine(scanner, provider)
    val files = listOf(
      com.example.data.FileItemEntity(id=101L,name="image_original.jpg",path="/storage/emulated/0/DCIM/image_original.jpg",category=com.example.data.FileCategory.IMAGES.name,sizeBytes=150000L,visualSimilarityHash="a1b2c3d4e5f60718"),
      com.example.data.FileItemEntity(id=102L,name="image_duplicate.jpg",path="/storage/emulated/0/DCIM/image_duplicate.jpg",category=com.example.data.FileCategory.IMAGES.name,sizeBytes=151000L,visualSimilarityHash="a1b2c3d4e5f60719"),
      com.example.data.FileItemEntity(id=103L,name="image_different.jpg",path="/storage/emulated/0/DCIM/image_different.jpg",category=com.example.data.FileCategory.IMAGES.name,sizeBytes=250000L,visualSimilarityHash="ffffffffffffffff"))
    val result = engine.getVisualDuplicates(kotlinx.coroutines.flow.flowOf(files), kotlinx.coroutines.flow.flowOf(95f)).first()
    assertEquals(1, result.size); assertEquals(2, result.first().files.size); assertTrue(result.first().files.any { it.id == 101L }); assertTrue(result.first().files.any { it.id == 102L }); assertFalse(result.first().files.any { it.id == 103L })
  }

  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  @Test fun test_duplicateDetectionEngine_semanticDuplicates_withoutModel_returnsEmpty() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>(); val scanner = StorageScanner(context); val provider = TFLiteSemanticEmbeddingProvider(); val engine = com.example.data.DuplicateDetectionEngine(scanner, provider)
    assertFalse(provider.isModelLoaded())
    val vec = FloatArray(128) { if (it == 5) .9f else if (it == 10) .3f else if (it == 15) .2f else 0f }
    val files = listOf(
      com.example.data.FileItemEntity(id=201L,name="document_1.pdf",path="/storage/emulated/0/Documents/document_1.pdf",category=com.example.data.FileCategory.DOCUMENTS.name,sizeBytes=12000L,semanticIndexed=true,semanticEmbeddingString=provider.floatArrayToString(vec)),
      com.example.data.FileItemEntity(id=202L,name="document_2_similar.pdf",path="/storage/emulated/0/Documents/document_2_similar.pdf",category=com.example.data.FileCategory.DOCUMENTS.name,sizeBytes=12500L,semanticIndexed=true,semanticEmbeddingString=provider.floatArrayToString(vec)))
    val result = engine.getSemanticDuplicates(kotlinx.coroutines.flow.flowOf(files), kotlinx.coroutines.flow.flowOf(85f)).first()
    assertTrue(result.isEmpty())
  }

  @Test fun test_appDatabase_migrations_config() {
    val migration1to2 = com.example.data.AppDatabase.MIGRATION_1_2; val migration2to3 = com.example.data.AppDatabase.MIGRATION_2_3
    assertEquals(1, migration1to2.startVersion); assertEquals(2, migration1to2.endVersion); assertEquals(2, migration2to3.startVersion); assertEquals(3, migration2to3.endVersion)
  }
}
