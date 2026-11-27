package com.enziidict.app.feature.srs

import com.enziidict.app.core.model.ReviewGrade
import com.enziidict.app.core.model.Sm2Result
import com.enziidict.app.core.model.SrsState
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure Kotlin implementation of the SuperMemo-2 (SM-2) spaced repetition algorithm.
 */
object Sm2Scheduler {

    private const val MIN_EASINESS_FACTOR = 1.3
    private const val MILLIS_PER_DAY = 86_400_000L

    fun schedule(
        currentEf: Double,
        currentIntervalDays: Int,
        currentRepetitions: Int,
        currentLapses: Int,
        grade: ReviewGrade,
        nowMillis: Long = System.currentTimeMillis()
    ): Sm2Result {
        val q = grade.quality

        val newEf = max(
            MIN_EASINESS_FACTOR,
            currentEf + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        )

        val newRepetitions: Int
        val newIntervalDays: Int
        val newLapses: Int
        val newState: SrsState

        if (q < 3) {
            newRepetitions = 0
            newIntervalDays = 1
            newLapses = currentLapses + 1
            newState = SrsState.RELEARNING
        } else {
            newLapses = currentLapses
            newRepetitions = currentRepetitions + 1
            val baseInterval = when (newRepetitions) {
                1 -> 1
                2 -> 6
                else -> (currentIntervalDays * newEf).roundToInt().coerceAtLeast(1)
            }
            newIntervalDays = when (grade) {
                ReviewGrade.HARD -> max(1, (baseInterval * 0.8).roundToInt())
                ReviewGrade.EASY -> max(baseInterval + 1, (baseInterval * 1.3).roundToInt())
                else -> baseInterval
            }
            newState = if (newRepetitions >= 2) SrsState.REVIEW else SrsState.LEARNING
        }

        val dueAtMillis = if (grade == ReviewGrade.AGAIN) {
            nowMillis + 10 * 60_000L
        } else {
            nowMillis + newIntervalDays.toLong() * MILLIS_PER_DAY
        }

        return Sm2Result(
            easinessFactor = newEf,
            intervalDays = newIntervalDays,
            repetitions = newRepetitions,
            lapses = newLapses,
            state = newState,
            dueAtMillis = dueAtMillis
        )
    }

    fun previewAllGrades(
        currentEf: Double,
        currentIntervalDays: Int,
        currentRepetitions: Int,
        currentLapses: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<ReviewGrade, Sm2Result> {
        return ReviewGrade.entries.associateWith { grade ->
            schedule(
                currentEf = currentEf,
                currentIntervalDays = currentIntervalDays,
                currentRepetitions = currentRepetitions,
                currentLapses = currentLapses,
                grade = grade,
                nowMillis = nowMillis
            )
        }
    }

    fun formatIntervalLabel(grade: ReviewGrade, result: Sm2Result): String {
        if (grade == ReviewGrade.AGAIN) return "10 phút"
        val days = result.intervalDays
        return when {
            days <= 1 -> "1 ngày"
            days < 30 -> "$days ngày"
            days < 365 -> "${(days / 30.0).roundToInt().coerceAtLeast(1)} tháng"
            else -> "${(days / 365.0).roundToInt().coerceAtLeast(1)} năm"
        }
    }
}
