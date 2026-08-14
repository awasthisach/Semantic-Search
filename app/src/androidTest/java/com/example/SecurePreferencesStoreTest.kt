package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.security.SecurePreferencesStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurePreferencesStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun storesAndReadsEncryptedValue() = runTest {
        val store = SecurePreferencesStore(context)
        store.putString("coverage-test", "secret-value")

        assertEquals("secret-value", store.getString("coverage-test"))

        store.remove("coverage-test")
        assertNull(store.getString("coverage-test"))
    }

    @Test
    fun migratesLegacySharedPreferencesAndRemovesLegacyValue() = runTest {
        val legacy = context.getSharedPreferences("legacy-secure-prefs-test", Context.MODE_PRIVATE)
        legacy.edit().putString("legacy-token", "legacy-secret").commit()
        val store = SecurePreferencesStore(context)

        assertEquals(1, store.migrateFrom(legacy, setOf("legacy-token")))
        assertEquals("legacy-secret", store.getString("legacy-token"))
        assertNull(legacy.getString("legacy-token", null))

        store.remove("legacy-token")
    }
}
