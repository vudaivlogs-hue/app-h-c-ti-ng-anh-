package com.enziidict.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.enziidict.app.data.local.entity.BilingualArticle
import com.enziidict.app.data.local.entity.SpeakingMessage
import com.enziidict.app.data.local.entity.SpeakingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakingDao {
    @Query("SELECT * FROM speaking_sessions ORDER BY started_at DESC LIMIT :limit")
    fun observeSessions(limit: Int = 30): Flow<List<SpeakingSession>>

    @Query("SELECT * FROM speaking_messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<SpeakingMessage>

    @Insert
    suspend fun insertSession(session: SpeakingSession): Long

    @Query(
        """
        UPDATE speaking_sessions
        SET ended_at = :endedAt, duration_sec = :durationSec, correction_suggestions_json = :suggestionsJson
        WHERE id = :sessionId
        """
    )
    suspend fun completeSession(
        sessionId: Long,
        endedAt: Long,
        durationSec: Int,
        suggestionsJson: String?
    )

    @Query("SELECT * FROM speaking_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): SpeakingSession?

    @Query("UPDATE speaking_sessions SET correction_suggestions_json = :suggestionsJson WHERE id = :sessionId")
    suspend fun updateAnalysis(sessionId: Long, suggestionsJson: String)

    @Query("DELETE FROM speaking_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Insert
    suspend fun insertMessage(message: SpeakingMessage): Long
}

@Dao
interface ReadingDao {
    @Query("SELECT * FROM bilingual_articles ORDER BY cefr_level ASC, id ASC")
    fun observeAllArticles(): Flow<List<BilingualArticle>>

    @Query("SELECT * FROM bilingual_articles ORDER BY cefr_level ASC, id ASC")
    suspend fun getAllArticles(): List<BilingualArticle>

    @Query("UPDATE bilingual_articles SET read_progress = :progress, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, updatedAt: Long)
}
