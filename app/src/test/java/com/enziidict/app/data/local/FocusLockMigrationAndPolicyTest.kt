package com.enziidict.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.database.EmergencyUnlockResult
import com.enziidict.app.core.database.FocusLockPolicyEvaluator
import com.enziidict.app.core.database.FocusLockRepository
import com.enziidict.app.core.database.FocusLockStatus
import com.enziidict.app.data.local.entity.DailyStudyLog
import com.enziidict.app.data.local.entity.FocusLockConfig
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
class FocusLockMigrationAndPolicyTest {

    @Test
    fun `migration from v3 to v4 preserves study progress and seeds focus_lock_config`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_v3_to_v4.db"
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
                CREATE TABLE daily_study_logs (
                    date_yyyy_mm_dd TEXT NOT NULL,
                    words_reviewed INTEGER NOT NULL,
                    words_learned INTEGER NOT NULL,
                    speaking_minutes INTEGER NOT NULL,
                    shadowing_count INTEGER NOT NULL,
                    xp_earned INTEGER NOT NULL,
                    goal_completed INTEGER NOT NULL,
                    PRIMARY KEY(date_yyyy_mm_dd)
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE TABLE user_streak_state (
                    id INTEGER NOT NULL,
                    current_streak INTEGER NOT NULL,
                    longest_streak INTEGER NOT NULL,
                    freeze_count INTEGER NOT NULL,
                    last_active_date TEXT,
                    daily_xp_goal INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )

            sqlite.execSQL(
                """
                INSERT INTO user_streak_state (
                    id, current_streak, longest_streak, freeze_count, last_active_date, daily_xp_goal
                ) VALUES (1, 4, 8, 2, '2026-11-28', 50)
                """.trimIndent()
            )
            sqlite.version = 3
        }

        val migratedDb = Room.databaseBuilder(context, EnziiDatabase::class.java, dbName)
            .addMigrations(EnziiDatabaseMigrations.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            val streak = migratedDb.studyProgressDao().getStreakState()!!
            assertEquals(4, streak.currentStreak)
            assertEquals(2, streak.freezeCount)

            val cfg = migratedDb.focusLockDao().getConfig()!!
            assertFalse(cfg.isEnabled)
            assertEquals(20, cfg.requiredStudyMinutes)
            assertEquals(10, cfg.requiredKnowledgeCount)
            assertEquals(5, cfg.emergencyUnlockDurationMinutes)
            assertEquals(2, cfg.maxEmergencyUnlocksPerDay)

            val repo = FocusLockRepository(migratedDb.focusLockDao(), migratedDb.studyProgressDao())
            repo.setEnabled(true)
            repo.toggleLockedApp("com.zhiliaoapp.musically", "TikTok", locked = true)
            repo.updateEmergencySettings(durationMinutes = 5, maxPerDay = 2)

            val day1 = LocalDate.of(2026, 11, 28)
            val now1 = 1_000_000L

            // Initial locking status before any study
            val evalLocked = repo.getCurrentEvaluation(nowMillis = now1, today = day1)
            assertEquals(FocusLockStatus.LOCKING, evalLocked.status)
            assertTrue(
                FocusLockPolicyEvaluator.shouldBlockPackage(
                    "com.zhiliaoapp.musically",
                    evalLocked,
                    "com.enziidict.app"
                )
            )
            assertFalse(
                FocusLockPolicyEvaluator.shouldBlockPackage(
                    "com.enziidict.app",
                    evalLocked,
                    "com.enziidict.app"
                )
            )

            // Trigger 2 emergency unlocks -> 3rd attempt must hit daily limit
            val g1 = repo.triggerEmergencyUnlock(nowMillis = now1, today = day1)
            assertTrue(g1 is EmergencyUnlockResult.Granted)
            val evalDuringEmergency = repo.getCurrentEvaluation(nowMillis = now1 + 60_000L, today = day1)
            assertEquals(FocusLockStatus.EMERGENCY_UNLOCKED, evalDuringEmergency.status)
            assertFalse(
                FocusLockPolicyEvaluator.shouldBlockPackage(
                    "com.zhiliaoapp.musically",
                    evalDuringEmergency,
                    "com.enziidict.app"
                )
            )

            val g2 = repo.triggerEmergencyUnlock(nowMillis = now1 + 400_000L, today = day1)
            assertTrue(g2 is EmergencyUnlockResult.Granted)
            val g3 = repo.triggerEmergencyUnlock(nowMillis = now1 + 800_000L, today = day1)
            assertTrue(g3 is EmergencyUnlockResult.LimitReached)

            // Next day automatically resets daily emergency quota
            val day2 = day1.plusDays(1)
            repo.resetEmergencyIfNewDay(today = day2)
            val evalDay2 = repo.getCurrentEvaluation(nowMillis = now1 + 86_400_000L, today = day2)
            assertEquals(2, evalDay2.emergencyRemainingUsesToday)
            assertEquals(FocusLockStatus.LOCKING, evalDay2.status)
            assertEquals(1, migratedDb.focusLockDao().observeLockedApps().first().size)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun `policy evaluator handles requireBoth vs either condition accurately`() {
        val log = DailyStudyLog(
            dateYyyyMmDd = "2026-11-28",
            wordsReviewed = 12,
            wordsLearned = 2,
            speakingMinutes = 4, // 4 + (12*25 + 2*20)/60 = 4 + 5 = 9 minutes studied, 14 knowledge
            shadowingCount = 0,
            xpEarned = 140,
            goalCompleted = true
        )
        val apps = setOf("com.facebook.katana")
        val day = LocalDate.of(2026, 11, 28)

        val eitherConfig = FocusLockConfig.default().copy(
            isEnabled = true,
            requiredStudyMinutes = 20,
            requiredKnowledgeCount = 10,
            requireBoth = false
        )
        val eitherEval = FocusLockPolicyEvaluator.evaluate(eitherConfig, apps, log, 1_000L, day)
        assertEquals(FocusLockStatus.UNLOCKED_BY_GOAL, eitherEval.status)

        val bothConfig = eitherConfig.copy(requireBoth = true)
        val bothEval = FocusLockPolicyEvaluator.evaluate(bothConfig, apps, log, 1_000L, day)
        assertEquals(FocusLockStatus.LOCKING, bothEval.status)

        val minutesOnlyConfig = bothConfig.copy(requiredKnowledgeCount = 0, requiredStudyMinutes = 8)
        val minutesOnlyEval = FocusLockPolicyEvaluator.evaluate(minutesOnlyConfig, apps, log, 1_000L, day)
        assertEquals(FocusLockStatus.UNLOCKED_BY_GOAL, minutesOnlyEval.status)
    }
}
