package com.enziidict.app.feature.shadowing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ShadowingPracticeLog(
    val itemId: String,
    val sentenceEn: String,
    val attemptsCount: Int = 0,
    val lastScore: Int = 0,
    val bestScore: Int = 0,
    val trickyWords: List<String> = emptyList(),
    val lastPracticedAt: Long = 0L
)

@Singleton
class ShadowingHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _logsFlow = MutableStateFlow(loadAll())
    val logsFlow: StateFlow<Map<String, ShadowingPracticeLog>> = _logsFlow.asStateFlow()

    fun getLog(itemId: String): ShadowingPracticeLog? = _logsFlow.value[itemId]

    fun recordAttempt(
        itemId: String,
        sentenceEn: String,
        score: Int,
        trickyWords: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): ShadowingPracticeLog {
        val currentMap = _logsFlow.value.toMutableMap()
        val prev = currentMap[itemId]

        val clampedScore = score.coerceIn(0, 100)
        // Keep the latest tricky words prioritized, capped to 6 most recent stumbling words
        val mergedTricky = (trickyWords + (prev?.trickyWords ?: emptyList()))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(6)

        val updated = ShadowingPracticeLog(
            itemId = itemId,
            sentenceEn = sentenceEn,
            attemptsCount = (prev?.attemptsCount ?: 0) + 1,
            lastScore = clampedScore,
            bestScore = maxOf(prev?.bestScore ?: 0, clampedScore),
            trickyWords = mergedTricky,
            lastPracticedAt = nowMillis
        )

        currentMap[itemId] = updated
        persistAll(currentMap)
        _logsFlow.value = currentMap
        return updated
    }

    private fun loadAll(): Map<String, ShadowingPracticeLog> {
        val raw = prefs.getString(KEY_LOGS_JSON, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, ShadowingPracticeLog>>(raw)
        }.getOrDefault(emptyMap())
    }

    private fun persistAll(map: Map<String, ShadowingPracticeLog>) {
        val serialized = json.encodeToString(map)
        prefs.edit().putString(KEY_LOGS_JSON, serialized).apply()
    }

    companion object {
        private const val PREFS_NAME = "enzii_shadowing_history"
        private const val KEY_LOGS_JSON = "practice_logs_json"
    }
}
