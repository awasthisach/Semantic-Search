package com.example.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageScannerCoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scanner = StorageScanner(context)

    @Test
    fun determineCategory_coversSupportedAndUnknownExtensions() {
        assertEquals("IMAGES", scanner.determineCategory("photo.JPEG").name)
        assertEquals("DOCUMENTS", scanner.determineCategory("report.PDF").name)
        assertEquals("AUDIO", scanner.determineCategory("song.FLAC").name)
        assertEquals("VIDEO", scanner.determineCategory("movie.MP4").name)
        assertEquals("ARCHIVES", scanner.determineCategory("backup.ZIP").name)
        assertEquals("APKS", scanner.determineCategory("bundle.APK").name)
        assertEquals("OTHER", scanner.determineCategory("unknown.bin").name)
    }

    @Test
    fun fileTypeHelpers_areCaseInsensitiveAndPrecise() {
        assertTrue(scanner.isVideoFile("clip.MKV"))
        assertFalse(scanner.isVideoFile("clip.mp3"))
        assertTrue(scanner.isPdfFile("document.PDF"))
        assertFalse(scanner.isPdfFile("document.pdf.tmp"))
        assertTrue(scanner.isDocumentFile("sheet.XLSX"))
        assertFalse(scanner.isDocumentFile("archive.zip"))
    }

    @Test
    fun scanDeviceStorage_discoversRealAppPrivateFilesAndSkipsHiddenFiles() = runTest {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val testDir = File(root, "scanner-test-${System.nanoTime()}")
        val nested = File(testDir, "nested")
        val visible = File(nested, "visible.txt")
        val hidden = File(nested, ".hidden.txt")
        assertTrue(nested.mkdirs())
        visible.writeText("production scanner test")
        hidden.writeText("must be ignored")

        try {
            val files = scanner.scanDeviceStorage(computeHashes = false)
            val visibleItem = files.firstOrNull { it.path == visible.absolutePath }

            assertNotNull(visibleItem)
            assertEquals("visible.txt", visibleItem?.name)
            assertEquals("DOCUMENTS", visibleItem?.category)
            assertEquals(visible.length(), visibleItem?.sizeBytes)
            assertTrue(files.none { it.path == hidden.absolutePath })
        } finally {
            testDir.deleteRecursively()
        }
    }

    @Test
    fun computeDHashFromBitmap_returnsDeterministicHash() {
        val bitmap = android.graphics.Bitmap.createBitmap(9, 8, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until 8) {
                for (x in 0 until 9) {
                    val value = (x * 20 + y) and 0xFF
                    bitmap.setPixel(x, y, android.graphics.Color.rgb(value, value, value))
                }
            }

            val first = scanner.computeDHashFromBitmap(bitmap)
            val second = scanner.computeDHashFromBitmap(bitmap)

            assertEquals(16, first.length)
            assertEquals(first, second)
        } finally {
            bitmap.recycle()
        }
    }
}
