package com.example.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class GoogleAuthState {
    object SignedOut : GoogleAuthState()
    object Authenticating : GoogleAuthState()
    data class SignedIn(
        val email: String,
        val displayName: String?,
        val accessToken: String
    ) : GoogleAuthState()
    data class Error(val message: String, val cause: Throwable? = null) : GoogleAuthState()
}

class GoogleAuthManager(private val sharedPrefs: SharedPreferences) {

    private val _authState = MutableStateFlow<GoogleAuthState>(GoogleAuthState.SignedOut)
    val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val accessToken = sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val email = sharedPrefs.getString(KEY_EMAIL, null)
        val displayName = sharedPrefs.getString(KEY_DISPLAY_NAME, null)

        if (accessToken != null && accessToken.isNotBlank() && email != null && email.isNotBlank()) {
            _authState.value = GoogleAuthState.SignedIn(email, displayName, accessToken)
        } else {
            _authState.value = GoogleAuthState.SignedOut
        }
    }

    /**
     * Persists credentials obtained from a real OAuth flow.
     * This method deliberately rejects the synthetic token format that the old
     * demo sign-in path generated. A fabricated token must never create a
     * SignedIn state because it would make the UI claim that Google is connected
     * when Google has never authenticated the user.
     */
    @Synchronized
    fun saveSession(accessToken: String, refreshToken: String?, email: String, displayName: String?) {
        if (accessToken.isBlank() || email.isBlank()) {
            _authState.value = GoogleAuthState.Error("Invalid Google authentication session")
            return
        }

        if (accessToken.startsWith(MOCK_ACCESS_TOKEN_PREFIX)) {
            _authState.value = GoogleAuthState.Error(
                "Synthetic Google OAuth credentials are not accepted. Complete the real Google sign-in flow."
            )
            return
        }

        try {
            sharedPrefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, accessToken)
                if (refreshToken != null) {
                    putString(KEY_REFRESH_TOKEN, refreshToken)
                } else {
                    remove(KEY_REFRESH_TOKEN)
                }
                putString(KEY_EMAIL, email)
                putString(KEY_DISPLAY_NAME, displayName)
                apply()
            }
            _authState.value = GoogleAuthState.SignedIn(email, displayName, accessToken)
        } catch (e: Exception) {
            _authState.value = GoogleAuthState.Error("Failed to persist authentication securely", e)
        }
    }

    @Synchronized
    fun clearSession() {
        try {
            sharedPrefs.edit().apply {
                remove(KEY_ACCESS_TOKEN)
                remove(KEY_REFRESH_TOKEN)
                remove(KEY_EMAIL)
                remove(KEY_DISPLAY_NAME)
                apply()
            }
            _authState.value = GoogleAuthState.SignedOut
        } catch (e: Exception) {
            _authState.value = GoogleAuthState.Error("Failed to revoke authentication locally", e)
        }
    }

    fun getAccessToken(): String? = sharedPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = sharedPrefs.getString(KEY_REFRESH_TOKEN, null)

    fun isAuthorized(): Boolean = _authState.value is GoogleAuthState.SignedIn

    companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"

        // Used only to reject the legacy/demo token generator still present in old UI code.
        const val MOCK_ACCESS_TOKEN_PREFIX = "ya29.a0AcEw0eB-"
    }
}
