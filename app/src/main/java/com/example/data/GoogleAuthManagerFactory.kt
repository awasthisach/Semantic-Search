package com.example.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object GoogleAuthManagerFactory {

    @Volatile
    private var INSTANCE: GoogleAuthManager? = null

    fun getInstance(context: Context): GoogleAuthManager {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                val securePrefs = try {
                    val masterKey = MasterKey.Builder(context.applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    EncryptedSharedPreferences.create(
                        context.applicationContext,
                        "secure_google_oauth_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e: Exception) {
                    Log.e("GoogleAuthManagerFactory", "Failed to create EncryptedSharedPreferences, falling back to standard prefs", e)
                    context.applicationContext.getSharedPreferences("secure_google_oauth_prefs_fallback", Context.MODE_PRIVATE)
                }
                GoogleAuthManager(securePrefs).also { INSTANCE = it }
            }
        }
    }
}
