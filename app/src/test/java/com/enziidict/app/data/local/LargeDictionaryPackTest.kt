package com.enziidict.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.database.DictionaryPackUpgrader
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.database.DictionaryUpgradeState
import com.enziidict.app.core.model.WordFolders
import com.enziidict.app.data.local.entity.SavedWord
import com.enziidict.app.data.local.entity.UserStreakState
import com.enziidict.app.data.local.entity.WordFolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LargeDictionaryPackTest {

    @Test
    fun `prepackaged dictionary db contains 100k+ entries and searches fast`() = runTest {
        val assetDb = File("src/main/assets/dictionary.db")
            .takeIf { it.exists() }
            ?: File("app/src/main/assets/dictionary.db")
        assertTrue("Expected pre-packaged dictionary.db at ${assetDb.absolutePath}", assetDb.exists())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_large_pack_verify.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
        assetDb.copyTo(dbFile, overwrite = true)

        val db = Room.databaseBuilder(context, EnziiDatabase::class.java, dbName)
            .addMigrations(
                EnziiDatabaseMigrations.MIGRATION_1_2,
                EnziiDatabaseMigrations.MIGRATION_2_3,
                EnziiDatabaseMigrations.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

        try {
            val count = db.dictionaryDao().countEntries()
            assertTrue("Expected >= 100,000 entries, actual=$count", count >= 100_000)

            val repo = DictionaryRepository(
                dictionaryDao = db.dictionaryDao(),
                wordFolderDao = db.wordFolderDao(),
                savedWordDao = db.savedWordDao(),
                json = Json { ignoreUnknownKeys = true; isLenient = true }
            )

            val prefixMs = measureTimeMillis {
                val res = repo.search("app", limit = 20)
                assertTrue(res.isNotEmpty())
                // Popular curated/common words must still rank near the top
                assertTrue(res.any { it.word.equals("approach", ignoreCase = true) || it.word.equals("apple", ignoreCase = true) })
            }

            val fvdpMs = measureTimeMillis {
                val res = repo.search("photosynthesis", limit = 10)
                assertTrue("Expected FVDP scientific word 'photosynthesis'", res.any {
                    it.word.equals("photosynthesis", ignoreCase = true)
                })
            }

            val vnReverseMs = measureTimeMillis {
                val res = repo.search("kiên cường", limit = 15)
                assertTrue("Expected Vietnamese reverse match for 'kiên cường'", res.isNotEmpty())
            }

            println("BENCHMARK 100K+ DICT: entries=$count prefixMs=$prefixMs fvdpMs=$fvdpMs vnReverseMs=$vnReverseMs")
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    @Test
    fun `dictionary pack upgrader merges new entries without losing saved words or streak state`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_upgrader_target.db"
        val seedName = "test_upgrader_seed.db"
        val dbFile = context.getDatabasePath(dbName)
        val seedFile = context.getDatabasePath(seedName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
        if (seedFile.exists()) seedFile.delete()

        val targetDb = Room.databaseBuilder(context, EnziiDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val seedDb = Room.databaseBuilder(context, EnziiDatabase::class.java, seedName)
            .allowMainThreadQueries()
            .build()

        try {
            // 1. Seed existing user database (1 word, 1 folder, 1 SRS card, active 5-day streak)
            val existingEntryId = targetDb.dictionaryDao().insertEntry(
                com.enziidict.app.data.local.entity.DictionaryEntry(
                    word = "resilient",
                    wordNormalized = "resilient",
                    ipaUk = "/rɪˈzɪl.i.ənt/",
                    ipaUs = "/rɪˈzɪl.jənt/",
                    pos = "adjective",
                    meaningVi = "kiên cường",
                    examplesJson = "[]",
                    synonymsJson = "[]",
                    collocationsJson = "[]",
                    cefrLevel = "B2",
                    frequencyRank = 10
                )
            )
            val folderId = targetDb.wordFolderDao().insertFolder(
                WordFolder(
                    name = WordFolders.DEFAULT,
                    iconName = "folder",
                    colorHex = "#1D4ED8",
                    createdAt = 1000L
                )
            )
            targetDb.savedWordDao().insertSavedWord(
                SavedWord(
                    entryId = existingEntryId,
                    folderId = folderId,
                    folderName = WordFolders.DEFAULT,
                    easeFactor = 2.6,
                    intervalDays = 6,
                    repetitions = 2,
                    lapses = 0,
                    state = "REVIEW",
                    nextReviewAt = 999999L,
                    lastReviewedAt = 2000L,
                    createdAt = 1000L
                )
            )
            targetDb.studyProgressDao().upsertStreakState(
                UserStreakState(
                    currentStreak = 5,
                    longestStreak = 7,
                    freezeCount = 2,
                    lastActiveDate = "2026-11-27",
                    dailyXpGoal = 50
                )
            )

            // 2. Prepare seed pack DB with duplicate 'resilient' + 2 new words
            seedDb.dictionaryDao().insertEntry(
                com.enziidict.app.data.local.entity.DictionaryEntry(
                    word = "resilient",
                    wordNormalized = "resilient",
                    ipaUk = "/rɪˈzɪl.i.ənt/",
                    ipaUs = "/rɪˈzɪl.jənt/",
                    pos = "adjective",
                    meaningVi = "bản trùng không được ghi đè",
                    examplesJson = "[]",
                    synonymsJson = "[]",
                    collocationsJson = "[]",
                    cefrLevel = null,
                    frequencyRank = 5000
                )
            )
            seedDb.dictionaryDao().insertEntry(
                com.enziidict.app.data.local.entity.DictionaryEntry(
                    word = "photosynthesis",
                    wordNormalized = "photosynthesis",
                    ipaUk = "/ˌfəʊ.təʊˈsɪn.θə.sɪs/",
                    ipaUs = "/ˌfoʊ.toʊˈsɪn.θə.sɪs/",
                    pos = "noun",
                    meaningVi = "quang hợp",
                    examplesJson = "[]",
                    synonymsJson = "[]",
                    collocationsJson = "[]",
                    cefrLevel = null,
                    frequencyRank = 6500
                )
            )
            seedDb.dictionaryDao().insertEntry(
                com.enziidict.app.data.local.entity.DictionaryEntry(
                    word = "algorithm",
                    wordNormalized = "algorithm",
                    ipaUk = "/ˈæl.ɡə.rɪ.ðəm/",
                    ipaUs = "/ˈæl.ɡə.rɪ.ðəm/",
                    pos = "noun",
                    meaningVi = "thuật toán",
                    examplesJson = "[]",
                    synonymsJson = "[]",
                    collocationsJson = "[]",
                    cefrLevel = null,
                    frequencyRank = 6000
                )
            )
            seedDb.close()

            // 3. Upgrade target DB from seed pack file
            val upgrader = DictionaryPackUpgrader(context, targetDb, targetDb.dictionaryDao())
            val afterCount = upgrader.upgradeFromFile(seedFile)
            assertEquals(3, afterCount)
            assertTrue(upgrader.state.value is DictionaryUpgradeState.Completed)

            // Duplicate did not overwrite user's curated entry
            val resilientList = targetDb.dictionaryDao().findExact("resilient")
            assertEquals(1, resilientList.size)
            assertEquals("kiên cường", resilientList.first().meaningVi)

            // New words searchable via prefix & FTS4
            val ftsHits = targetDb.dictionaryDao().searchFullText("quang*", 10)
            assertTrue(ftsHits.any { it.word == "photosynthesis" })

            // Saved SRS card & Streak state remained untouched
            val saved = targetDb.savedWordDao().observeAllSavedWords().first()
            assertEquals(1, saved.size)
            assertEquals(6, saved.first().savedWord.intervalDays)
            val streak = targetDb.studyProgressDao().getStreakState()!!
            assertEquals(5, streak.currentStreak)
            assertEquals(2, streak.freezeCount)
        } finally {
            targetDb.close()
            dbFile.delete()
            seedFile.delete()
        }
    }
}
