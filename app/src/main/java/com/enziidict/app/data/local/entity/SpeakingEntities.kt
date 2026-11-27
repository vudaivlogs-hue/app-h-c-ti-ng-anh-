package com.enziidict.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "speaking_sessions",
    indices = [Index(name = "idx_speaking_sessions_time", value = ["started_at"])]
)
data class SpeakingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topic: String,
    @ColumnInfo(name = "voice_name")
    val voiceName: String,
    val mode: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long?,
    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,
    @ColumnInfo(name = "correction_suggestions_json")
    val correctionSuggestionsJson: String?
)

@Entity(
    tableName = "speaking_messages",
    foreignKeys = [
        ForeignKey(
            entity = SpeakingSession::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(name = "idx_speaking_messages_session", value = ["session_id", "created_at"])]
)
data class SpeakingMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    val speaker: String,
    val transcript: String,
    @ColumnInfo(name = "suggestion_note")
    val suggestionNote: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
