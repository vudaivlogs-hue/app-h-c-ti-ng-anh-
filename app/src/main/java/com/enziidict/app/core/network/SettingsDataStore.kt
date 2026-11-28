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

enum class DailyWordSource(val labelVi: String) {
    SRS_DUE("Sổ tay SRS đến hạn"),
    COMMON_VOCAB("Từ vựng thông dụng"),
    MIXED("Kết hợp (SRS + Mẫu câu)")
}

object GeminiModelPresets {
    const val FLASH_LATEST = "gemini-flash-latest"
    const val FLASH_25 = "gemini-2.5-flash"
    const val FLASH_25_LITE = "gemini-2.5-flash-lite"
    const val FLASH_20 = "gemini-2.0-flash"
    const val PRO_25 = "gemini-2.5-pro"
    const val DEFAULT_LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"

    val ALL: List<Pair<String, String>> = listOf(
        FLASH_LATEST to "Tự động trỏ tới bản Flash mới nhất của API Key (Khuyên dùng)",
        FLASH_25 to "Gemini 2.5 Flash — cân bằng tốc độ & chất lượng",
        FLASH_25_LITE to "Gemini 2.5 Flash Lite — phản hồi nhanh, tiết kiệm quota",
        PRO_25 to "Gemini 2.5 Pro — phân tích ngữ pháp chuyên sâu nhất"
    )
}

data class UserSettings(
    val geminiModel: String = GeminiModelPresets.FLASH_25,
    val geminiLiveModel: String = GeminiModelPresets.DEFAULT_LIVE_MODEL,
    val discoveredTextModels: List<String> = emptyList(),
    val discoveredLiveModels: List<String> = emptyList(),
    val defaultAiVoice: AiVoice = AiVoice.AOEDE,
    val preferOfflineFallback: Boolean = false,
    val customFolders: Set<String> = emptySet(),
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val streakDays: Int = 0,
    val lastStudyEpochDay: Long = 0L,
    val studiedToday: Boolean = false,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderHour: Int = 20,
    val dailyReminderMinute: Int = 0,
    val dailyWordEnabled: Boolean = false,
    val dailyWordFrequency: Int = 1,
    val dailyWordSource: DailyWordSource = DailyWordSource.MIXED,
    val dismissedMascotTip: Boolean = false
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GEMINI_LIVE_MODEL = stringPreferencesKey("gemini_live_model")
        val DISCOVERED_TEXT_MODELS = stringPreferencesKey("discovered_text_models")
        val DISCOVERED_LIVE_MODELS = stringPreferencesKey("discovered_live_models")
        val DEFAULT_AI_VOICE = stringPreferencesKey("default_ai_voice")
        val PREFER_OFFLINE_FALLBACK = booleanPreferencesKey("prefer_offline_fallback")
        val CUSTOM_FOLDERS = stringSetPreferencesKey("custom_folders")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val LAST_STUDY_EPOCH_DAY = longPreferencesKey("last_study_epoch_day")
        val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val DAILY_REMINDER_HOUR = intPreferencesKey("daily_reminder_hour")
        val DAILY_REMINDER_MINUTE = intPreferencesKey("daily_reminder_minute")
        val DAILY_WORD_ENABLED = booleanPreferencesKey("daily_word_enabled")
        val DAILY_WORD_FREQUENCY = intPreferencesKey("daily_word_frequency")
        val DAILY_WORD_SOURCE = stringPreferencesKey("daily_word_source")
        val DISMISSED_MASCOT_TIP = booleanPreferencesKey("dismissed_mascot_tip")
    }

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        val voiceName = prefs[Keys.DEFAULT_AI_VOICE] ?: AiVoice.AOEDE.voiceName
        val voice = AiVoice.entries.firstOrNull { it.voiceName == voiceName } ?: AiVoice.AOEDE
        val themeName = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val theme = AppThemeMode.entries.firstOrNull { it.name == themeName } ?: AppThemeMode.SYSTEM
        val rawStreak = prefs[Keys.STREAK_DAYS] ?: 0
        val lastDay = prefs[Keys.LAST_STUDY_EPOCH_DAY] ?: 0L
        val today = LocalDate.now().toEpochDay()
        val studiedToday = lastDay == today
        val effectiveStreak = if (studiedToday || lastDay == today - 1L) rawStreak else 0
        val wordSourceName = prefs[Keys.DAILY_WORD_SOURCE] ?: DailyWordSource.MIXED.name
        val wordSource = DailyWordSource.entries.firstOrNull { it.name == wordSourceName } ?: DailyWordSource.MIXED

        val discoveredText = prefs[Keys.DISCOVERED_TEXT_MODELS]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val discoveredLive = prefs[Keys.DISCOVERED_LIVE_MODELS]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        UserSettings(
            geminiModel = GeminiModelCatalogService.normalizeModelId(
                prefs[Keys.GEMINI_MODEL] ?: GeminiModelPresets.FLASH_25
            ).ifEmpty { GeminiModelPresets.FLASH_25 },
            geminiLiveModel = GeminiModelCatalogService.normalizeFullModelName(
                prefs[Keys.GEMINI_LIVE_MODEL] ?: GeminiModelPresets.DEFAULT_LIVE_MODEL
            ).ifEmpty { GeminiModelPresets.DEFAULT_LIVE_MODEL },
            discoveredTextModels = discoveredText,
            discoveredLiveModels = discoveredLive,
            defaultAiVoice = voice,
            preferOfflineFallback = prefs[Keys.PREFER_OFFLINE_FALLBACK] ?: false,
            customFolders = prefs[Keys.CUSTOM_FOLDERS] ?: emptySet(),
            themeMode = theme,
            streakDays = effectiveStreak,
            lastStudyEpochDay = lastDay,
            studiedToday = studiedToday,
            dailyReminderEnabled = prefs[Keys.DAILY_REMINDER_ENABLED] ?: false,
            dailyReminderHour = (prefs[Keys.DAILY_REMINDER_HOUR] ?: 20).coerceIn(0, 23),
            dailyReminderMinute = (prefs[Keys.DAILY_REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
            dailyWordEnabled = prefs[Keys.DAILY_WORD_ENABLED] ?: false,
            dailyWordFrequency = (prefs[Keys.DAILY_WORD_FREQUENCY] ?: 1).coerceIn(1, 2),
            dailyWordSource = wordSource,
            dismissedMascotTip = prefs[Keys.DISMISSED_MASCOT_TIP] ?: false
        )
    }

    suspend fun dismissMascotTip() {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DISMISSED_MASCOT_TIP] = true
        }
    }

    suspend fun saveGeminiModel(model: String) {
        val cleaned = GeminiModelCatalogService.normalizeModelId(model)
        if (cleaned.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.GEMINI_MODEL] = cleaned
        }
    }

    suspend fun saveGeminiLiveModel(model: String) {
        val cleaned = GeminiModelCatalogService.normalizeFullModelName(model)
        if (cleaned.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.GEMINI_LIVE_MODEL] = cleaned
        }
    }

    suspend fun saveDiscoveredModels(textModels: List<String>, liveModels: List<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DISCOVERED_TEXT_MODELS] = textModels
                .map { GeminiModelCatalogService.normalizeModelId(it) }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("\n")
            prefs[Keys.DISCOVERED_LIVE_MODELS] = liveModels
                .map { GeminiModelCatalogService.normalizeFullModelName(it) }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("\n")
        }
    }

    suspend fun saveThemeMode(mode: AppThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun saveDailyReminderConfig(enabled: Boolean, hour: Int, minute: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DAILY_REMINDER_ENABLED] = enabled
            prefs[Keys.DAILY_REMINDER_HOUR] = hour.coerceIn(0, 23)
            prefs[Keys.DAILY_REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun saveDailyWordConfig(enabled: Boolean, frequency: Int, source: DailyWordSource) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DAILY_WORD_ENABLED] = enabled
            prefs[Keys.DAILY_WORD_FREQUENCY] = frequency.coerceIn(1, 2)
            prefs[Keys.DAILY_WORD_SOURCE] = source.name
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
