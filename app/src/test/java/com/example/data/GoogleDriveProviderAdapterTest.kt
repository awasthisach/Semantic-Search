package com.example.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDriveProviderAdapterTest {
    private lateinit var authManager: GoogleAuthManager
    private lateinit var fakeInterceptor: FakeInterceptor
    private lateinit var adapter: GoogleDriveProviderAdapter

    @Before
    fun setUp() {
        authManager = GoogleAuthManager(FakeSharedPreferences())
        fakeInterceptor = FakeInterceptor()
        adapter = GoogleDriveProviderAdapter(
            authManager,
            OkHttpClient.Builder().addInterceptor(fakeInterceptor).build()
        )
    }

    @Test
    fun testUploadFile_WhenFileDoesNotExist() {
        val file = File("non_existent_file.txt")
        val result = kotlinx.coroutines.runBlocking { adapter.uploadFile(file, "remote.txt") }
        assertTrue(result is CloudSyncResult.Error)
        assertEquals(
            "File does not exist or is not readable: ${file.absolutePath}",
            (result as CloudSyncResult.Error).message
        )
    }

    @Test
    fun testUploadFile_WhenNotAuthenticated() {
        val file = File.createTempFile("test_upload", ".txt").apply { writeText("hello"); deleteOnExit() }
        val result = kotlinx.coroutines.runBlocking { adapter.uploadFile(file, "remote.txt") }
        assertTrue(result is CloudSyncResult.Error)
        assertTrue((result as CloudSyncResult.Error).message.contains("user is not authenticated"))
    }

    @Test
    fun testUploadFile_Success() {
        val file = File.createTempFile("test_upload", ".txt").apply { writeText("hello"); deleteOnExit() }
        authManager.saveSession("access_123", "refresh_123", "user@test.com", "Test")
        fakeInterceptor.responseProvider = { request ->
            if (request.method == "POST") {
                response(request, 200, "", "Location" to "https://upload.googleapis.com/resumable/file_id_123")
            } else {
                response(request, 200, "{\"id\":\"file_id_123\"}")
            }
        }
        val result = kotlinx.coroutines.runBlocking { adapter.uploadFile(file, "remote.txt") }
        assertTrue(result is CloudSyncResult.Success)
        assertEquals(file.length(), (result as CloudSyncResult.Success).bytesTransferred)
    }

    @Test
    fun testUploadFile_HttpError() {
        val file = File.createTempFile("test_upload", ".txt").apply { writeText("hello"); deleteOnExit() }
        authManager.saveSession("access_123", "refresh_123", "user@test.com", "Test")
        fakeInterceptor.responseProvider = { request -> response(request, 500, "Server Error") }
        val result = kotlinx.coroutines.runBlocking { adapter.uploadFile(file, "remote.txt") }
        assertTrue(result is CloudSyncResult.Error)
        val error = result as CloudSyncResult.Error
        assertTrue(error.message.contains("HTTP 500"))
        assertTrue(error.isRetryable)
    }

    @Test
    fun testDownloadFile_Success() {
        val file = File.createTempFile("test_download", ".txt").apply { deleteOnExit() }
        authManager.saveSession("access_123", "refresh_123", "user@test.com", "Test")
        var calls = 0
        fakeInterceptor.responseProvider = { request ->
            calls++
            if (calls == 1) {
                response(request, 200, "{\"files\":[{\"id\":\"gdrive_file_id_456\",\"name\":\"remote.txt\"}]}")
            } else {
                response(request, 200, "downloaded content")
            }
        }
        val result = kotlinx.coroutines.runBlocking { adapter.downloadFile("remote.txt", file) }
        assertTrue(result is CloudSyncResult.Success)
        assertEquals("downloaded content", file.readText())
    }

    private fun response(
        request: Request,
        code: Int,
        body: String,
        header: Pair<String, String>? = null
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code in 200..299) "OK" else "Error")
        .apply { header?.let { header(it.first, it.second) } }
        .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
        .build()

    private class FakeInterceptor : Interceptor {
        lateinit var responseProvider: (Request) -> Response
        override fun intercept(chain: Interceptor.Chain): Response = responseProvider(chain.request())
    }

    private class FakeSharedPreferences : android.content.SharedPreferences {
        private val map = mutableMapOf<String, Any?>()
        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {}
        inner class Editor : android.content.SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            override fun putString(k: String, v: String?) = apply { pending[k] = v; removed.remove(k) }
            override fun putStringSet(k: String, v: Set<String>?) = apply { pending[k] = v; removed.remove(k) }
            override fun putInt(k: String, v: Int) = apply { pending[k] = v; removed.remove(k) }
            override fun putLong(k: String, v: Long) = apply { pending[k] = v; removed.remove(k) }
            override fun putFloat(k: String, v: Float) = apply { pending[k] = v; removed.remove(k) }
            override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v; removed.remove(k) }
            override fun remove(k: String) = apply { removed += k; pending.remove(k) }
            override fun clear() = apply { removed += map.keys; pending.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() { removed.forEach(map::remove); map.putAll(pending) }
        }
    }
}
