package com.enziidict.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.enziidict.app.data.local.entity.DictionaryEntry

@Dao
interface DictionaryDao {

    @Query(
        """
        SELECT * FROM dictionary_entries
        WHERE word_normalized >= :prefix AND word_normalized < :prefixUpper
        ORDER BY CASE WHEN word_normalized = :prefix THEN 0 ELSE 1 END, frequency_rank ASC
        LIMIT :limit
        """
    )
    suspend fun searchPrefix(
        prefix: String,
        prefixUpper: String,
        limit: Int = 30
    ): List<DictionaryEntry>

    @Query(
        """
        SELECT * FROM dictionary_entries
        WHERE word_normalized = :wordNormalized
        ORDER BY frequency_rank ASC
        """
    )
    suspend fun findExact(wordNormalized: String): List<DictionaryEntry>

    @Query(
        """
        SELECT d.* FROM dictionary_entries d
        INNER JOIN dictionary_fts fts ON d.id = fts.rowid
        WHERE dictionary_fts MATCH :ftsQuery
        ORDER BY d.frequency_rank ASC
        LIMIT :limit
        """
    )
    suspend fun searchFullText(
        ftsQuery: String,
        limit: Int = 30
    ): List<DictionaryEntry>

    @Query(
        """
        SELECT * FROM dictionary_entries
        WHERE meaning_vi LIKE '%' || :query || '%'
        ORDER BY frequency_rank ASC
        LIMIT :limit
        """
    )
    suspend fun searchByMeaning(
        query: String,
        limit: Int = 30
    ): List<DictionaryEntry>

    @Query("SELECT * FROM dictionary_entries ORDER BY frequency_rank ASC LIMIT :limit")
    suspend fun getTopPopularWords(limit: Int = 25): List<DictionaryEntry>

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun countEntries(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: DictionaryEntry): Long

    @Update
    suspend fun updateEntry(entry: DictionaryEntry)
}
