package com.enziidict.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.enziidict.app.data.local.entity.DictionaryEntry
import com.enziidict.app.data.local.entity.SavedWord
import com.enziidict.app.data.local.entity.WordFolder
import kotlinx.coroutines.flow.Flow

data class SavedWordWithEntry(
    @Embedded val savedWord: SavedWord,
    @Embedded(prefix = "dict_") val entry: DictionaryEntry
)

data class FolderStats(
    @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "due_count") val dueCount: Int
)

@Dao
interface SavedWordDao {

    @Query("SELECT DISTINCT folder_name FROM saved_words ORDER BY folder_name ASC")
    fun observeFolders(): Flow<List<String>>

    @Query(
        """
        SELECT folder_name,
            COUNT(*) AS total_count,
            SUM(CASE WHEN next_review_at <= :nowMillis THEN 1 ELSE 0 END) AS due_count
        FROM saved_words
        GROUP BY folder_name
        ORDER BY folder_name ASC
        """
    )
    fun observeFolderStats(nowMillis: Long): Flow<List<FolderStats>>

    @Query("SELECT COUNT(*) FROM saved_words WHERE next_review_at <= :nowMillis")
    fun observeDueCount(nowMillis: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_words")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_words WHERE repetitions >= 3 AND interval_days >= 7")
    fun observeMasteredCount(): Flow<Int>

    @Query(
        """
        SELECT s.*,
            d.id AS dict_id,
            d.word AS dict_word,
            d.word_normalized AS dict_word_normalized,
            d.ipa_uk AS dict_ipa_uk,
            d.ipa_us AS dict_ipa_us,
            d.pos AS dict_pos,
            d.meaning_vi AS dict_meaning_vi,
            d.examples_json AS dict_examples_json,
            d.synonyms_json AS dict_synonyms_json,
            d.collocations_json AS dict_collocations_json,
            d.cefr_level AS dict_cefr_level,
            d.frequency_rank AS dict_frequency_rank
        FROM saved_words s
        INNER JOIN dictionary_entries d ON s.entry_id = d.id
        WHERE COALESCE(s.last_reviewed_at, s.created_at) >= :sinceMillis
        ORDER BY COALESCE(s.last_reviewed_at, s.created_at) DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyStudiedWords(sinceMillis: Long, limit: Int = 10): Flow<List<SavedWordWithEntry>>

    @Query(
        """
        SELECT f.*
        FROM word_folders f
        INNER JOIN saved_words s ON s.folder_id = f.id
        WHERE s.entry_id = :entryId
        ORDER BY CASE WHEN f.name = 'Mặc định' THEN 0 ELSE 1 END ASC, f.created_at ASC, f.id ASC
        """
    )
    fun observeSavedFoldersForEntry(entryId: Long): Flow<List<WordFolder>>

    @Query("SELECT * FROM saved_words WHERE entry_id = :entryId")
    suspend fun getSavedWordsForEntry(entryId: Long): List<SavedWord>

    @Query(
        """
        SELECT s.*,
            d.id AS dict_id,
            d.word AS dict_word,
            d.word_normalized AS dict_word_normalized,
            d.ipa_uk AS dict_ipa_uk,
            d.ipa_us AS dict_ipa_us,
            d.pos AS dict_pos,
            d.meaning_vi AS dict_meaning_vi,
            d.examples_json AS dict_examples_json,
            d.synonyms_json AS dict_synonyms_json,
            d.collocations_json AS dict_collocations_json,
            d.cefr_level AS dict_cefr_level,
            d.frequency_rank AS dict_frequency_rank
        FROM saved_words s
        INNER JOIN dictionary_entries d ON s.entry_id = d.id
        ORDER BY s.created_at DESC
        """
    )
    fun observeAllSavedWords(): Flow<List<SavedWordWithEntry>>

    @Query(
        """
        SELECT s.*,
            d.id AS dict_id,
            d.word AS dict_word,
            d.word_normalized AS dict_word_normalized,
            d.ipa_uk AS dict_ipa_uk,
            d.ipa_us AS dict_ipa_us,
            d.pos AS dict_pos,
            d.meaning_vi AS dict_meaning_vi,
            d.examples_json AS dict_examples_json,
            d.synonyms_json AS dict_synonyms_json,
            d.collocations_json AS dict_collocations_json,
            d.cefr_level AS dict_cefr_level,
            d.frequency_rank AS dict_frequency_rank
        FROM saved_words s
        INNER JOIN dictionary_entries d ON s.entry_id = d.id
        WHERE s.next_review_at <= :nowMillis
        ORDER BY s.next_review_at ASC
        LIMIT :limit
        """
    )
    suspend fun getDueWords(nowMillis: Long, limit: Int = 30): List<SavedWordWithEntry>

    @Query(
        """
        SELECT s.*,
            d.id AS dict_id,
            d.word AS dict_word,
            d.word_normalized AS dict_word_normalized,
            d.ipa_uk AS dict_ipa_uk,
            d.ipa_us AS dict_ipa_us,
            d.pos AS dict_pos,
            d.meaning_vi AS dict_meaning_vi,
            d.examples_json AS dict_examples_json,
            d.synonyms_json AS dict_synonyms_json,
            d.collocations_json AS dict_collocations_json,
            d.cefr_level AS dict_cefr_level,
            d.frequency_rank AS dict_frequency_rank
        FROM saved_words s
        INNER JOIN dictionary_entries d ON s.entry_id = d.id
        WHERE s.folder_id = :folderId AND s.next_review_at <= :nowMillis
        ORDER BY s.next_review_at ASC
        LIMIT :limit
        """
    )
    suspend fun getDueWordsByFolderId(
        folderId: Long,
        nowMillis: Long,
        limit: Int = 30
    ): List<SavedWordWithEntry>

    @Query(
        """
        SELECT s.*,
            d.id AS dict_id,
            d.word AS dict_word,
            d.word_normalized AS dict_word_normalized,
            d.ipa_uk AS dict_ipa_uk,
            d.ipa_us AS dict_ipa_us,
            d.pos AS dict_pos,
            d.meaning_vi AS dict_meaning_vi,
            d.examples_json AS dict_examples_json,
            d.synonyms_json AS dict_synonyms_json,
            d.collocations_json AS dict_collocations_json,
            d.cefr_level AS dict_cefr_level,
            d.frequency_rank AS dict_frequency_rank
        FROM saved_words s
        INNER JOIN dictionary_entries d ON s.entry_id = d.id
        WHERE s.folder_name = :folderName AND s.next_review_at <= :nowMillis
        ORDER BY s.next_review_at ASC
        LIMIT :limit
        """
    )
    suspend fun getDueWordsByFolder(
        folderName: String,
        nowMillis: Long,
        limit: Int = 30
    ): List<SavedWordWithEntry>

    @Query(
        """
        SELECT s.*,
            d.id AS dict_id,
            d.word AS dict_word,
            d.word_normalized AS dict_word_normalized,
            d.ipa_uk AS dict_ipa_uk,
            d.ipa_us AS dict_ipa_us,
            d.pos AS dict_pos,
            d.meaning_vi AS dict_meaning_vi,
            d.examples_json AS dict_examples_json,
            d.synonyms_json AS dict_synonyms_json,
            d.collocations_json AS dict_collocations_json,
            d.cefr_level AS dict_cefr_level,
            d.frequency_rank AS dict_frequency_rank
        FROM saved_words s
        INNER JOIN dictionary_entries d ON s.entry_id = d.id
        WHERE s.folder_id = :folderId
        ORDER BY s.created_at DESC
        """
    )
    fun observeWordsByFolderId(folderId: Long): Flow<List<SavedWordWithEntry>>

    @Query(
        """
        SELECT s.*,
            d.id AS dict_id,
            d.word AS dict_word,
            d.word_normalized AS dict_word_normalized,
            d.ipa_uk AS dict_ipa_uk,
            d.ipa_us AS dict_ipa_us,
            d.pos AS dict_pos,
            d.meaning_vi AS dict_meaning_vi,
            d.examples_json AS dict_examples_json,
            d.synonyms_json AS dict_synonyms_json,
            d.collocations_json AS dict_collocations_json,
            d.cefr_level AS dict_cefr_level,
            d.frequency_rank AS dict_frequency_rank
        FROM saved_words s
        INNER JOIN dictionary_entries d ON s.entry_id = d.id
        WHERE s.folder_name = :folderName
        ORDER BY s.created_at DESC
        """
    )
    fun observeWordsByFolder(folderName: String): Flow<List<SavedWordWithEntry>>

    @Query("SELECT * FROM saved_words WHERE folder_id = :folderId AND entry_id = :entryId LIMIT 1")
    suspend fun findSavedWordByFolderId(folderId: Long, entryId: Long): SavedWord?

    @Query("SELECT * FROM saved_words WHERE folder_name = :folderName AND entry_id = :entryId LIMIT 1")
    suspend fun findSavedWord(folderName: String, entryId: Long): SavedWord?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSavedWord(savedWord: SavedWord): Long

    @Update
    suspend fun updateSavedWord(savedWord: SavedWord)

    @Query("UPDATE saved_words SET folder_name = :newName WHERE folder_id = :folderId")
    suspend fun updateFolderNameForFolderId(folderId: Long, newName: String)

    @Query("DELETE FROM saved_words WHERE id = :id")
    suspend fun deleteSavedWord(id: Long)

    @Query("DELETE FROM saved_words WHERE entry_id = :entryId AND folder_id = :folderId")
    suspend fun deleteSavedWordByEntryAndFolder(entryId: Long, folderId: Long)

    @Query("DELETE FROM saved_words WHERE entry_id = :entryId")
    suspend fun deleteAllForEntry(entryId: Long)

    @Query("DELETE FROM saved_words WHERE folder_name = :folderName")
    suspend fun deleteFolderWords(folderName: String)
}
