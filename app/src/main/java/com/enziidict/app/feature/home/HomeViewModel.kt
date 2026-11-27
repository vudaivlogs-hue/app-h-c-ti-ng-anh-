package com.enziidict.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.data.local.dao.ReadingDao
import com.enziidict.app.data.local.dao.SavedWordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val streakDays: Int = 0,
    val studiedToday: Boolean = false,
    val dueCardsCount: Int = 0,
    val totalSavedCount: Int = 0,
    val articlesCount: Int = 0,
    val wordOfTheDay: DictionaryEntry? = null,
    val isLoadingWord: Boolean = true,
    val statusMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    savedWordDao: SavedWordDao,
    readingDao: ReadingDao,
    private val settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val featuredPoolFlow = MutableStateFlow<List<DictionaryEntry>>(emptyList())
    private val wordIndexOffsetFlow = MutableStateFlow(0)
    private val isLoadingWordFlow = MutableStateFlow(true)
    private val statusMessageFlow = MutableStateFlow<String?>(null)

    private val nowMillis = System.currentTimeMillis() + 60_000L

    val uiState: StateFlow<HomeUiState> = combine(
        settingsDataStore.settingsFlow,
        savedWordDao.observeDueCount(nowMillis),
        savedWordDao.observeTotalCount(),
        readingDao.observeAllArticles(),
        featuredPoolFlow,
        wordIndexOffsetFlow,
        isLoadingWordFlow,
        statusMessageFlow
    ) { values ->
        val settings = values[0] as com.enziidict.app.core.network.UserSettings
        val dueCount = values[1] as Int
        val totalSaved = values[2] as Int
        @Suppress("UNCHECKED_CAST")
        val articles = values[3] as List<com.enziidict.app.data.local.entity.BilingualArticle>
        @Suppress("UNCHECKED_CAST")
        val pool = values[4] as List<DictionaryEntry>
        val offset = values[5] as Int
        val loading = values[6] as Boolean
        val status = values[7] as String?

        val todayEpoch = LocalDate.now().toEpochDay()
        val dailyWord = if (pool.isNotEmpty()) {
            val baseIndex = (todayEpoch % pool.size).toInt().coerceAtLeast(0)
            pool[(baseIndex + offset) % pool.size]
        } else {
            null
        }

        HomeUiState(
            streakDays = settings.streakDays,
            studiedToday = settings.lastStudyEpochDay == todayEpoch,
            dueCardsCount = dueCount,
            totalSavedCount = totalSaved,
            articlesCount = articles.size,
            wordOfTheDay = dailyWord,
            isLoadingWord = loading,
            statusMessage = status
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            isLoadingWordFlow.value = true
            val top = dictionaryRepository.search("", limit = 40)
            // Prefer rich entries with examples for Word of the Day
            val rich = top.filter { it.examples.isNotEmpty() }.ifEmpty { top }
            featuredPoolFlow.value = rich
            isLoadingWordFlow.value = false
        }
    }

    fun showNextFeaturedWord() {
        wordIndexOffsetFlow.value += 1
        statusMessageFlow.value = null
    }

    fun speak(text: String, accent: TtsAccent) {
        ttsManager.speak(text, accent)
    }

    fun saveWordOfTheDay(entry: DictionaryEntry) {
        viewModelScope.launch {
            val inserted = dictionaryRepository.saveToDefaultWordbook(entry.id)
            settingsDataStore.recordStudyActivity()
            statusMessageFlow.value = if (inserted) {
                "Đã lưu \"${entry.word}\" vào Sổ tay SRS"
            } else {
                "\"${entry.word}\" đã có sẵn trong Sổ tay SRS"
            }
        }
    }
}
