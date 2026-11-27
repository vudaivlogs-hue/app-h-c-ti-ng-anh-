package com.enziidict.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_entries",
    indices = [
        Index(name = "idx_dict_word_prefix", value = ["word_normalized", "frequency_rank"]),
        Index(name = "idx_dict_exact_word_pos", value = ["word_normalized", "pos"]),
        Index(name = "idx_dict_cefr_pos", value = ["cefr_level", "pos", "frequency_rank"])
    ]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    @ColumnInfo(name = "word_normalized", collate = ColumnInfo.NOCASE)
    val wordNormalized: String,
    @ColumnInfo(name = "ipa_uk")
    val ipaUk: String,
    @ColumnInfo(name = "ipa_us")
    val ipaUs: String,
    val pos: String,
    @ColumnInfo(name = "meaning_vi")
    val meaningVi: String,
    @ColumnInfo(name = "examples_json")
    val examplesJson: String,
    @ColumnInfo(name = "synonyms_json")
    val synonymsJson: String,
    @ColumnInfo(name = "collocations_json")
    val collocationsJson: String,
    @ColumnInfo(name = "cefr_level")
    val cefrLevel: String?,
    @ColumnInfo(name = "frequency_rank")
    val frequencyRank: Int
)

@Fts4(
    contentEntity = DictionaryEntry::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"]
)
@Entity(tableName = "dictionary_fts")
data class DictionaryFts(
    val word: String,
    @ColumnInfo(name = "meaning_vi")
    val meaningVi: String,
    @ColumnInfo(name = "collocations_json")
    val collocationsJson: String,
    @ColumnInfo(name = "synonyms_json")
    val synonymsJson: String
)
