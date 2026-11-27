package com.enziidict.app.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.enziidict.app.core.model.AiVoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "enzii_settings")

enum class AppThemeMode(val labelVi: String) {
    SYSTEM("Theo hệ thống"),
    LIGHT("Sáng"),
    DARK("Tối")
}

object GeminiModelPresets {
    const val FLASH_25 = "gemini-2.5-flash"
    const val FLASH_20 = "gemini-2.0-flash"
    const val PRO_25 = "gemini-2.5-pro"

    val ALL: List<Pair<String, String>> = listOf(
        FLASH_25 to "Nhanh, cân bằng chi phí & chất lượng (Khuyên dùng)",
        FLASH_20 to "Phản hồi siêu nhanh cho giải thích ngắn",
        PRO_25 to "Phân tích ngữ pháp & sắc thái chuyên sâu nhất"
    )
}

data class UserSettings(
    val geminiModel: String = GeminiModelPresets.FLASH_25,
    val geminiLiveModel: String = "models/gemini-2.5-flash-native-audio-preview-09-2025",
    val defaultAiVoice: AiVoice = AiVoice.AOEDE,
    val preferOfflineFallback: Boolean = false,
    val customFolders: Set<String> = emptySet(),
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val streakDays: Int = 0,
    val lastStudyEpochDay: Long = 0L
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GEMINI_LIVE_MODEL = stringPreferencesKey("gemini_live_model")
        val DEFAULT_AI_VOICE = stringPreferencesKey("default_ai_voice")
        val PREFER_OFFLINE_FALLBACK = booleanPreferencesKey("prefer_offline_fallback")
        val CUSTOM_FOLDERS = stringSetPreferencesKey("custom_folders")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val LAST_STUDY_EPOCH_DAY = longPreferencesKey("last_study_epoch_day")
    }

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        val voiceName = prefs[Keys.DEFAULT_AI_VOICE] ?: AiVoice.AOEDE.voiceName
        val voice = AiVoice.entries.firstOrNull { it.voiceName == voiceName } ?: AiVoice.AOEDE
        val themeName = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val theme = AppThemeMode.entries.firstOrNull { it.name == themeName } ?: AppThemeMode.SYSTEM
        val rawStreak = prefs[Keys.STREAK_DAYS] ?: 0
        val lastDay = prefs[Keys.LAST_STUDY_EPOCH_DAY] ?: 0L
        val today = LocalDate.now().toEpochDay()
        val effectiveStreak = if (lastDay == today || lastDay == today - 1L) rawStreak else 0

        UserSettings(
            geminiModel = prefs[Keys.GEMINI_MODEL] ?: GeminiModelPresets.FLASH_25,
            geminiLiveModel = prefs[Keys.GEMINI_LIVE_MODEL]
                ?: "models/gemini-2.5-flash-native-audio-preview-09-2025",
            defaultAiVoice = voice,
            preferOfflineFallback = prefs[Keys.PREFER_OFFLINE_FALLBACK] ?: false,
            customFolders = prefs[Keys.CUSTOM_FOLDERS] ?: emptySet(),
            themeMode = theme,
            streakDays = effectiveStreak,
            lastStudyEpochDay = lastDay
        )
    }

    suspend fun saveGeminiModel(model: String) {
        val cleaned = model.trim()
        if (cleaned.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.GEMINI_MODEL] = cleaned
        }
    }

    suspend fun saveThemeMode(mode: AppThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun recordStudyActivity(todayEpochDay: Long = LocalDate.now().toEpochDay()) {
        context.settingsDataStore.edit { prefs ->
            val lastDay = prefs[Keys.LAST_STUDY_EPOCH_DAY] ?: 0L
            val currentStreak = prefs[Keys.STREAK_DAYS] ?: 0
            val nextStreak = when {
                lastDay == todayEpochDay -> currentStreak.coerceAtLeast(1)
                lastDay == todayEpochDay - 1L -> currentStreak + 1
                else -> 1
            }
            prefs[Keys.STREAK_DAYS] = nextStreak
            prefs[Keys.LAST_STUDY_EPOCH_DAY] = todayEpochDay
        }
    }

    suspend fun saveDefaultAiVoice(voice: AiVoice) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_AI_VOICE] = voice.voiceName
        }
    }

    suspend fun addCustomFolder(folderName: String) {
        val cleaned = folderName.trim()
        if (cleaned.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_FOLDERS] ?: emptySet()
            prefs[Keys.CUSTOM_FOLDERS] = current + cleaned
        }
    }

    suspend fun removeCustomFolder(folderName: String) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_FOLDERS] ?: emptySet()
            prefs[Keys.CUSTOM_FOLDERS] = current - folderName
        }
    }
}
