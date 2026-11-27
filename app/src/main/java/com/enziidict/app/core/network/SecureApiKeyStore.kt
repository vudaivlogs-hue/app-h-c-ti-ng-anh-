package com.enziidict.app.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy { createEncryptedPrefs() }

    private val _apiKeyFlow = MutableStateFlow("")
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    val isEncryptedStorage: Boolean
        get() = _isEncrypted

    private var _isEncrypted: Boolean = false

    init {
        _apiKeyFlow.value = prefs.getString(KEY_GEMINI_API_KEY, "").orEmpty()
    }

    fun getApiKey(): String = prefs.getString(KEY_GEMINI_API_KEY, "").orEmpty()

    fun saveApiKey(rawKey: String) {
        val cleaned = rawKey.trim()
        prefs.edit().putString(KEY_GEMINI_API_KEY, cleaned).apply()
        _apiKeyFlow.value = cleaned
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
        _apiKeyFlow.value = ""
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { _isEncrypted = true }
        }.getOrElse {
            _isEncrypted = false
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val PREFS_FILE_NAME = "enzii_secure_keys"
        private const val FALLBACK_PREFS_NAME = "enzii_secure_keys_fallback"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
