package com.enziidict.app.feature.shadowing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.data.local.EnziiDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShadowingChunkerTest {

    private lateinit var db: EnziiDatabase
    private lateinit var repository: DictionaryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.databaseBuilder(context, EnziiDatabase::class.java, "test_shadowing.db")
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
    fun `chunker splits long sentences on punctuation and clause boundaries without lonely trailing word`() {
        val sentence = "Before diving into details, let me clarify the context and outline our overall approach."
        val chunks = ShadowingChunker.splitIntoChunks(sentence)

        assertTrue("Expected at least 3 chunks, got $chunks", chunks.size >= 3)
        assertEquals("Before diving into details,", chunks.first())
        assertTrue("All words must be preserved", chunks.joinToString(" ") == sentence)
    }

    @Test
    fun `presets cover all 4 shadowing categories with chunks and Vietnamese meanings`() {
        for (cat in ShadowingCategory.entries) {
            val items = ShadowingPresets.ALL.filter { it.category == cat }
            assertTrue("Expected presets for category $cat", items.size >= 4)
            assertTrue(items.all { it.chunks.isNotEmpty() && it.meaningVi.isNotBlank() })
        }
    }

    @Test
    fun `saving a shadowing phrase persists it into Luyen Shadowing SRS folder`() = runTest {
        val folder = "Luyện Shadowing"
        val inserted = repository.savePhraseToFolder(
            word = "call it a day",
            pos = "shadowing",
            meaningVi = "kết thúc công việc trong ngày",
            folderName = folder
        )
        assertTrue(inserted)

        val due = db.savedWordDao().getDueWordsByFolder(folder, System.currentTimeMillis() + 60_000L)
        assertTrue(due.any { it.entry.word == "call it a day" })
    }
}
