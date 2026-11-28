package com.enziidict.app.core.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.data.local.dao.SavedWordDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class DailyStudyReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsDataStore: SettingsDataStore,
    private val savedWordDao: SavedWordDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val forcePreview = inputData.getBoolean(KEY_FORCE_PREVIEW, false)
        val settings = settingsDataStore.settingsFlow.first()
        if (!forcePreview && !settings.dailyReminderEnabled) {
            return Result.success()
        }

        val dueCount = savedWordDao.getDueWords(System.currentTimeMillis() + 60_000L, limit = 100).size
        val content = EnziiNotificationHelper.buildStudyReminderContent(
            studiedToday = settings.studiedToday,
            streakDays = settings.streakDays,
            dueCount = dueCount,
            forcePreview = forcePreview
        ) ?: return Result.success()

        EnziiNotificationHelper.postStudyReminder(applicationContext, content)
        return Result.success()
    }

    companion object {
        const val KEY_FORCE_PREVIEW = "force_preview"
    }
}
