package com.enziidict.app.feature.srs

import com.enziidict.app.core.model.ReviewGrade
import com.enziidict.app.core.model.SrsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2SchedulerTest {

    @Test
    fun `first good review advances interval to 1 day and learning state`() {
        val now = 1_700_000_000_000L
        val res = Sm2Scheduler.schedule(
            currentEf = 2.5,
            currentIntervalDays = 0,
            currentRepetitions = 0,
            currentLapses = 0,
            grade = ReviewGrade.GOOD,
            nowMillis = now
        )

        assertEquals(1, res.repetitions)
        assertEquals(1, res.intervalDays)
        assertEquals(SrsState.LEARNING, res.state)
        assertEquals(now + 86_400_000L, res.dueAtMillis)
    }

    @Test
    fun `repeated good reviews follow SM-2 growth 1 then 6 then scaled by EF`() {
        val first = Sm2Scheduler.schedule(2.5, 0, 0, 0, ReviewGrade.GOOD, nowMillis = 0L)
        val second = Sm2Scheduler.schedule(
            first.easinessFactor, first.intervalDays, first.repetitions, first.lapses,
            ReviewGrade.GOOD, nowMillis = 0L
        )
        val third = Sm2Scheduler.schedule(
            second.easinessFactor, second.intervalDays, second.repetitions, second.lapses,
            ReviewGrade.GOOD, nowMillis = 0L
        )

        assertEquals(1, first.intervalDays)
        assertEquals(6, second.intervalDays)
        assertEquals(SrsState.REVIEW, second.state)
        assertTrue(third.intervalDays >= 14)
        assertEquals(3, third.repetitions)
    }

    @Test
    fun `again grade resets repetitions increments lapses and keeps EF floor`() {
        val res = Sm2Scheduler.schedule(
            currentEf = 1.35,
            currentIntervalDays = 10,
            currentRepetitions = 4,
            currentLapses = 1,
            grade = ReviewGrade.AGAIN,
            nowMillis = 1_000L
        )

        assertEquals(0, res.repetitions)
        assertEquals(2, res.lapses)
        assertEquals(SrsState.RELEARNING, res.state)
        assertTrue(res.easinessFactor >= 1.3)
        assertEquals(1_000L + 10 * 60_000L, res.dueAtMillis)
    }
}
