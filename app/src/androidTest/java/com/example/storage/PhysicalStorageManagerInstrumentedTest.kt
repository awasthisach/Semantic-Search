package com.example.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.security.KeystoreVaultManager
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalStorageManagerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var workDir: File
    private lateinit var vaultManager: KeystoreVaultManager

    @Before
    fun setUp() {
        workDir = File(context.cacheDir, "physical-storage-test-${System.nanoTime()}").apply {
            mkdirs()
        }
        vaultManager = KeystoreVaultManager()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun encryptAndRestore_roundTrip_preservesBytesAndRemovesSource() {
        val source = File(workDir, "round-trip.txt")
        val original = "production-vault-round-trip-${System.nanoTime()}".toByteArray()
        source.writeBytes(original)

        val encrypted = PhysicalStorageManager.encryptAndWipeSource(
            context,
            source.absolutePath,
            vaultManager,
        ).getOrThrow()

        assertFalse("Plaintext source must be removed after successful vaulting", source.exists())
        assertTrue(File(encrypted.vaultFilePath).isFile)
        assertTrue(encrypted.iv.size == 12)

        val restored = File(workDir, "restored.txt")
        val result = PhysicalStorageManager.decryptAndRestore(
            context,
            encrypted.vaultFilePath,
            restored.absolutePath,
            encrypted.iv,
            vaultManager,
        ).getOrThrow()

        assertTrue(File(result).isFile)
        assertArrayEquals(original, restored.readBytes())
        assertFalse("Encrypted source must be removed after successful restoration", File(encrypted.vaultFilePath).exists())
    }

    @Test
    fun encryptAndWipeSource_rejectsMissingSource() {
        val missing = File(workDir, "does-not-exist.bin")

        val result = PhysicalStorageManager.encryptAndWipeSource(
            context,
            missing.absolutePath,
            vaultManager,
        )

        assertTrue(result.isFailure)
        assertFalse(missing.exists())
    }

    @Test
    fun renameFile_changesPhysicalFileAndPreservesContents() {
        val source = File(workDir, "before.txt")
        val contents = "rename-test".toByteArray()
        source.writeBytes(contents)

        val result = PhysicalStorageManager.renameFile(context, source.absolutePath, "after.txt")

        assertTrue(result.isSuccess)
        val target = File(workDir, "after.txt")
        assertTrue(target.isFile)
        assertFalse(source.exists())
        assertArrayEquals(contents, target.readBytes())
    }

    @Test
    fun moveToTrashAndRestore_roundTripPreservesContents() {
        val source = File(workDir, "trash-test.txt")
        val contents = "trash-restore-test".toByteArray()
        source.writeBytes(contents)

        val trashed = PhysicalStorageManager.moveToTrash(context, source.absolutePath).getOrThrow()
        assertFalse(source.exists())
        assertTrue(File(trashed).isFile)

        val restored = PhysicalStorageManager.restoreFromTrash(
            context,
            trashed,
            source.absolutePath,
        ).getOrThrow()

        assertTrue(File(restored).isFile)
        assertArrayEquals(contents, source.readBytes())
        assertFalse(File(trashed).exists())
    }
}
