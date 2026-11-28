package com.enziidict.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.model.WordFolders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordFolderMigrationTest {

    @Test
    fun `migration from v1 to v2 preserves all saved words and creates word_folders`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_v1_to_v2.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        // 1. Create a genuine v1 SQLite database matching Room schema v1
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
                CREATE TABLE saved_words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    entry_id INTEGER NOT NULL,
                    folder_name TEXT NOT NULL,
                    ease_factor REAL NOT NULL,
                    interval_days INTEGER NOT NULL,
                    repetitions INTEGER NOT NULL,
                    lapses INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    next_review_at INTEGER NOT NULL,
                    last_reviewed_at INTEGER,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(entry_id) REFERENCES dictionary_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE UNIQUE INDEX idx_saved_words_unique_entry_folder ON saved_words (folder_name, entry_id)")
            sqlite.execSQL("CREATE INDEX idx_saved_words_due_queue ON saved_words (folder_name, next_review_at)")
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

            // Seed 2 dictionary entries + 2 v1 saved words (one default, one custom "Động vật")
            sqlite.execSQL(
                """
                INSERT INTO dictionary_entries (
                    id, word, word_normalized, ipa_uk, ipa_us, pos, meaning_vi,
                    examples_json, synonyms_json, collocations_json, cefr_level, frequency_rank
                ) VALUES
                    (1, 'animal', 'animal', '/ˈæn.ɪ.məl/', '/ˈæn.ɪ.məl/', 'noun', 'động vật', '[]', '[]', '[]', 'A1', 100),
                    (2, 'decision', 'decision', '/dɪˈsɪʒ.ən/', '/dɪˈsɪʒ.ən/', 'noun', 'quyết định', '[]', '[]', '[]', 'B1', 101)
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO saved_words (
                    id, entry_id, folder_name, ease_factor, interval_days, repetitions,
                    lapses, state, next_review_at, last_reviewed_at, created_at
                ) VALUES
                    (10, 1, 'Động vật', 2.6, 6, 2, 0, 'REVIEW', 1000, 900, 800),
                    (11, 2, 'Mặc định', 2.5, 1, 1, 0, 'LEARNING', 1000, 950, 850)
                """.trimIndent()
            )
            sqlite.version = 1
        }

        // 2. Open via Room v3 with MIGRATION_1_2 + MIGRATION_2_3 (validates full Room v3 schema)
        val migratedDb = Room.databaseBuilder(context, EnziiDatabase::class.java, dbName)
            .addMigrations(
                EnziiDatabaseMigrations.MIGRATION_1_2,
                EnziiDatabaseMigrations.MIGRATION_2_3,
                EnziiDatabaseMigrations.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

        try {
            val folders = migratedDb.wordFolderDao().getAllFolders()
            val folderNames = folders.map { it.name }.toSet()
            assertTrue(folderNames.containsAll(listOf("Mặc định", "Oxford 3000", "Từ lưu từ hội thoại", "Động vật")))

            val animalFolder = migratedDb.wordFolderDao().findByName("Động vật")
            assertNotNull(animalFolder)

            val savedInAnimals = migratedDb.savedWordDao().observeWordsByFolderId(animalFolder!!.id).first()
            assertEquals(1, savedInAnimals.size)
            assertEquals("animal", savedInAnimals.first().entry.word)
            assertEquals(2.6, savedInAnimals.first().savedWord.easeFactor, 0.001)
            assertEquals(6, savedInAnimals.first().savedWord.intervalDays)
            assertEquals(2, savedInAnimals.first().savedWord.repetitions)

            val allSaved = migratedDb.savedWordDao().observeAllSavedWords().first()
            assertEquals(2, allSaved.size)

            // 3. Verify repository folder operations & protection of default folder
            val repo = DictionaryRepository(
                dictionaryDao = migratedDb.dictionaryDao(),
                wordFolderDao = migratedDb.wordFolderDao(),
                savedWordDao = migratedDb.savedWordDao(),
                json = Json { ignoreUnknownKeys = true; isLenient = true }
            )

            val defaultFolder = migratedDb.wordFolderDao().findByName(WordFolders.DEFAULT)!!
            assertFalse("Default folder must not be deletable", repo.deleteFolder(defaultFolder.id))
            assertFalse("Default folder must not be renamable", repo.renameFolder(defaultFolder.id, "Khác"))

            val ieltsFolder = repo.createFolderAndSave(entryId = 2L, folderName = "IELTS")
            assertNotNull(ieltsFolder)
            val savedFoldersForDecision = repo.observeSavedFoldersForEntry(2L).first()
            assertEquals(setOf("Mặc định", "IELTS"), savedFoldersForDecision.map { it.name }.toSet())

            assertTrue(repo.renameFolder(ieltsFolder!!.id, "IELTS Speaking"))
            val renamed = migratedDb.wordFolderDao().findById(ieltsFolder.id)!!
            assertEquals("IELTS Speaking", renamed.name)
            val updatedSaved = migratedDb.savedWordDao().findSavedWordByFolderId(ieltsFolder.id, 2L)!!
            assertEquals("IELTS Speaking", updatedSaved.folderName)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }
}
