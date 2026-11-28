package com.enziidict.app.core.notification

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.model.ExampleItem
import com.enziidict.app.feature.shadowing.ShadowingPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyNotificationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `computeInitialDelayMillis calculates same-day delay and rolls over past target to tomorrow`() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val morning = ZonedDateTime.of(2026, 11, 28, 8, 0, 0, 0, zone)
        val sameDayDelay = StudyNotificationScheduler.computeInitialDelayMillis(20, 0, morning)
        assertEquals(12 * 3600 * 1000L, sameDayDelay)

        val lateNight = ZonedDateTime.of(2026, 11, 28, 22, 0, 0, 0, zone)
        val nextDayDelay = StudyNotificationScheduler.computeInitialDelayMillis(20, 0, lateNight)
        assertEquals(22 * 3600 * 1000L, nextDayDelay)
    }

    @Test
    fun `buildStudyReminderContent skips when goal already reached and protects streak otherwise`() {
        assertNull(
            EnziiNotificationHelper.buildStudyReminderContent(
                studiedToday = true,
                streakDays = 5,
                dueCount = 0,
                forcePreview = false
            )
        )

        val streakProtect = EnziiNotificationHelper.buildStudyReminderContent(
            studiedToday = false,
            streakDays = 7,
            dueCount = 4
        )
        assertNotNull(streakProtect)
        assertTrue(streakProtect!!.title.contains("7 ngày"))
        assertTrue(streakProtect.bigText.contains("4 thẻ"))

        val dueOnly = EnziiNotificationHelper.buildStudyReminderContent(
            studiedToday = true,
            streakDays = 3,
            dueCount = 6
        )
        assertNotNull(dueOnly)
        assertTrue(dueOnly!!.title.contains("6 thẻ"))
    }

    @Test
    fun `buildWord and buildSentence produce BigText content with accurate deep links`() {
        val sampleEntry = DictionaryEntry(
            id = 12L,
            word = "resilient",
            ipaUk = "/rɪˈzɪl.i.ənt/",
            ipaUs = "/rɪˈzɪl.jənt/",
            pos = "adjective",
            meaningVi = "kiên cường, mau phục hồi",
            examples = listOf(ExampleItem("She remained resilient after setbacks.", "Cô ấy vẫn kiên cường sau biến cố.")),
            synonyms = listOf("strong"),
            collocations = emptyList(),
            cefrLevel = "B2",
            frequencyRank = 200
        )
        val wordNotif = EnziiNotificationHelper.buildWordNotificationContent(sampleEntry)
        assertTrue(wordNotif.title.contains("resilient"))
        assertTrue(wordNotif.title.contains("/rɪˈzɪl.jənt/"))
        assertTrue(wordNotif.bigText.contains("kiên cường"))
        assertEquals(EnziiNotificationHelper.DEST_DICTIONARY_WORD, wordNotif.destination)
        assertEquals("resilient", wordNotif.wordQuery)

        val sentenceItem = ShadowingPresets.ALL.first()
        val sentenceNotif = EnziiNotificationHelper.buildSentenceNotificationContent(sentenceItem)
        assertTrue(sentenceNotif.bigText.contains(sentenceItem.sentenceEn))
        assertEquals(EnziiNotificationHelper.DEST_SHADOWING, sentenceNotif.destination)
        assertEquals(sentenceItem.id, sentenceNotif.shadowingId)
    }

    @Test
    fun `ensureChannels registers both channels on Android 14 and missing permission blocks safely`() {
        EnziiNotificationHelper.ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(nm.getNotificationChannel(EnziiNotificationHelper.CHANNEL_DAILY_REMINDER))
        assertNotNull(nm.getNotificationChannel(EnziiNotificationHelper.CHANNEL_DAILY_WORD))

        // On SDK 34 without POST_NOTIFICATIONS granted, posting must return false without throwing
        assertFalse(EnziiNotificationHelper.hasNotificationPermission(context))
        val reminder = EnziiNotificationHelper.buildStudyReminderContent(
            studiedToday = false,
            streakDays = 2,
            dueCount = 3
        )!!
        assertFalse(EnziiNotificationHelper.postStudyReminder(context, reminder))
    }
}
