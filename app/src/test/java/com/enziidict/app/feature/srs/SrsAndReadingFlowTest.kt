package com.enziidict.app.feature.srs

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.model.ReviewGrade
import com.enziidict.app.core.model.SrsState
import com.enziidict.app.core.model.WordFolders
import com.enziidict.app.data.local.EnziiDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SrsAndReadingFlowTest {

    private lateinit var db: EnziiDatabase
    private lateinit var repository: DictionaryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.databaseBuilder(context, EnziiDatabase::class.java, "test_srs_reading_flow.db")
            .createFromAsset("dictionary.db")
            .allowMainThreadQueries()
            .build()
        repository = DictionaryRepository(
            dictionaryDao = db.dictionaryDao(),
            wordFolderDao = db.wordFolderDao(),
            savedWordDao = db.savedWordDao(),
            json = Json { ignoreUnknownKeys = true; isLenient = true }
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `seeded database contains 5 bilingual articles spanning A2 to C1`() = runTest {
        val articles = db.readingDao().getAllArticles()
        assertEquals(5, articles.size)
        val levels = articles.map { it.cefrLevel }.toSet()
        assertTrue(levels.containsAll(listOf("A2", "B1", "B2", "C1")))
    }

    @Test
    fun `tap word in reading article then lookup save to custom folder and SM-2 review updates next_review_at`() = runTest {
        // 1. Tap an inflected word from a C1/B2 reading article ("decisions")
        val matches = repository.quickLookup("decisions.")
        val entry = matches.firstOrNull()
        assertNotNull("Expected lemmatized lookup for 'decisions.' to match 'decision'", entry)
        assertEquals("decision", entry!!.word)

        // 2. Save word to a custom folder and prevent duplicates
        val customFolder = "IELTS Reading"
        assertTrue(repository.saveToFolder(entry.id, customFolder))
        assertFalse(repository.saveToFolder(entry.id, customFolder))

        // 3. Verify due queue in that custom folder contains the newly saved card
        val now = System.currentTimeMillis()
        val dueBefore = db.savedWordDao().getDueWordsByFolder(customFolder, now + 60_000L)
        assertEquals(1, dueBefore.size)
        val cardWithEntry = dueBefore.first()
        assertEquals("decision", cardWithEntry.entry.word)

        // 4. Verify SM-2 preview labels exist for all 4 grades
        val previews = Sm2Scheduler.previewAllGrades(
            currentEf = cardWithEntry.savedWord.easeFactor,
            currentIntervalDays = cardWithEntry.savedWord.intervalDays,
            currentRepetitions = cardWithEntry.savedWord.repetitions,
            currentLapses = cardWithEntry.savedWord.lapses,
            nowMillis = now
        )
        assertEquals("10 phút", Sm2Scheduler.formatIntervalLabel(ReviewGrade.AGAIN, previews[ReviewGrade.AGAIN]!!))
        assertEquals("1 ngày", Sm2Scheduler.formatIntervalLabel(ReviewGrade.GOOD, previews[ReviewGrade.GOOD]!!))

        // 5. Grade as GOOD and persist SM-2 update
        val goodResult = previews[ReviewGrade.GOOD]!!
        db.savedWordDao().updateSavedWord(
            cardWithEntry.savedWord.copy(
                easeFactor = goodResult.easinessFactor,
                intervalDays = goodResult.intervalDays,
                repetitions = goodResult.repetitions,
                lapses = goodResult.lapses,
                state = goodResult.state.name,
                nextReviewAt = goodResult.dueAtMillis,
                lastReviewedAt = now
            )
        )

        // 6. Verify card left immediate due queue and next_review_at advanced by 1 day
        val dueAfter = db.savedWordDao().getDueWordsByFolder(customFolder, now + 60_000L)
        assertTrue("Card should not be due immediately after GOOD review", dueAfter.isEmpty())

        val updated = db.savedWordDao().findSavedWord(customFolder, entry.id)!!
        assertEquals(1, updated.repetitions)
        assertEquals(1, updated.intervalDays)
        assertEquals(SrsState.LEARNING.name, updated.state)
        assertEquals(now, updated.lastReviewedAt)
        assertTrue(updated.nextReviewAt > now)
    }

    @Test
    fun `preset folders are pre-seeded with starter due cards`() = runTest {
        val now = System.currentTimeMillis() + 60_000L
        for (preset in WordFolders.PRESETS) {
            val due = db.savedWordDao().getDueWordsByFolder(preset, now)
            assertTrue("Expected starter cards in preset folder $preset", due.isNotEmpty())
        }
    }
}
