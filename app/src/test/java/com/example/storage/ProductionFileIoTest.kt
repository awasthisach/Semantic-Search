package com.example.storage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProductionFileIoTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun validateFileName_rejects_path_traversal_and_control_characters() {
        assertTrue(ProductionFileIo.validateFileName("photo.jpg").isSuccess)
        assertTrue(ProductionFileIo.validateFileName("../photo.jpg").isFailure)
        assertTrue(ProductionFileIo.validateFileName("folder/photo.jpg").isFailure)
        assertTrue(ProductionFileIo.validateFileName("photo\u0000.jpg").isFailure)
        assertTrue(ProductionFileIo.validateFileName(".").isFailure)
        assertTrue(ProductionFileIo.validateFileName("..").isFailure)
    }

    @Test
    fun local_copy_rename_and_delete_mutate_physical_files() {
        val root = File(context.cacheDir, "production-file-io-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(root, "source.txt").apply { writeText("security-first") }
            val copied = File(root, "copied.txt")

            val copyResult = ProductionFileIo.copy(context, source.absolutePath, copied.absolutePath)
            assertTrue(copyResult.isSuccess)
            assertTrue(copied.exists())
            assertEquals(source.readText(), copied.readText())

            val renamedResult = ProductionFileIo.rename(context, copied.absolutePath, "renamed.txt")
            assertTrue(renamedResult.isSuccess)
            val renamed = File(renamedResult.getOrThrow())
            assertTrue(renamed.exists())
            assertFalse(copied.exists())
            assertEquals("security-first", renamed.readText())

            val deleteResult = ProductionFileIo.delete(context, renamed.absolutePath)
            assertTrue(deleteResult.isSuccess)
            assertFalse(renamed.exists())
            assertTrue(source.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
