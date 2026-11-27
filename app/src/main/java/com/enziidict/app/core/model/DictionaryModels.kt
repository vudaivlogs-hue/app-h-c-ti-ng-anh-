package com.enziidict.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExampleItem(
    val en: String,
    val vi: String
)

@Serializable
data class CollocationItem(
    val phrase: String,
    @SerialName("meaning_vi") val meaningVi: String,
    val example: String = ""
)

data class DictionaryEntry(
    val id: Long,
    val word: String,
    val ipaUk: String,
    val ipaUs: String,
    val pos: String,
    val meaningVi: String,
    val examples: List<ExampleItem>,
    val synonyms: List<String>,
    val collocations: List<CollocationItem>,
    val cefrLevel: String?,
    val frequencyRank: Int
)
