package com.enziidict.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.database.DailyQuestType
import com.enziidict.app.core.database.LearningProgressRepository
import com.enziidict.app.core.database.StreakCalculator
import com.enziidict.app.data.local.entity.DailyStudyLog
import com.enziidict.app.data.local.entity.UserStreakState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyProgressMigrationAndLogicTest {

    @Test
    fun `migration from v2 to v3 preserves existing saved_words and seeds user_streak_state`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_v2_to_v3.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE dictionary_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    word TEXT NOT NULL,
                    word_normalized TEXT NOT NULL COLLATE NOCASE,
                    ipa_uk TEXT NOT NULL,
                    ipa_us TEXT NOT NULL,
                    pos TEXT NOT NULL,
                    meaning_vi TEXT NOT NULL,
                    examples_json TEXT NOT NULL,
                    synonyms_json TEXT NOT NULL,
                    collocations_json TEXT NOT NULL,
                    cefr_level TEXT,
                    frequency_rank INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE INDEX idx_dict_word_prefix ON dictionary_entries (word_normalized, frequency_rank)")
            sqlite.execSQL("CREATE INDEX idx_dict_exact_word_pos ON dictionary_entries (word_normalized, pos)")
            sqlite.execSQL("CREATE INDEX idx_dict_cefr_pos ON dictionary_entries (cefr_level, pos, frequency_rank)")

            sqlite.execSQL(
                """
                CREATE VIRTUAL TABLE dictionary_fts USING FTS4(
                    word TEXT NOT NULL,
                    meaning_vi TEXT NOT NULL,
                    collocations_json TEXT NOT NULL,
                    synonyms_json TEXT NOT NULL,
                    tokenize=unicode61 `remove_diacritics=1`,
                    content=`dictionary_entries`
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE TABLE word_folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    icon_name TEXT NOT NULL,
                    color_hex TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE UNIQUE INDEX idx_word_folders_name_unique ON word_folders (name)")

            sqlite.execSQL(
                """
                CREATE TABLE saved_words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    entry_id INTEGER NOT NULL,
                    folder_id INTEGER NOT NULL,
                    folder_name TEXT NOT NULL,
                    ease_factor REAL NOT NULL,
                    interval_days INTEGER NOT NULL,
                    repetitions INTEGER NOT NULL,
                    lapses INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    next_review_at INTEGER NOT NULL,
                    last_reviewed_at INTEGER,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(entry_id) REFERENCES dictionary_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(folder_id) REFERENCES word_folders(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE UNIQUE INDEX idx_saved_words_unique_entry_folder_id ON saved_words (folder_id, entry_id)")
            sqlite.execSQL("CREATE INDEX idx_saved_words_due_queue ON saved_words (folder_id, next_review_at)")
            sqlite.execSQL("CREATE INDEX idx_saved_words_entry_id ON saved_words (entry_id)")

            sqlite.execSQL(
                """
                CREATE TABLE speaking_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    topic TEXT NOT NULL,
                    voice_name TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER,
                    duration_sec INTEGER NOT NULL,
                    correction_suggestions_json TEXT
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE INDEX idx_speaking_sessions_time ON speaking_sessions (started_at)")

            sqlite.execSQL(
                """
                CREATE TABLE speaking_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    session_id INTEGER NOT NULL,
                    speaker TEXT NOT NULL,
                    transcript TEXT NOT NULL,
                    suggestion_note TEXT,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(session_id) REFERENCES speaking_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE INDEX idx_speaking_messages_session ON speaking_messages (session_id, created_at)")

            sqlite.execSQL(
                """
                CREATE TABLE bilingual_articles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title_en TEXT NOT NULL,
                    title_vi TEXT NOT NULL,
                    cefr_level TEXT NOT NULL,
                    category TEXT NOT NULL,
                    paragraphs_json TEXT NOT NULL,
                    read_progress REAL NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE INDEX idx_articles_level ON bilingual_articles (cefr_level, updated_at)")

            sqlite.execSQL(
                """
                INSERT INTO dictionary_entries (
                    id, word, word_normalized, ipa_uk, ipa_us, pos, meaning_vi,
                    examples_json, synonyms_json, collocations_json, cefr_level, frequency_rank
                ) VALUES (1, 'resilient', 'resilient', '/rɪˈzɪl.i.ənt/', '/rɪˈzɪl.jənt/', 'adjective', 'kiên cường', '[]', '[]', '[]', 'B2', 100)
                """.trimIndent()
            )
            sqlite.execSQL(
                "INSERT INTO word_folders (id, name, icon_name, color_hex, created_at) VALUES (1, 'Mặc định', 'folder', '#1D4ED8', 1000)"
            )
            sqlite.execSQL(
                """
                INSERT INTO saved_words (
                    id, entry_id, folder_id, folder_name, ease_factor, interval_days,
                    repetitions, lapses, state, next_review_at, last_reviewed_at, created_at
                ) VALUES (10, 1, 1, 'Mặc định', 2.6, 10, 4, 0, 'REVIEW', 5000, 2000, 1000)
                """.trimIndent()
            )
            sqlite.version = 2
        }

        val migratedDb = Room.databaseBuilder(context, EnziiDatabase::class.java, dbName)
            .addMigrations(EnziiDatabaseMigrations.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val masteredCount = migratedDb.savedWordDao().observeMasteredCount().first()
            assertEquals(1, masteredCount)

            val streakState = migratedDb.studyProgressDao().getStreakState()!!
            assertEquals(0, streakState.currentStreak)
            assertEquals(1, streakState.freezeCount)
            assertEquals(50, streakState.dailyXpGoal)

            val repo = LearningProgressRepository(
                studyProgressDao = migratedDb.studyProgressDao(),
                syncSettingsActivity = {}
            )

            val day1 = LocalDate.of(2026, 11, 20)
            val award1 = repo.recordFlashcardReview(isAccurate = true, isFirstTimeLearned = true, today = day1)
            assertEquals(10, award1.xpAdded)
            assertEquals(1, award1.streakAfter)
            assertTrue(award1.streakIncreased)
            assertFalse(award1.goalJustCompleted)

            // Reach daily goal (>= 50 XP)
            repo.recordShadowingCompleted(scorePercent = 85, today = day1) // +20 -> 30 XP
            val awardSpeak = repo.recordSpeakingSession(durationSec = 125, today = day1) // +20 -> 50 XP
            assertEquals(50, awardSpeak.totalXpToday)
            assertTrue(awardSpeak.goalJustCompleted)

            val logDay1 = migratedDb.studyProgressDao().getLogForDate(day1.toString())!!
            assertEquals(1, logDay1.wordsReviewed)
            assertEquals(1, logDay1.wordsLearned)
            assertEquals(1, logDay1.shadowingCount)
            assertEquals(2, logDay1.speakingMinutes)
            assertTrue(logDay1.goalCompleted)

            // Day 2 consecutive
            val day2 = day1.plusDays(1)
            val award2 = repo.recordFlashcardReview(isAccurate = true, isFirstTimeLearned = false, today = day2)
            assertEquals(2, award2.streakAfter)
            assertEquals(0, award2.freezeUsedCount)

            // Skip Day 3 -> Day 4 consumes 1 Streak Freeze and preserves streak!
            val day4 = day2.plusDays(2)
            val award4 = repo.recordFlashcardReview(isAccurate = true, isFirstTimeLearned = false, today = day4)
            assertEquals(3, award4.streakAfter)
            assertEquals(1, award4.freezeUsedCount)
            assertEquals(0, award4.freezeRemaining)

            // Skip Day 5 with 0 freezes left -> Day 6 resets streak to 1 while keeping longest = 3
            val day6 = day4.plusDays(2)
            val award6 = repo.recordFlashcardReview(isAccurate = true, isFirstTimeLearned = false, today = day6)
            assertEquals(1, award6.streakAfter)
            assertFalse(award6.streakIncreased)
            val persistedStreak = migratedDb.studyProgressDao().getStreakState()!!
            assertEquals(3, persistedStreak.longestStreak)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun `streak calculator builds daily quests, week strip, and weekly freeze reward`() {
        val state = UserStreakState(
            currentStreak = 6,
            longestStreak = 6,
            freezeCount = 1,
            lastActiveDate = "2026-11-26",
            dailyXpGoal = 50
        )
        val transition = StreakCalculator.applyActivity(state, LocalDate.of(2026, 11, 27))
        assertEquals(7, transition.streakAfter)
        assertTrue(transition.streakIncreased)
        assertEquals(2, transition.updatedState.freezeCount)

        val todayLog = DailyStudyLog(
            dateYyyyMmDd = "2026-11-27",
            wordsReviewed = 10,
            wordsLearned = 2,
            speakingMinutes = 1,
            shadowingCount = 2,
            xpEarned = 65,
            goalCompleted = true
        )
        val quests = StreakCalculator.buildDailyQuests(todayLog)
        assertEquals(3, quests.size)
        assertTrue(quests.first { it.type == DailyQuestType.SRS_REVIEW }.isCompleted)
        assertTrue(quests.first { it.type == DailyQuestType.SHADOWING }.isCompleted)
        assertFalse(quests.first { it.type == DailyQuestType.SPEAKING }.isCompleted)

        val strip = StreakCalculator.buildCurrentWeekStrip(
            logs = listOf(todayLog),
            today = LocalDate.of(2026, 11, 27)
        )
        assertEquals(7, strip.size)
        assertEquals(1, strip.count { it.isCompleted })
        assertTrue(strip.first { it.isToday }.isCompleted)
    }
}
