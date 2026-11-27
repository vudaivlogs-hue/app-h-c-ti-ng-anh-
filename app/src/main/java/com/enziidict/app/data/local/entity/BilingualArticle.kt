package com.enziidict.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bilingual_articles",
    indices = [Index(name = "idx_articles_level", value = ["cefr_level", "updated_at"])]
)
data class BilingualArticle(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "title_en")
    val titleEn: String,
    @ColumnInfo(name = "title_vi")
    val titleVi: String,
    @ColumnInfo(name = "cefr_level")
    val cefrLevel: String,
    val category: String,
    @ColumnInfo(name = "paragraphs_json")
    val paragraphsJson: String,
    @ColumnInfo(name = "read_progress")
    val readProgress: Float,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
