package com.enziidict.app.core.database

import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.data.local.dao.StudyProgressDao
import com.enziidict.app.data.local.entity.DailyStudyLog
import com.enziidict.app.data.local.entity.UserStreakState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

data class ProgressAwardResult(
    val xpAdded: Int,
    val totalXpToday: Int,
    val dailyXpGoal: Int,
    val streakBefore: Int,
    val streakAfter: Int,
    val streakIncreased: Boolean,
    val freezeUsedCount: Int,
    val freezeRemaining: Int,
    val goalJustCompleted: Boolean
)

enum class DailyQuestType {
    SRS_REVIEW,
    SHADOWING,
    SPEAKING
}

data class DailyQuestItem(
    val type: DailyQuestType,
    val title: String,
    val subtitle: String,
    val current: Int,
    val target: Int,
    val rewardXp: Int
) {
    val isCompleted: Boolean
        get() = current >= target

    val progress: Float
        get() = (current.toFloat() / target.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
}

data class WeekDayProgress(
    val date: LocalDate,
    val labelVi: String,
    val isToday: Boolean,
    val isFuture: Boolean,
    val isCompleted: Boolean,
    val xpEarned: Int
)

data class StreakTransition(
    val updatedState: UserStreakState,
    val streakBefore: Int,
    val streakAfter: Int,
    val streakIncreased: Boolean,
    val freezeUsedCount: Int
)

object StreakCalculator {

    private val DAY_LABELS_VI = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

    fun applyActivity(current: UserStreakState, activeDate: LocalDate): StreakTransition {
        val lastDate = current.lastActiveDate
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val effectiveBefore = effectiveDisplayStreak(current, activeDate)
        var freezeUsed = 0
        var nextFreeze = current.freezeCount.coerceAtLeast(0)
        var maintainedOrStarted = false

        val nextStreak = when {
            lastDate == null -> {
                maintainedOrStarted = true
                1
            }
            lastDate == activeDate -> {
                maintainedOrStarted = current.currentStreak == 0
                current.currentStreak.coerceAtLeast(1)
            }
            else -> {
                val gap = ChronoUnit.DAYS.between(lastDate, activeDate)
                when {
                    gap == 1L -> {
                        maintainedOrStarted = true
                        current.currentStreak + 1
                    }
                    gap > 1L -> {
                        val missedDays = (gap - 1L).toInt()
                        if (current.currentStreak > 0 && missedDays <= nextFreeze) {
                            freezeUsed = missedDays
                            nextFreeze -= missedDays
                            maintainedOrStarted = true
                            current.currentStreak + 1
                        } else {
                            maintainedOrStarted = false
                            1
                        }
                    }
                    else -> {
                        maintainedOrStarted = false
                        current.currentStreak.coerceAtLeast(1)
                    }
                }
            }
        }

        val streakIncreased = maintainedOrStarted && nextStreak > current.currentStreak
        if (streakIncreased && nextStreak % 7 == 0) {
            nextFreeze = (nextFreeze + 1).coerceAtMost(UserStreakState.MAX_FREEZE_COUNT)
        }

        val nextLongest = maxOf(current.longestStreak, nextStreak)
        val nextLastDate = if (lastDate == null || !activeDate.isBefore(lastDate)) {
            activeDate.toString()
        } else {
            current.lastActiveDate
        }

        return StreakTransition(
            updatedState = current.copy(
                currentStreak = nextStreak,
                longestStreak = nextLongest,
                freezeCount = nextFreeze,
                lastActiveDate = nextLastDate
            ),
            streakBefore = effectiveBefore,
            streakAfter = nextStreak,
            streakIncreased = streakIncreased,
            freezeUsedCount = freezeUsed
        )
    }

    fun effectiveDisplayStreak(state: UserStreakState, today: LocalDate = LocalDate.now()): Int {
        val lastDate = state.lastActiveDate
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return 0

        if (state.currentStreak <= 0) return 0
        val gap = ChronoUnit.DAYS.between(lastDate, today)
        return when {
            gap <= 1L -> state.currentStreak
            (gap - 1L) <= state.freezeCount.coerceAtLeast(0).toLong() -> state.currentStreak
            else -> 0
        }
    }

    fun buildDailyQuests(todayLog: DailyStudyLog?): List<DailyQuestItem> {
        val reviewed = todayLog?.wordsReviewed ?: 0
        val shadowing = todayLog?.shadowingCount ?: 0
        val speakingMin = todayLog?.speakingMinutes ?: 0

        return listOf(
            DailyQuestItem(
                type = DailyQuestType.SRS_REVIEW,
                title = "Ôn 10 thẻ từ vựng",
                subtitle = "Chạm để mở Sổ tay Flashcard SM-2",
                current = reviewed.coerceAtLeast(0),
                target = 10,
                rewardXp = 20
            ),
            DailyQuestItem(
                type = DailyQuestType.SHADOWING,
                title = "Hoàn thành 2 câu Shadowing",
                subtitle = "Đọc chấm điểm ASR hoặc AI Coach",
                current = shadowing.coerceAtLeast(0),
                target = 2,
                rewardXp = 20
            ),
            DailyQuestItem(
                type = DailyQuestType.SPEAKING,
                title = "Trò chuyện 2 phút với AI",
                subtitle = "Thực hành phản xạ với gia sư Speaking",
                current = speakingMin.coerceAtLeast(0),
                target = 2,
                rewardXp = 25
            )
        )
    }

    fun buildCurrentWeekStrip(
        logs: List<DailyStudyLog>,
        today: LocalDate = LocalDate.now()
    ): List<WeekDayProgress> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val byDate = logs.associateBy { it.dateYyyyMmDd }

        return (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val xp = byDate[date.toString()]?.xpEarned ?: 0
            WeekDayProgress(
                date = date,
                labelVi = DAY_LABELS_VI[offset],
                isToday = date == today,
                isFuture = date.isAfter(today),
                isCompleted = xp > 0,
                xpEarned = xp
            )
        }
    }
}

@Singleton
class LearningProgressRepository private constructor(
    private val studyProgressDao: StudyProgressDao,
    private val settingsDataStore: SettingsDataStore?,
    private val onSyncActivity: (suspend (Long) -> Unit)?
) {
    @Inject
    constructor(
        studyProgressDao: StudyProgressDao,
        settingsDataStore: SettingsDataStore
    ) : this(studyProgressDao, settingsDataStore, null)

    internal constructor(
        studyProgressDao: StudyProgressDao,
        syncSettingsActivity: suspend (Long) -> Unit
    ) : this(studyProgressDao, null, syncSettingsActivity)

    private val mutex = Mutex()

    fun observeTodayLog(today: LocalDate = LocalDate.now()): Flow<DailyStudyLog?> =
        studyProgressDao.observeLogForDate(today.toString())

    fun observeStreakState(): Flow<UserStreakState> =
        studyProgressDao.observeStreakState().map { it ?: UserStreakState.default() }

    fun observeLogsForDays(days: Int, today: LocalDate = LocalDate.now()): Flow<List<DailyStudyLog>> {
        val since = today.minusDays((days - 1).coerceAtLeast(0).toLong())
        return studyProgressDao.observeLogsFrom(since.toString())
    }

    suspend fun recordFlashcardReview(
        isAccurate: Boolean,
        isFirstTimeLearned: Boolean,
        today: LocalDate = LocalDate.now()
    ): ProgressAwardResult {
        val xp = if (isAccurate) 10 else 5
        return applyLogDelta(
            date = today,
            wordsReviewedDelta = 1,
            wordsLearnedDelta = if (isFirstTimeLearned) 1 else 0,
            speakingMinutesDelta = 0,
            shadowingCountDelta = 0,
            xpDelta = xp
        )
    }

    suspend fun recordShadowingCompleted(
        scorePercent: Int,
        today: LocalDate = LocalDate.now()
    ): ProgressAwardResult {
        val xp = if (scorePercent >= 80) 20 else 15
        return applyLogDelta(
            date = today,
            wordsReviewedDelta = 0,
            wordsLearnedDelta = 0,
            speakingMinutesDelta = 0,
            shadowingCountDelta = 1,
            xpDelta = xp
        )
    }

    suspend fun recordSpeakingSession(
        durationSec: Int,
        today: LocalDate = LocalDate.now()
    ): ProgressAwardResult {
        val minutes = ((durationSec.coerceAtLeast(1) + 30) / 60).coerceAtLeast(1)
        val xp = (minutes * 10).coerceAtMost(60)
        return applyLogDelta(
            date = today,
            wordsReviewedDelta = 0,
            wordsLearnedDelta = 0,
            speakingMinutesDelta = minutes,
            shadowingCountDelta = 0,
            xpDelta = xp
        )
    }

    suspend fun recordReadingCompleted(
        wordsCount: Int = 3,
        today: LocalDate = LocalDate.now()
    ): ProgressAwardResult {
        return applyLogDelta(
            date = today,
            wordsReviewedDelta = 0,
            wordsLearnedDelta = wordsCount.coerceAtLeast(1),
            speakingMinutesDelta = 0,
            shadowingCountDelta = 0,
            xpDelta = 20
        )
    }

    suspend fun recordWordSaved(today: LocalDate = LocalDate.now()): ProgressAwardResult {
        return applyLogDelta(
            date = today,
            wordsReviewedDelta = 0,
            wordsLearnedDelta = 1,
            speakingMinutesDelta = 0,
            shadowingCountDelta = 0,
            xpDelta = 3
        )
    }

    private suspend fun applyLogDelta(
        date: LocalDate,
        wordsReviewedDelta: Int,
        wordsLearnedDelta: Int,
        speakingMinutesDelta: Int,
        shadowingCountDelta: Int,
        xpDelta: Int
    ): ProgressAwardResult = mutex.withLock {
        val dateKey = date.toString()
        val currentStreakState = studyProgressDao.getStreakState() ?: UserStreakState.default()
        val goal = currentStreakState.dailyXpGoal.coerceAtLeast(10)

        val existingLog = studyProgressDao.getLogForDate(dateKey) ?: DailyStudyLog(
            dateYyyyMmDd = dateKey,
            wordsReviewed = 0,
            wordsLearned = 0,
            speakingMinutes = 0,
            shadowingCount = 0,
            xpEarned = 0,
            goalCompleted = false
        )

        val wasGoalCompleted = existingLog.goalCompleted
        val newXp = existingLog.xpEarned + xpDelta
        val isGoalCompletedNow = wasGoalCompleted || newXp >= goal

        val updatedLog = existingLog.copy(
            wordsReviewed = existingLog.wordsReviewed + wordsReviewedDelta,
            wordsLearned = existingLog.wordsLearned + wordsLearnedDelta,
            speakingMinutes = existingLog.speakingMinutes + speakingMinutesDelta,
            shadowingCount = existingLog.shadowingCount + shadowingCountDelta,
            xpEarned = newXp,
            goalCompleted = isGoalCompletedNow
        )
        studyProgressDao.upsertLog(updatedLog)

        val transition = StreakCalculator.applyActivity(currentStreakState, date)
        studyProgressDao.upsertStreakState(transition.updatedState)
        settingsDataStore?.let { ds ->
            runCatching { ds.recordStudyActivity(date.toEpochDay()) }
        }
        onSyncActivity?.invoke(date.toEpochDay())

        ProgressAwardResult(
            xpAdded = xpDelta,
            totalXpToday = updatedLog.xpEarned,
            dailyXpGoal = goal,
            streakBefore = transition.streakBefore,
            streakAfter = transition.streakAfter,
            streakIncreased = transition.streakIncreased,
            freezeUsedCount = transition.freezeUsedCount,
            freezeRemaining = transition.updatedState.freezeCount,
            goalJustCompleted = !wasGoalCompleted && isGoalCompletedNow
        )
    }
}
