package com.enziidict.app.feature.shadowing

enum class ShadowingCategory(val labelVi: String) {
    DAILY("Daily"),
    IDIOMS("Idioms"),
    PHRASAL_VERBS("Phrasal Verbs"),
    INTERVIEW("Interview")
}

enum class ShadowingSpeed(val rate: Float, val label: String, val hintVi: String) {
    SLOW(0.75f, "0.75x", "Chậm — nghe rõ nối âm"),
    NORMAL(1.0f, "1.0x", "Tự nhiên"),
    FAST(1.15f, "1.15x", "Nhanh — phản xạ nhịp")
}

data class ShadowingItem(
    val id: String,
    val sentenceEn: String,
    val meaningVi: String,
    val ipa: String = "",
    val chunks: List<String> = ShadowingChunker.splitIntoChunks(sentenceEn),
    val category: ShadowingCategory = ShadowingCategory.DAILY,
    val sourceLabel: String = "Luyện nhanh",
    val keywordForSrs: String = ""
)

object ShadowingChunker {

    private val CLAUSE_BREAK_WORDS = setOf(
        "and", "but", "so", "because", "although", "while", "when", "if",
        "before", "after", "that", "which", "who", "with", "without", "for", "to", "into"
    )

    /**
     * Splits an English sentence into natural spoken rhythm chunks (2–5 words each)
     * so learners can easily shadow stress, linking, and micro-pauses.
     */
    fun splitIntoChunks(sentence: String): List<String> {
        val cleaned = sentence.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return emptyList()

        // First split on strong punctuation boundaries while preserving the punctuation mark
        val primarySegments = cleaned.split(Regex("(?<=[,;:.!?—])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<String>()
        for (segment in primarySegments) {
            val words = segment.split(" ")
            if (words.size <= 5) {
                result += segment
                continue
            }

            val current = mutableListOf<String>()
            for (word in words) {
                val lower = word.lowercase().trim(',', ';', '.', '!', '?')
                val shouldBreakBefore = current.size >= 3 && (lower in CLAUSE_BREAK_WORDS || current.size >= 5)
                if (shouldBreakBefore) {
                    result += current.joinToString(" ")
                    current.clear()
                }
                current += word
            }
            if (current.isNotEmpty()) {
                // Avoid leaving a lonely 1-word trailing chunk
                if (current.size == 1 && result.isNotEmpty()) {
                    val last = result.removeAt(result.lastIndex)
                    result += "$last ${current.first()}"
                } else {
                    result += current.joinToString(" ")
                }
            }
        }
        return result
    }
}

object ShadowingPresets {

    val ALL: List<ShadowingItem> = listOf(
        // DAILY
        ShadowingItem(
            id = "daily_1",
            sentenceEn = "Could I grab an iced latte with oat milk, please?",
            meaningVi = "Cho tôi gọi một ly latte đá sữa yến mạch được không?",
            ipa = "/kʊd aɪ ɡræb ən aɪst ˈlɑː.teɪ wɪð əʊt mɪlk pliːz/",
            chunks = listOf("Could I grab", "an iced latte", "with oat milk,", "please?"),
            category = ShadowingCategory.DAILY,
            sourceLabel = "Daily",
            keywordForSrs = "grab a coffee"
        ),
        ShadowingItem(
            id = "daily_2",
            sentenceEn = "I didn't quite catch that — could you say it a bit slower?",
            meaningVi = "Tôi chưa nghe rõ lắm — bạn nói chậm lại một chút được không?",
            ipa = "/aɪ ˈdɪd.ənt kwaɪt kætʃ ðæt/",
            chunks = listOf("I didn't quite catch that —", "could you say it", "a bit slower?"),
            category = ShadowingCategory.DAILY,
            sourceLabel = "Daily",
            keywordForSrs = "catch that"
        ),
        ShadowingItem(
            id = "daily_3",
            sentenceEn = "Let's touch base tomorrow morning before the meeting starts.",
            meaningVi = "Hãy trao đổi nhanh vào sáng mai trước khi cuộc họp bắt đầu nhé.",
            ipa = "/lets tʌtʃ beɪs təˈmɒr.əʊ ˈmɔː.nɪŋ/",
            chunks = listOf("Let's touch base", "tomorrow morning", "before the meeting starts."),
            category = ShadowingCategory.DAILY,
            sourceLabel = "Daily",
            keywordForSrs = "touch base"
        ),
        ShadowingItem(
            id = "daily_4",
            sentenceEn = "Even ten minutes of daily practice makes a huge difference.",
            meaningVi = "Ngay cả mười phút luyện tập mỗi ngày cũng tạo ra khác biệt lớn.",
            ipa = "/ˈiː.vən ten ˈmɪn.ɪts əv ˈdeɪ.li ˈpræk.tɪs/",
            chunks = listOf("Even ten minutes", "of daily practice", "makes a huge difference."),
            category = ShadowingCategory.DAILY,
            sourceLabel = "Daily",
            keywordForSrs = "make a difference"
        ),

        // IDIOMS
        ShadowingItem(
            id = "idiom_1",
            sentenceEn = "Let's call it a day and pick this up fresh tomorrow.",
            meaningVi = "Hôm nay nghỉ ở đây thôi rồi mai đầu óc tỉnh táo làm tiếp nhé.",
            ipa = "/lets kɔːl ɪt ə deɪ/",
            chunks = listOf("Let's call it a day", "and pick this up", "fresh tomorrow."),
            category = ShadowingCategory.IDIOMS,
            sourceLabel = "Idioms",
            keywordForSrs = "call it a day"
        ),
        ShadowingItem(
            id = "idiom_2",
            sentenceEn = "Don't worry, learning a new language is not rocket science.",
            meaningVi = "Đừng lo, học một ngôn ngữ mới đâu phải chuyện quá cao siêu.",
            ipa = "/ˈrɒk.ɪt ˈsaɪ.əns/",
            chunks = listOf("Don't worry,", "learning a new language", "is not rocket science."),
            category = ShadowingCategory.IDIOMS,
            sourceLabel = "Idioms",
            keywordForSrs = "not rocket science"
        ),
        ShadowingItem(
            id = "idiom_3",
            sentenceEn = "We're all on the same page about the launch schedule.",
            meaningVi = "Tất cả chúng ta đều thống nhất quan điểm về lịch ra mắt.",
            ipa = "/ɒn ðə seɪm peɪdʒ/",
            chunks = listOf("We're all", "on the same page", "about the launch schedule."),
            category = ShadowingCategory.IDIOMS,
            sourceLabel = "Idioms",
            keywordForSrs = "on the same page"
        ),
        ShadowingItem(
            id = "idiom_4",
            sentenceEn = "Once in a blue moon, I treat myself to a quiet weekend getaway.",
            meaningVi = "Thỉnh thoảng hiếm hoi lắm, tôi tự thưởng cho mình một chuyến nghỉ cuối tuần yên tĩnh.",
            ipa = "/wʌns ɪn ə bluː muːn/",
            chunks = listOf("Once in a blue moon,", "I treat myself", "to a quiet weekend getaway."),
            category = ShadowingCategory.IDIOMS,
            sourceLabel = "Idioms",
            keywordForSrs = "once in a blue moon"
        ),

        // PHRASAL VERBS
        ShadowingItem(
            id = "pv_1",
            sentenceEn = "The flight is about to take off, so please fasten your seatbelt.",
            meaningVi = "Chuyến bay sắp cất cánh, vui lòng thắt dây an toàn.",
            ipa = "/teɪk ɒf/ • /teɪk ɔːf/",
            chunks = listOf("The flight is about", "to take off,", "so please fasten", "your seatbelt."),
            category = ShadowingCategory.PHRASAL_VERBS,
            sourceLabel = "Phrasal Verbs",
            keywordForSrs = "take off"
        ),
        ShadowingItem(
            id = "pv_2",
            sentenceEn = "We need to figure out why the app slows down on older phones.",
            meaningVi = "Chúng ta cần tìm ra lý do ứng dụng chạy chậm trên điện thoại đời cũ.",
            ipa = "/ˈfɪɡ.ər aʊt/",
            chunks = listOf("We need to figure out", "why the app slows down", "on older phones."),
            category = ShadowingCategory.PHRASAL_VERBS,
            sourceLabel = "Phrasal Verbs",
            keywordForSrs = "figure out"
        ),
        ShadowingItem(
            id = "pv_3",
            sentenceEn = "I'm really looking forward to working with your engineering team.",
            meaningVi = "Tôi thực sự rất mong được làm việc cùng đội ngũ kỹ sư của bạn.",
            ipa = "/ˈlʊk.ɪŋ ˈfɔː.wəd tuː/",
            chunks = listOf("I'm really looking forward", "to working with", "your engineering team."),
            category = ShadowingCategory.PHRASAL_VERBS,
            sourceLabel = "Phrasal Verbs",
            keywordForSrs = "look forward to"
        ),
        ShadowingItem(
            id = "pv_4",
            sentenceEn = "She came up with a practical workflow that cut review time in half.",
            meaningVi = "Cô ấy nghĩ ra một quy trình thực tế giúp giảm một nửa thời gian rà soát.",
            ipa = "/keɪm ʌp wɪð/",
            chunks = listOf("She came up with", "a practical workflow", "that cut review time", "in half."),
            category = ShadowingCategory.PHRASAL_VERBS,
            sourceLabel = "Phrasal Verbs",
            keywordForSrs = "come up with"
        ),

        // INTERVIEW
        ShadowingItem(
            id = "iv_1",
            sentenceEn = "Before diving into details, let me clarify the context and our goal.",
            meaningVi = "Trước khi đi sâu vào chi tiết, cho phép tôi làm rõ bối cảnh và mục tiêu của chúng tôi.",
            ipa = "/ˈklær.ɪ.faɪ ðə ˈkɒn.tekst/",
            chunks = listOf("Before diving into details,", "let me clarify the context", "and our goal."),
            category = ShadowingCategory.INTERVIEW,
            sourceLabel = "Interview",
            keywordForSrs = "clarify the context"
        ),
        ShadowingItem(
            id = "iv_2",
            sentenceEn = "My biggest strength is breaking complex problems into clear, testable steps.",
            meaningVi = "Điểm mạnh lớn nhất của tôi là chia nhỏ vấn đề phức tạp thành các bước rõ ràng, kiểm chứng được.",
            ipa = "/ˈbreɪ.kɪŋ ˈkɒm.pleks ˈprɒb.ləmz/",
            chunks = listOf("My biggest strength", "is breaking complex problems", "into clear, testable steps."),
            category = ShadowingCategory.INTERVIEW,
            sourceLabel = "Interview",
            keywordForSrs = "break down a problem"
        ),
        ShadowingItem(
            id = "iv_3",
            sentenceEn = "When priorities shift unexpectedly, I align with stakeholders right away.",
            meaningVi = "Khi mức độ ưu tiên thay đổi bất ngờ, tôi thống nhất ngay với các bên liên quan.",
            ipa = "/əˈlaɪn wɪð ˈsteɪkˌhəʊl.dəz/",
            chunks = listOf("When priorities shift", "unexpectedly,", "I align with stakeholders", "right away."),
            category = ShadowingCategory.INTERVIEW,
            sourceLabel = "Interview",
            keywordForSrs = "align with stakeholders"
        ),
        ShadowingItem(
            id = "iv_4",
            sentenceEn = "I'd love to learn more about how your team measures product impact.",
            meaningVi = "Tôi rất muốn tìm hiểu thêm về cách nhóm bạn đo lường tác động sản phẩm.",
            ipa = "/ˈmeʒ.əz ˈprɒd.ʌkt ˈɪm.pækt/",
            chunks = listOf("I'd love to learn more", "about how your team", "measures product impact."),
            category = ShadowingCategory.INTERVIEW,
            sourceLabel = "Interview",
            keywordForSrs = "measurable impact"
        )
    )
}
