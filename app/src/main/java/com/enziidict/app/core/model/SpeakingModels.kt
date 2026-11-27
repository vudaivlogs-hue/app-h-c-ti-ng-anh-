package com.enziidict.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SpeakingTopic(
    val code: String,
    val titleVi: String,
    val promptInstruction: String
) {
    DAILY(
        code = "DAILY",
        titleVi = "Giao tiếp hằng ngày",
        promptInstruction = "Have a natural, warm everyday English conversation about daily life, hobbies, routines, or weekend plans."
    ),
    CAFE(
        code = "CAFE",
        titleVi = "Quán cà phê",
        promptInstruction = "Roleplay as a friendly barista or fellow customer at a cozy coffee shop: ordering drinks, customizing orders, and making light small talk."
    ),
    INTERVIEW(
        code = "INTERVIEW",
        titleVi = "Phỏng vấn xin việc",
        promptInstruction = "Roleplay as a supportive English job interviewer asking clear background, strengths, and situational questions with concise follow-ups."
    ),
    TRAVEL(
        code = "TRAVEL",
        titleVi = "Du lịch",
        promptInstruction = "Roleplay realistic travel scenarios such as airport check-in, hotel reservations, asking for directions, or exploring local attractions."
    ),
    FREE(
        code = "FREE",
        titleVi = "Tự do",
        promptInstruction = "Act as an encouraging English conversation partner open to discussing any topic the learner brings up."
    )
}

enum class SpeakingLevel(
    val code: String,
    val titleVi: String,
    val cefrHint: String,
    val instruction: String
) {
    BEGINNER(
        code = "BEGINNER",
        titleVi = "Cơ bản",
        cefrHint = "A1–A2",
        instruction = "Speak slowly and clearly using simple everyday vocabulary and short 1–2 sentence turns."
    ),
    INTERMEDIATE(
        code = "INTERMEDIATE",
        titleVi = "Trung cấp",
        cefrHint = "B1–B2",
        instruction = "Speak at a natural moderate pace using common idioms and phrasal verbs in 2–3 sentence turns."
    ),
    ADVANCED(
        code = "ADVANCED",
        titleVi = "Nâng cao",
        cefrHint = "C1",
        instruction = "Speak naturally like a native speaker with nuanced vocabulary, follow-up probes, and engaging discussion."
    )
}

enum class AiVoice(val voiceName: String, val descriptionVi: String) {
    AOEDE("Aoede", "Nữ — ấm áp, tự nhiên"),
    PUCK("Puck", "Nam — năng động, rõ ràng"),
    CHARON("Charon", "Nam — trầm, điềm tĩnh"),
    KORE("Kore", "Nữ — nhẹ nhàng, chuẩn xác"),
    FENRIR("Fenrir", "Nam — dứt khoát, chuyên nghiệp")
}

enum class SpeakingMode {
    GEMINI_LIVE,
    FALLBACK_STT_TTS
}

@Serializable
data class SpeakingMistake(
    val original: String,
    val correction: String,
    @SerialName("explanation_vi") val explanationVi: String
)

@Serializable
data class SuggestedVocab(
    val word: String,
    val pos: String = "noun",
    @SerialName("meaning_vi") val meaningVi: String
)

@Serializable
data class SpeakingAnalysis(
    @SerialName("score_out_of_10") val scoreOutOf10: Int = 7,
    @SerialName("overall_feedback_vi") val overallFeedbackVi: String,
    val mistakes: List<SpeakingMistake> = emptyList(),
    @SerialName("suggested_vocabulary") val suggestedVocabulary: List<SuggestedVocab> = emptyList()
)
