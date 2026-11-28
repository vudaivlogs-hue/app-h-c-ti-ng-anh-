package com.enziidict.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.database.DailyQuestItem
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.database.LearningProgressRepository
import com.enziidict.app.core.database.StreakCalculator
import com.enziidict.app.core.database.WeekDayProgress
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.data.local.dao.ReadingDao
import com.enziidict.app.data.local.dao.SavedWordDao
import com.enziidict.app.data.local.entity.DailyStudyLog
import com.enziidict.app.data.local.entity.UserStreakState
import com.enziidict.app.ui.components.MascotLevel
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
    val longestStreak: Int = 0,
    val freezeCount: Int = UserStreakState.INITIAL_FREEZE_COUNT,
    val xpToday: Int = 0,
    val dailyXpGoal: Int = UserStreakState.DEFAULT_DAILY_XP_GOAL,
    val weekStrip: List<WeekDayProgress> = emptyList(),
    val dailyQuests: List<DailyQuestItem> = StreakCalculator.buildDailyQuests(null),
    val studiedToday: Boolean = false,
    val dueCardsCount: Int = 0,
    val totalSavedCount: Int = 0,
    val masteredCount: Int = 0,
    val mascotLevel: MascotLevel = MascotLevel.BASIC,
    val showMascotTip: Boolean = false,
    val articlesCount: Int = 0,
    val wordOfTheDay: DictionaryEntry? = null,
    val isLoadingWord: Boolean = true,
    val statusMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    private val progressRepository: LearningProgressRepository,
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

    private val progressSnapshotFlow = combine(
        progressRepository.observeStreakState(),
        progressRepository.observeTodayLog(),
        progressRepository.observeLogsForDays(14),
        savedWordDao.observeMasteredCount()
    ) { streakState, todayLog, recentLogs, masteredCount ->
        ProgressSnapshot(streakState, todayLog, recentLogs, masteredCount)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        settingsDataStore.settingsFlow,
        progressSnapshotFlow,
        savedWordDao.observeDueCount(nowMillis),
        savedWordDao.observeTotalCount(),
        readingDao.observeAllArticles(),
        featuredPoolFlow,
        wordIndexOffsetFlow,
        isLoadingWordFlow,
        statusMessageFlow
    ) { values ->
        val settings = values[0] as com.enziidict.app.core.network.UserSettings
        val progress = values[1] as ProgressSnapshot
        val dueCount = values[2] as Int
        val totalSaved = values[3] as Int
        @Suppress("UNCHECKED_CAST")
        val articles = values[4] as List<com.enziidict.app.data.local.entity.BilingualArticle>
        @Suppress("UNCHECKED_CAST")
        val pool = values[5] as List<DictionaryEntry>
        val offset = values[6] as Int
        val loading = values[7] as Boolean
        val status = values[8] as String?

        val today = LocalDate.now()
        val todayEpoch = today.toEpochDay()
        val dailyWord = if (pool.isNotEmpty()) {
            val baseIndex = (todayEpoch % pool.size).toInt().coerceAtLeast(0)
            pool[(baseIndex + offset) % pool.size]
        } else {
            null
        }

        val effectiveStreak = StreakCalculator.effectiveDisplayStreak(progress.streakState, today)
            .coerceAtLeast(if (progress.streakState.lastActiveDate == null) settings.streakDays else 0)
        val xpToday = progress.todayLog?.xpEarned ?: 0
        val studiedToday = xpToday > 0 || settings.lastStudyEpochDay == todayEpoch

        HomeUiState(
            streakDays = effectiveStreak,
            longestStreak = maxOf(progress.streakState.longestStreak, effectiveStreak),
            freezeCount = progress.streakState.freezeCount,
            xpToday = xpToday,
            dailyXpGoal = progress.streakState.dailyXpGoal,
            weekStrip = StreakCalculator.buildCurrentWeekStrip(progress.recentLogs, today),
            dailyQuests = StreakCalculator.buildDailyQuests(progress.todayLog),
            studiedToday = studiedToday,
            dueCardsCount = dueCount,
            totalSavedCount = totalSaved,
            masteredCount = progress.masteredCount,
            mascotLevel = MascotLevel.fromMasteredCount(progress.masteredCount),
            showMascotTip = !settings.dismissedMascotTip,
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

    fun dismissMascotTip() {
        viewModelScope.launch {
            settingsDataStore.dismissMascotTip()
        }
    }

    fun speak(text: String, accent: TtsAccent) {
        ttsManager.speak(text, accent)
    }

    fun saveWordOfTheDay(entry: DictionaryEntry) {
        viewModelScope.launch {
            val inserted = dictionaryRepository.saveToDefaultWordbook(entry.id)
            if (inserted) {
                progressRepository.recordWordSaved()
            }
            statusMessageFlow.value = if (inserted) {
                "Đã lưu \"${entry.word}\" vào Sổ tay SRS"
            } else {
                "\"${entry.word}\" đã có sẵn trong Sổ tay SRS"
            }
        }
    }

    private data class ProgressSnapshot(
        val streakState: UserStreakState,
        val todayLog: DailyStudyLog?,
        val recentLogs: List<DailyStudyLog>,
        val masteredCount: Int
    )
}
