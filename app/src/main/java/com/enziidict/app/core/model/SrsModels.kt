package com.enziidict.app.core.model

enum class ReviewGrade(val quality: Int, val labelEn: String, val labelVi: String) {
    AGAIN(1, "Again", "Quên"),
    HARD(3, "Hard", "Khó"),
    GOOD(4, "Good", "Nhớ"),
    EASY(5, "Easy", "Dễ")
}

enum class SrsState {
    NEW,
    LEARNING,
    REVIEW,
    RELEARNING
}

data class Sm2Result(
    val easinessFactor: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val lapses: Int,
    val state: SrsState,
    val dueAtMillis: Long
)

object WordFolders {
    const val DEFAULT = "Mặc định"
    const val OXFORD_3000 = "Oxford 3000"
    const val CONVERSATION = "Từ lưu từ hội thoại"

    val PRESETS: List<String> = listOf(DEFAULT, OXFORD_3000, CONVERSATION)
}
