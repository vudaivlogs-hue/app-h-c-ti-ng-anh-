package com.enziidict.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.database.LearningProgressRepository
import com.enziidict.app.core.database.StreakCalculator
import com.enziidict.app.core.database.WeekDayProgress
import com.enziidict.app.data.local.dao.SavedWordDao
import com.enziidict.app.data.local.dao.SavedWordWithEntry
import com.enziidict.app.data.local.entity.DailyStudyLog
import com.enziidict.app.data.local.entity.UserStreakState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class SummaryPeriod(val labelVi: String, val days: Int) {
    WEEK("Tuần (7 ngày)", 7),
    MONTH("Tháng (30 ngày)", 30)
}

enum class SummaryChartMetric(val labelVi: String) {
    WORDS("Số từ đã học/ôn"),
    MINUTES("Phút luyện tập")
}

data class DailyChartBarUi(
    val date: LocalDate,
    val label: String,
    val wordsCount: Int,
    val practiceMinutes: Int,
    val xpEarned: Int,
    val goalCompleted: Boolean,
    val isToday: Boolean
)

data class TopStrengthUi(
    val skillNameVi: String,
    val detailVi: String,
    val badgeLabel: String
)

data class LearningSummaryUiState(
    val period: SummaryPeriod = SummaryPeriod.WEEK,
    val metric: SummaryChartMetric = SummaryChartMetric.WORDS,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val freezeCount: Int = UserStreakState.INITIAL_FREEZE_COUNT,
    val totalXpInPeriod: Int = 0,
    val totalWordsInPeriod: Int = 0,
    val totalMinutesInPeriod: Int = 0,
    val activeDaysInPeriod: Int = 0,
    val goalsCompletedInPeriod: Int = 0,
    val masteredCount: Int = 0,
    val totalSavedCount: Int = 0,
    val topStrength: TopStrengthUi = TopStrengthUi(
        skillNameVi = "Chưa có dữ liệu",
        detailVi = "Hãy ôn 1 thẻ SRS, luyện Shadowing hoặc nói AI để mở khóa thống kê",
        badgeLabel = "Mới bắt đầu"
    ),
    val chartBars: List<DailyChartBarUi> = emptyList(),
    val weekStrip: List<WeekDayProgress> = emptyList(),
    val weeklyHighlightWords: List<SavedWordWithEntry> = emptyList()
)

@HiltViewModel
class LearningSummaryViewModel @Inject constructor(
    progressRepository: LearningProgressRepository,
    savedWordDao: SavedWordDao,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val periodFlow = MutableStateFlow(SummaryPeriod.WEEK)
    private val metricFlow = MutableStateFlow(SummaryChartMetric.WORDS)

    private val sevenDaysAgoMillis = LocalDate.now()
        .minusDays(6)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    val uiState: StateFlow<LearningSummaryUiState> = combine(
        periodFlow,
        metricFlow,
        progressRepository.observeStreakState(),
        progressRepository.observeLogsForDays(30),
        savedWordDao.observeMasteredCount(),
        savedWordDao.observeTotalCount(),
        savedWordDao.observeRecentlyStudiedWords(sevenDaysAgoMillis, limit = 10)
    ) { values ->
        val period = values[0] as SummaryPeriod
        val metric = values[1] as SummaryChartMetric
        val streakState = values[2] as UserStreakState
        @Suppress("UNCHECKED_CAST")
        val logs30 = values[3] as List<DailyStudyLog>
        val mastered = values[4] as Int
        val totalSaved = values[5] as Int
        @Suppress("UNCHECKED_CAST")
        val recentWords = values[6] as List<SavedWordWithEntry>

        val today = LocalDate.now()
        val byDate = logs30.associateBy { it.dateYyyyMmDd }
        val dayFmt = DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault())
        val weekFmt = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

        val bars = (period.days - 1 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            val log = byDate[d.toString()]
            val words = (log?.wordsReviewed ?: 0) + (log?.wordsLearned ?: 0)
            val minutes = estimatePracticeMinutes(log)
            val label = if (period == SummaryPeriod.WEEK) {
                weekFmt[d.dayOfWeek.value - 1]
            } else {
                d.format(dayFmt)
            }
            DailyChartBarUi(
                date = d,
                label = label,
                wordsCount = words,
                practiceMinutes = minutes,
                xpEarned = log?.xpEarned ?: 0,
                goalCompleted = log?.goalCompleted == true,
                isToday = d == today
            )
        }

        val periodLogs = bars.mapNotNull { byDate[it.date.toString()] }
        val totalWords = bars.sumOf { it.wordsCount }
        val totalMinutes = bars.sumOf { it.practiceMinutes }
        val totalXp = bars.sumOf { it.xpEarned }
        val activeDays = bars.count { it.xpEarned > 0 }
        val goalsCompleted = bars.count { it.goalCompleted }

        LearningSummaryUiState(
            period = period,
            metric = metric,
            currentStreak = StreakCalculator.effectiveDisplayStreak(streakState, today),
            longestStreak = streakState.longestStreak,
            freezeCount = streakState.freezeCount,
            totalXpInPeriod = totalXp,
            totalWordsInPeriod = totalWords,
            totalMinutesInPeriod = totalMinutes,
            activeDaysInPeriod = activeDays,
            goalsCompletedInPeriod = goalsCompleted,
            masteredCount = mastered,
            totalSavedCount = totalSaved,
            topStrength = computeTopStrength(periodLogs),
            chartBars = bars,
            weekStrip = StreakCalculator.buildCurrentWeekStrip(logs30, today),
            weeklyHighlightWords = recentWords
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningSummaryUiState())

    fun selectPeriod(period: SummaryPeriod) {
        periodFlow.value = period
    }

    fun selectMetric(metric: SummaryChartMetric) {
        metricFlow.value = metric
    }

    fun speakWord(word: String) {
        ttsManager.speak(word, TtsAccent.US)
    }

    companion object {
        fun estimatePracticeMinutes(log: DailyStudyLog?): Int {
            if (log == null) return 0
            val estimatedExtrasSec = log.wordsReviewed * 25 + log.shadowingCount * 45 + log.wordsLearned * 20
            val total = log.speakingMinutes + (estimatedExtrasSec / 60)
            return if (total > 0) total else if (log.xpEarned > 0) 1 else 0
        }

        fun computeTopStrength(logs: List<DailyStudyLog>): TopStrengthUi {
            val srsCount = logs.sumOf { it.wordsReviewed + it.wordsLearned }
            val speakingMins = logs.sumOf { it.speakingMinutes }
            val shadowingCount = logs.sumOf { it.shadowingCount }

            if (srsCount == 0 && speakingMins == 0 && shadowingCount == 0) {
                return TopStrengthUi(
                    skillNameVi = "Chưa có dữ liệu",
                    detailVi = "Hãy hoàn thành 1 hoạt động học để xem điểm mạnh nổi bật",
                    badgeLabel = "Khởi động"
                )
            }

            // Weighted effort scores to compare minutes, cards, and shadowing sentences fairly
            val srsScore = srsCount * 2
            val speakingScore = speakingMins * 8
            val shadowingScore = shadowingCount * 6

            return when {
                speakingScore >= srsScore && speakingScore >= shadowingScore && speakingMins > 0 -> TopStrengthUi(
                    skillNameVi = "Giao tiếp AI Speaking",
                    detailVi = "Đã luyện nói $speakingMins phút trong giai đoạn này",
                    badgeLabel = "Phản xạ nói"
                )
                shadowingScore >= srsScore && shadowingScore > speakingScore && shadowingCount > 0 -> TopStrengthUi(
                    skillNameVi = "Luyện phát âm Shadowing",
                    detailVi = "Đã hoàn thành $shadowingCount lượt đọc đối chiếu",
                    badgeLabel = "Ngữ điệu chuẩn"
                )
                else -> TopStrengthUi(
                    skillNameVi = "Ghi nhớ Từ vựng SRS",
                    detailVi = "Đã học và ôn $srsCount lượt từ trong sổ tay",
                    badgeLabel = "Từ vựng bền"
                )
            }
        }
    }
}
