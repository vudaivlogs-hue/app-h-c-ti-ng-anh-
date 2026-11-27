package com.enziidict.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enziidict.app.data.local.entity.WordFolder
import kotlinx.coroutines.flow.Flow

data class WordFolderWithCount(
    @Embedded val folder: WordFolder,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "due_count") val dueCount: Int
)

@Dao
interface WordFolderDao {

    @Query(
        """
        SELECT f.*,
            COUNT(s.id) AS word_count,
            COALESCE(SUM(CASE WHEN s.id IS NOT NULL AND s.next_review_at <= :nowMillis THEN 1 ELSE 0 END), 0) AS due_count
        FROM word_folders f
        LEFT JOIN saved_words s ON s.folder_id = f.id
        GROUP BY f.id
        ORDER BY CASE WHEN f.name = 'Mặc định' THEN 0 ELSE 1 END ASC, f.created_at ASC, f.id ASC
        """
    )
    fun observeFoldersWithCounts(nowMillis: Long): Flow<List<WordFolderWithCount>>

    @Query("SELECT * FROM word_folders ORDER BY CASE WHEN name = 'Mặc định' THEN 0 ELSE 1 END ASC, created_at ASC, id ASC")
    fun observeAllFolders(): Flow<List<WordFolder>>

    @Query("SELECT * FROM word_folders ORDER BY CASE WHEN name = 'Mặc định' THEN 0 ELSE 1 END ASC, created_at ASC, id ASC")
    suspend fun getAllFolders(): List<WordFolder>

    @Query("SELECT * FROM word_folders WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): WordFolder?

    @Query("SELECT * FROM word_folders WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): WordFolder?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: WordFolder): Long

    @Query("UPDATE word_folders SET name = :newName WHERE id = :id")
    suspend fun renameFolder(id: Long, newName: String)

    @Query("DELETE FROM word_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)
}
