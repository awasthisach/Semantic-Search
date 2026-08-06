package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.TFLiteSemanticEmbeddingProvider
import com.example.storage.StorageScanner
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for Perceptual Hashing (dHash), Hamming Distance, and AI Semantic Embedding Provider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleUnitTest {
  @org.junit.Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("vvf_vault_prefs", Context.MODE_PRIVATE).edit().clear().commit()
  }


  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun cosineSimilarity_identicalVectors_returnsOne() {
    val provider = TFLiteSemanticEmbeddingProvider()
    val vec = floatArrayOf(0.5f, 0.2f, -0.1f, 0.9f)
    val similarity = provider.calculateCosineSimilarity(vec, vec)
    assertEquals(1.0f, similarity, 0.001f)
  }

  @Test
  fun cosineSimilarity_orthogonalVectors_returnsZero() {
    val provider = TFLiteSemanticEmbeddingProvider()
    val vec1 = floatArrayOf(1.0f, 0.0f)
    val vec2 = floatArrayOf(0.0f, 1.0f)
    val similarity = provider.calculateCosineSimilarity(vec1, vec2)
    assertEquals(0.0f, similarity, 0.001f)
  }

  @Test
  fun tfliteProvider_missingModel_handlesGracefullyWithoutCrash() {
    val provider = TFLiteSemanticEmbeddingProvider(java.io.File("/non_existent_model.tflite"))
    assertFalse(provider.isModelLoaded())
    val dummyFile = java.io.File("/non_existent_file.jpg")
    kotlinx.coroutines.runBlocking {
      assertNull(provider.generateImageEmbedding(dummyFile))
      assertNull(provider.generateTextEmbedding("test query"))
    }
  }

  @Test
  fun hammingDistance_sameHash_returnsZero() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scanner = StorageScanner(context)
    val hash = "a1b2c3d4e5f60718"
    val distance = scanner.calculateHammingDistance(hash, hash)
    assertEquals(0, distance)
  }

  @Test
  fun hammingDistance_differentHash_calculatesBits() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scanner = StorageScanner(context)
    val hash1 = "a1b2c3d4e5f60718"
    val hash2 = "a1b2c3d4e5f60719" // last hex digit differs by 1 bit (8 vs 9 -> 1000 vs 1001)
    val distance = scanner.calculateHammingDistance(hash1, hash2)
    assertEquals(1, distance)
  }

  @Test
  fun isImageFile_identifiesSupportedFormats() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scanner = StorageScanner(context)
    assertTrue(scanner.isImageFile("photo.jpg"))
    assertTrue(scanner.isImageFile("photo.jpeg"))
    assertTrue(scanner.isImageFile("photo.png"))
    assertTrue(scanner.isImageFile("photo.webp"))
    assertTrue(scanner.isImageFile("photo.heic"))
    assertFalse(scanner.isImageFile("document.pdf"))
    assertFalse(scanner.isImageFile("archive.zip"))
  }

  @Test
  fun isVideoFile_identifiesSupportedVideoFormats() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scanner = StorageScanner(context)
    assertTrue(scanner.isVideoFile("clip.mp4"))
    assertTrue(scanner.isVideoFile("movie.mkv"))
    assertTrue(scanner.isVideoFile("video.avi"))
    assertTrue(scanner.isVideoFile("recorded.webm"))
    assertFalse(scanner.isVideoFile("image.png"))
    assertFalse(scanner.isVideoFile("doc.pdf"))
  }

  @Test
  fun computeVideoDHash_nonExistentFile_returnsEmptyHash() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scanner = StorageScanner(context)
    val hash = scanner.computeVideoDHash(java.io.File("/non_existent_video.mp4"))
    assertEquals("", hash)
  }

  @Test
  fun isPdfFile_identifiesPdfFiles() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scanner = StorageScanner(context)
    assertTrue(scanner.isPdfFile("document.pdf"))
    assertTrue(scanner.isPdfFile("REPORT.PDF"))
    assertFalse(scanner.isPdfFile("document.docx"))
    assertFalse(scanner.isPdfFile("photo.png"))
  }

  @Test
  fun isDocumentFile_identifiesDocumentFormats() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scanner = StorageScanner(context)
    assertTrue(scanner.isDocumentFile("file.pdf"))
    assertTrue(scanner.isDocumentFile("file.doc"))
    assertTrue(scanner.isDocumentFile("file.docx"))
    assertTrue(scanner.isDocumentFile("file.txt"))
    assertTrue(scanner.isDocumentFile("file.xlsx"))
    assertFalse(scanner.isDocumentFile("file.mp3"))
    assertFalse(scanner.isDocumentFile("file.mp4"))
  }

  @Test
  fun computeDocumentFingerprint_nonExistentFile_returnsEmpty() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scanner = StorageScanner(context)
    val fp = scanner.computeDocumentFingerprint(java.io.File("/non_existent_doc.pdf"))
    assertEquals("", fp)
  }

  @Test
  fun vaultPin_verificationAndChange_persistsCorrectly() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.AppDatabase::class.java).allowMainThreadQueries().build()
    val repo = com.example.data.SmartManagerRepository(context, database.fileDao())
    
    // Default PIN "1234" check
    assertTrue(repo.verifyVaultPin("1234"))
    assertFalse(repo.verifyVaultPin("0000"))

    // Change PIN from "1234" to "9876"
    val changed = repo.changeVaultPin("1234", "9876")
    assertTrue(changed)

    // Old PIN should fail, new PIN should succeed
    assertFalse(repo.verifyVaultPin("1234"))
    assertTrue(repo.verifyVaultPin("9876"))

    database.close()
  }

  @Test
  fun mainViewModel_changeVaultPin_handlesUpdates() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.MainViewModel(app)

    // Change PIN with wrong current PIN should fail
    val wrongChange = viewModel.changeVaultPin("1111", "5555")
    assertFalse(wrongChange)
    assertEquals("Failed to update PIN. Check current PIN.", viewModel.pinError.value)

    // Change PIN with correct current PIN should succeed
    val correctChange = viewModel.changeVaultPin("1234", "5555")
    assertTrue(correctChange)
    assertEquals(null, viewModel.pinError.value)
  }

  @Test
  fun vectorSerialization_roundtrip_isAccurate() {
    val provider = TFLiteSemanticEmbeddingProvider()
    val original = floatArrayOf(0.123f, -0.456f, 0.789f)
    val str = provider.floatArrayToString(original)
    val parsed = provider.stringToFloatArray(str)
    assertNotNull(parsed)
    assertArrayEquals(original, parsed!!, 0.0001f)
  }
}
