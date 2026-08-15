package com.example.data

import android.content.Context
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.UnknownHostException

class CloudSyncEngineTest {
    private val context = mockk<Context>(relaxed = true)
    private val dao = mockk<FileDao>(relaxed = true)
    private val authManager = mockk<GoogleAuthManager>(relaxed = true)

    private fun item(path: String, provider: String = "GOOGLE_DRIVE") = CloudSyncItemEntity(
        id = 1L,
        provider = provider,
        fileName = "sample.txt",
        filePath = path,
        fileSize = 7L,
        status = "PENDING"
    )

    @Test
    fun missingFile_returnsNonRetryableError() = runTest {
        val engine = CloudSyncEngine(context, dao, authManager)

        val result = engine.syncItem(item("${System.getProperty("java.io.tmpdir")}/does-not-exist-${System.nanoTime()}"))

        assertTrue(result is CloudSyncResult.Error)
        result as CloudSyncResult.Error
        assertFalse(result.isRetryable)
    }

    @Test
    fun unsupportedProvider_returnsNonRetryableError() = runTest {
        val file = File.createTempFile("cloud-sync", ".txt")
        try {
            val engine = CloudSyncEngine(context, dao, authManager)

            val result = engine.syncItem(item(file.absolutePath, "UNKNOWN_PROVIDER"))

            assertTrue(result is CloudSyncResult.Error)
            result as CloudSyncResult.Error
            assertFalse(result.isRetryable)
        } finally {
            file.delete()
        }
    }

    @Test
    fun successfulUpload_returnsSuccess() = runTest {
        val file = File.createTempFile("cloud-sync", ".txt")
        try {
            val adapter = mockk<CloudProviderAdapter>()
            coEvery { adapter.uploadFile(file, "sample.txt") } returns CloudSyncResult.Success(7L)
            val engine = CloudSyncEngine(context, dao, authManager, adapter)

            val result = engine.syncItem(item(file.absolutePath))

            assertEquals(CloudSyncResult.Success(7L), result)
        } finally {
            file.delete()
        }
    }

    @Test
    fun unknownHostException_isRetryable() = runTest {
        val file = File.createTempFile("cloud-sync", ".txt")
        try {
            val adapter = mockk<CloudProviderAdapter>()
            coEvery { adapter.uploadFile(file, "sample.txt") } throws UnknownHostException("offline")
            val engine = CloudSyncEngine(context, dao, authManager, adapter)

            val result = engine.syncItem(item(file.absolutePath))

            assertTrue(result is CloudSyncResult.Error)
            result as CloudSyncResult.Error
            assertTrue(result.isRetryable)
            assertEquals("offline", result.message)
        } finally {
            file.delete()
        }
    }

    @Test
    fun ioException_isRetryable() = runTest {
        val file = File.createTempFile("cloud-sync", ".txt")
        try {
            val adapter = mockk<CloudProviderAdapter>()
            coEvery { adapter.uploadFile(file, "sample.txt") } throws IOException("temporary failure")
            val engine = CloudSyncEngine(context, dao, authManager, adapter)

            val result = engine.syncItem(item(file.absolutePath))

            assertTrue(result is CloudSyncResult.Error)
            result as CloudSyncResult.Error
            assertTrue(result.isRetryable)
        } finally {
            file.delete()
        }
    }
}
