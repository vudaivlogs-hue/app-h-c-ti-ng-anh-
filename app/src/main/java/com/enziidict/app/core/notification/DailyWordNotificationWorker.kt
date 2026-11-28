package com.enziidict.app.core.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.model.CollocationItem
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.model.ExampleItem
import com.enziidict.app.core.network.DailyWordSource
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.data.local.dao.SavedWordDao
import com.enziidict.app.feature.shadowing.ShadowingPresets
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.LocalDate

@HiltWorker
class DailyWordNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsDataStore: SettingsDataStore,
    private val savedWordDao: SavedWordDao,
    private val dictionaryRepository: DictionaryRepository,
    private val json: Json
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val forcePreview = inputData.getBoolean(KEY_FORCE_PREVIEW, false)
        val slot = inputData.getString(KEY_SLOT) ?: "morning"
        val settings = settingsDataStore.settingsFlow.first()

        if (!forcePreview && !settings.dailyWordEnabled) {
            return Result.success()
        }

        val daySeed = LocalDate.now().toEpochDay().toInt() + if (slot == "afternoon") 7 else 0

        val content = when (settings.dailyWordSource) {
            DailyWordSource.SRS_DUE -> {
                pickDueSrsEntry(daySeed)?.let { EnziiNotificationHelper.buildWordNotificationContent(it) }
                    ?: pickCommonWordEntry(daySeed)?.let { EnziiNotificationHelper.buildWordNotificationContent(it) }
            }
            DailyWordSource.COMMON_VOCAB -> {
                pickCommonWordEntry(daySeed)?.let { EnziiNotificationHelper.buildWordNotificationContent(it) }
            }
            DailyWordSource.MIXED -> {
                val preferSentence = (slot == "afternoon") || (daySeed % 2 != 0)
                if (preferSentence) {
                    val presets = ShadowingPresets.ALL
                    val item = presets[Math.floorMod(daySeed, presets.size)]
                    EnziiNotificationHelper.buildSentenceNotificationContent(item)
                } else {
                    (pickDueSrsEntry(daySeed) ?: pickCommonWordEntry(daySeed))
                        ?.let { EnziiNotificationHelper.buildWordNotificationContent(it) }
                }
            }
        }

        if (content != null) {
            EnziiNotificationHelper.postDailyWordOrSentence(applicationContext, content)
        }
        return Result.success()
    }

    private suspend fun pickDueSrsEntry(seed: Int): DictionaryEntry? {
        val due = savedWordDao.getDueWords(System.currentTimeMillis() + 60_000L, limit = 50)
        if (due.isEmpty()) return null
        val picked = due[Math.floorMod(seed, due.size)].entry
        return DictionaryEntry(
            id = picked.id,
            word = picked.word,
            ipaUk = picked.ipaUk,
            ipaUs = picked.ipaUs,
            pos = picked.pos,
            meaningVi = picked.meaningVi,
            examples = runCatching { json.decodeFromString<List<ExampleItem>>(picked.examplesJson) }.getOrDefault(emptyList()),
            synonyms = runCatching { json.decodeFromString<List<String>>(picked.synonymsJson) }.getOrDefault(emptyList()),
            collocations = runCatching { json.decodeFromString<List<CollocationItem>>(picked.collocationsJson) }.getOrDefault(emptyList()),
            cefrLevel = picked.cefrLevel,
            frequencyRank = picked.frequencyRank
        )
    }

    private suspend fun pickCommonWordEntry(seed: Int): DictionaryEntry? {
        val popular = dictionaryRepository.search("", limit = 50)
        if (popular.isEmpty()) return null
        return popular[Math.floorMod(seed, popular.size)]
    }

    companion object {
        const val KEY_SLOT = "slot"
        const val KEY_FORCE_PREVIEW = "force_preview"
    }
}
