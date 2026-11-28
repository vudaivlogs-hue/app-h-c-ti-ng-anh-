package com.enziidict.app.core.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.enziidict.app.core.network.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class PreviewNotificationKind {
    REMINDER,
    WORD_OR_SENTENCE
}

@Singleton
class StudyNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun syncSchedules(settings: UserSettings) {
        val workManager = WorkManager.getInstance(context)

        if (settings.dailyReminderEnabled) {
            val delayMs = computeInitialDelayMillis(
                targetHour = settings.dailyReminderHour,
                targetMinute = settings.dailyReminderMinute
            )
            val request = PeriodicWorkRequestBuilder<DailyStudyReminderWorker>(24L, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_DAILY_REMINDER)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_DAILY_REMINDER,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(WORK_DAILY_REMINDER)
        }

        if (settings.dailyWordEnabled) {
            val morningDelayMs = computeInitialDelayMillis(targetHour = 8, targetMinute = 30)
            val morningInput = Data.Builder()
                .putString(DailyWordNotificationWorker.KEY_SLOT, "morning")
                .build()
            val morningRequest = PeriodicWorkRequestBuilder<DailyWordNotificationWorker>(24L, TimeUnit.HOURS)
                .setInitialDelay(morningDelayMs, TimeUnit.MILLISECONDS)
                .setInputData(morningInput)
                .addTag(WORK_DAILY_WORD_MORNING)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_DAILY_WORD_MORNING,
                ExistingPeriodicWorkPolicy.UPDATE,
                morningRequest
            )

            if (settings.dailyWordFrequency >= 2) {
                val afternoonDelayMs = computeInitialDelayMillis(targetHour = 13, targetMinute = 0)
                val afternoonInput = Data.Builder()
                    .putString(DailyWordNotificationWorker.KEY_SLOT, "afternoon")
                    .build()
                val afternoonRequest = PeriodicWorkRequestBuilder<DailyWordNotificationWorker>(24L, TimeUnit.HOURS)
                    .setInitialDelay(afternoonDelayMs, TimeUnit.MILLISECONDS)
                    .setInputData(afternoonInput)
                    .addTag(WORK_DAILY_WORD_AFTERNOON)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    WORK_DAILY_WORD_AFTERNOON,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    afternoonRequest
                )
            } else {
                workManager.cancelUniqueWork(WORK_DAILY_WORD_AFTERNOON)
            }
        } else {
            workManager.cancelUniqueWork(WORK_DAILY_WORD_MORNING)
            workManager.cancelUniqueWork(WORK_DAILY_WORD_AFTERNOON)
        }
    }

    fun triggerPreviewNow(kind: PreviewNotificationKind) {
        val workManager = WorkManager.getInstance(context)
        when (kind) {
            PreviewNotificationKind.REMINDER -> {
                val input = Data.Builder()
                    .putBoolean(DailyStudyReminderWorker.KEY_FORCE_PREVIEW, true)
                    .build()
                val request = OneTimeWorkRequestBuilder<DailyStudyReminderWorker>()
                    .setInputData(input)
                    .build()
                workManager.enqueue(request)
            }
            PreviewNotificationKind.WORD_OR_SENTENCE -> {
                val input = Data.Builder()
                    .putBoolean(DailyWordNotificationWorker.KEY_FORCE_PREVIEW, true)
                    .putString(DailyWordNotificationWorker.KEY_SLOT, "morning")
                    .build()
                val request = OneTimeWorkRequestBuilder<DailyWordNotificationWorker>()
                    .setInputData(input)
                    .build()
                workManager.enqueue(request)
            }
        }
    }

    companion object {
        const val WORK_DAILY_REMINDER = "enzii_daily_study_reminder"
        const val WORK_DAILY_WORD_MORNING = "enzii_daily_word_morning"
        const val WORK_DAILY_WORD_AFTERNOON = "enzii_daily_word_afternoon"

        fun computeInitialDelayMillis(
            targetHour: Int,
            targetMinute: Int,
            now: ZonedDateTime = ZonedDateTime.now()
        ): Long {
            val h = targetHour.coerceIn(0, 23)
            val m = targetMinute.coerceIn(0, 59)
            var nextRun = now.withHour(h).withMinute(m).withSecond(0).withNano(0)
            if (!nextRun.isAfter(now)) {
                nextRun = nextRun.plusDays(1)
            }
            return Duration.between(now, nextRun).toMillis().coerceAtLeast(1_000L)
        }
    }
}
