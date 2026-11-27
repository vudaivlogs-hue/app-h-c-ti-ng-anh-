package com.enziidict.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GrammarPoint(
    val structure: String,
    val explanationVi: String
)

@Serializable
data class GrammarAnalysis(
    val translatedText: String,
    val tenseOrPattern: String = "",
    val points: List<GrammarPoint> = emptyList(),
    val keyVocabulary: List<String> = emptyList()
)

@Serializable
data class BilingualParagraph(
    val en: String,
    val vi: String
)
