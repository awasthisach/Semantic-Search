package com.example.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhysicalStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = tempFolder.newFolder("test_storage")
    }

    @Test
    fun testPhysicalRename() {
        val srcFile = File(testDir, "original.txt").apply { writeText("Hello World") }
        assertTrue(srcFile.exists())

        val result = PhysicalStorageManager.renameFile(context, srcFile.absolutePath, "renamed.txt")
        assertTrue(result.isSuccess)

        val renamedFile = File(testDir, "renamed.txt")
        assertTrue(renamedFile.exists())
        assertEquals("Hello World", renamedFile.readText())
        assertFalse(srcFile.exists())
    }

    @Test
    fun testPhysicalMoveToTrashAndRestore() {
        val srcFile = File(testDir, "document.pdf").apply { writeText("PDF Content") }
        val originalPath = srcFile.absolutePath

        val trashResult = PhysicalStorageManager.moveToTrash(context, originalPath)
        assertTrue(trashResult.isSuccess)
        val trashPath = trashResult.getOrThrow()

        assertFalse(File(originalPath).exists())
        assertTrue(File(trashPath).exists())

        val restoreResult = PhysicalStorageManager.restoreFromTrash(context, trashPath, originalPath)
        assertTrue(restoreResult.isSuccess)

        assertTrue(File(originalPath).exists())
        assertEquals("PDF Content", File(originalPath).readText())
    }

    @Test
    fun testPhysicalDelete() {
        val fileToDelete = File(testDir, "temp.txt").apply { writeText("Delete Me") }
        assertTrue(fileToDelete.exists())

        val deleted = PhysicalStorageManager.deleteFile(context, fileToDelete.absolutePath)
        assertTrue(deleted)
        assertFalse(fileToDelete.exists())
    }
}
