package com.enziidict.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_words",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntry::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordFolder::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_saved_words_unique_entry_folder_id", value = ["folder_id", "entry_id"], unique = true),
        Index(name = "idx_saved_words_due_queue", value = ["folder_id", "next_review_at"]),
        Index(name = "idx_saved_words_entry_id", value = ["entry_id"])
    ]
)
data class SavedWord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "entry_id")
    val entryId: Long,
    @ColumnInfo(name = "folder_id")
    val folderId: Long,
    @ColumnInfo(name = "folder_name")
    val folderName: String,
    @ColumnInfo(name = "ease_factor")
    val easeFactor: Double,
    @ColumnInfo(name = "interval_days")
    val intervalDays: Int,
    val repetitions: Int,
    val lapses: Int,
    val state: String,
    @ColumnInfo(name = "next_review_at")
    val nextReviewAt: Long,
    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
