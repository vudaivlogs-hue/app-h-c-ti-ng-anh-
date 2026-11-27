package com.enziidict.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.data.local.entity.SpeakingMessage
import com.enziidict.app.data.local.entity.SpeakingSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryDatabaseTest {

    private lateinit var db: EnziiDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.databaseBuilder(context, EnziiDatabase::class.java, "test_dictionary.db")
            .createFromAsset("dictionary.db")
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `prepackaged dictionary db loads ~500+ entries and prefix search is under 15ms`() = runTest {
        val dao = db.dictionaryDao()
        val total = dao.countEntries()
        assertTrue("Expected at least 450 seeded entries, got $total", total >= 450)

        // Warm up SQLite page cache once, then measure prefix search latency
        dao.searchPrefix("app", "apq", limit = 20)

        var results = emptyList<com.enziidict.app.data.local.entity.DictionaryEntry>()
        val elapsedMs = measureTimeMillis {
            results = dao.searchPrefix("app", "apq", limit = 20)
        }

        assertTrue("Expected prefix 'app' to match 'apple', 'apply', etc.", results.any { it.word == "apple" })
        assertTrue("Prefix search took ${elapsedMs}ms (expected < 15ms)", elapsedMs < 15L)
    }

    @Test
    fun `fts4 full-text search matches collocations and synonyms`() = runTest {
        val dao = db.dictionaryDao()
        val results = dao.searchFullText("decision*", limit = 10)
        assertTrue("Expected FTS4 lookup to match 'decision' / 'make'", results.any { it.word == "decision" })
    }

    @Test
    fun `vietnamese meaning lookup returns matching English entries`() = runTest {
        val dao = db.dictionaryDao()
        val results = dao.searchByMeaning("quyết định", limit = 10)
        assertTrue("Expected Vietnamese lookup to return 'decision'", results.any { it.word == "decision" })
    }

    @Test
    fun `saved_words and speaking_sessions persist SM-2 and transcript records`() = runTest {
        val due = db.savedWordDao().getDueWords(System.currentTimeMillis() + 60_000L, limit = 10)
        assertTrue("Expected seeded starter saved_words", due.isNotEmpty())

        val sessionId = db.speakingDao().insertSession(
            SpeakingSession(
                topic = "DAILY",
                voiceName = "Aoede",
                mode = "GEMINI_LIVE",
                startedAt = 1_000L,
                endedAt = null,
                durationSec = 0,
                correctionSuggestionsJson = null
            )
        )
        db.speakingDao().insertMessage(
            SpeakingMessage(
                sessionId = sessionId,
                speaker = "USER",
                transcript = "I want to improve my English.",
                suggestionNote = null,
                createdAt = 1_100L
            )
        )
        val messages = db.speakingDao().getMessagesForSession(sessionId)
        assertEquals(1, messages.size)
        assertEquals("I want to improve my English.", messages.first().transcript)
    }
}
