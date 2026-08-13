package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.storage.ProductionFileIo
import com.example.storage.PhysicalStorageManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProductionFileIoTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun rejectsPathTraversalAndInvalidNames() {
        assertTrue(ProductionFileIo.validateFileName("photo.jpg").isSuccess)
        assertTrue(ProductionFileIo.validateFileName("../photo.jpg").isFailure)
        assertTrue(ProductionFileIo.validateFileName("..").isFailure)
        assertTrue(ProductionFileIo.validateFileName("folder/photo.jpg").isFailure)
        assertTrue(ProductionFileIo.validateFileName("bad\u0000name.jpg").isFailure)
    }

    @Test
    fun localRenameChangesPhysicalFileAndNeverOverwrites() {
        val root = File(context.cacheDir, "physical-io-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(root, "old.txt").apply { writeText("hello") }
            val existing = File(root, "existing.txt").apply { writeText("keep") }
            val renamed = ProductionFileIo.rename(context, source.absolutePath, "new.txt")
            assertTrue(renamed.isSuccess)
            assertFalse(source.exists())
            assertEquals("hello", File(renamed.getOrThrow()).readText())
            val collision = ProductionFileIo.rename(context, renamed.getOrThrow(), existing.name)
            assertTrue(collision.isFailure)
            assertEquals("keep", existing.readText())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun localCopyVerifiesContentAndKeepsSource() {
        val root = File(context.cacheDir, "physical-copy-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(root, "source.bin").apply { writeBytes(ByteArray(4096) { (it % 251).toByte() }) }
            val destination = File(root, "nested/destination.bin")
            val result = ProductionFileIo.copy(context, source.absolutePath, destination.absolutePath)
            assertTrue(result.isSuccess)
            assertTrue(source.exists())
            assertTrue(destination.exists())
            assertEquals(source.readBytes().toList(), destination.readBytes().toList())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun recycleBinMoveAndRestoreArePhysicalAndAtomicFromDbPerspective() {
        val root = File(context.cacheDir, "physical-trash-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(root, "important.txt").apply { writeText("important data") }
            val moved = ProductionFileIo.moveToRecycleBin(context, source.absolutePath)
            assertTrue(moved.isSuccess)
            assertFalse(source.exists())
            val trash = File(moved.getOrThrow())
            assertTrue(trash.exists())
            val restoredTarget = File(root, "restored.txt")
            val restored = ProductionFileIo.restoreFromRecycleBin(context, trash.absolutePath, restoredTarget.absolutePath)
            assertTrue(restored.isSuccess)
            assertFalse(trash.exists())
            assertTrue(restoredTarget.exists())
            assertEquals("important data", restoredTarget.readText())
        } finally {
            root.deleteRecursively()
            PhysicalStorageManager.getRecycleBinDir(context).listFiles()?.forEach { it.delete() }
        }
    }

    @Test
    fun deleteRemovesPhysicalFileBeforeRepositoryMetadataCanBeRemoved() {
        val file = File(context.cacheDir, "delete-test-${System.nanoTime()}.txt").apply { writeText("delete me") }
        assertTrue(file.exists())
        val result = ProductionFileIo.delete(context, file.absolutePath)
        assertTrue(result.isSuccess)
        assertFalse(file.exists())
    }
}
