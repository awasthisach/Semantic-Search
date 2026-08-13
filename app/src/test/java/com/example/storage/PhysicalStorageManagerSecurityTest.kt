package com.example.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalStorageManagerSecurityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun renameRejectsPathTraversalAndDirectoryChanges() {
        val traversal = PhysicalStorageManager.renameFile(
            context,
            "/does/not/exist/file.txt",
            "../escape.txt"
        )
        val absolute = PhysicalStorageManager.renameFile(
            context,
            "/does/not/exist/file.txt",
            "/tmp/escape.txt"
        )

        assertTrue(traversal.isFailure)
        assertTrue(absolute.isFailure)
    }

    @Test
    fun renameRejectsBlankName() {
        val result = PhysicalStorageManager.renameFile(
            context,
            "/does/not/exist/file.txt",
            ""
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun recycleBinAndVaultDirectoriesAreAppPrivate() {
        val recycleBin = PhysicalStorageManager.getRecycleBinDir(context)
        val vault = PhysicalStorageManager.getVaultDir(context)

        assertTrue(recycleBin.absolutePath.startsWith(context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath))
        assertTrue(vault.absolutePath.startsWith(context.filesDir.absolutePath))
        assertFalse(recycleBin.absolutePath == "/sdcard/.recycle_bin")
    }
}
