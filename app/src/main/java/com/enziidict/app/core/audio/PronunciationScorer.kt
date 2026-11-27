package com.enziidict.app.core.audio

import kotlin.math.max
import kotlin.math.min

data class WordPronunciationMatch(
    val expectedWord: String,
    val isMatched: Boolean
)

data class PronunciationResult(
    val scorePercent: Int,
    val wordMatches: List<WordPronunciationMatch>
)

enum class WordMatchStatus {
    CORRECT,
    MISPRONOUNCED,
    MISSED
}

data class DetailedWordMatch(
    val displayWord: String,
    val normalizedWord: String,
    val status: WordMatchStatus,
    val heardWord: String? = null
)

data class DetailedPronunciationResult(
    val scorePercent: Int,
    val wordMatches: List<DetailedWordMatch>,
    val trickyWords: List<String>
)

object PronunciationScorer {

    fun evaluate(expectedSentence: String, spokenTranscript: String): PronunciationResult {
        val expectedTokens = tokenize(expectedSentence)
        val spokenTokens = tokenize(spokenTranscript)

        if (expectedTokens.isEmpty()) {
            return PronunciationResult(scorePercent = 0, wordMatches = emptyList())
        }

        val m = expectedTokens.size
        val n = spokenTokens.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (expectedTokens[i - 1] == spokenTokens[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    max(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val matchedIndices = BooleanArray(m)
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            when {
                expectedTokens[i - 1] == spokenTokens[j - 1] -> {
                    matchedIndices[i - 1] = true
                    i--
                    j--
                }
                dp[i - 1][j] >= dp[i][j - 1] -> i--
                else -> j--
            }
        }

        val matches = expectedTokens.mapIndexed { index, token ->
            WordPronunciationMatch(expectedWord = token, isMatched = matchedIndices[index])
        }
        val matchedCount = matches.count { it.isMatched }
        val percent = ((matchedCount.toDouble() / m.toDouble()) * 100.0).toInt().coerceIn(0, 100)

        return PronunciationResult(scorePercent = percent, wordMatches = matches)
    }

    /**
     * Aligns target sentence words against spoken transcript using Levenshtein-based sequence alignment
     * to distinguish CORRECT, MISPRONOUNCED (substituted/skewed), and MISSED (dropped) words.
     */
    fun evaluateDetailed(
        expectedSentence: String,
        spokenTranscript: String
    ): DetailedPronunciationResult {
        val rawWords = expectedSentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val expectedPairs = rawWords.mapNotNull { display ->
            val norm = display.lowercase().replace(Regex("[^a-z0-9']"), "").trim('\'')
            if (norm.isEmpty()) null else display to norm
        }
        val spokenTokens = tokenize(spokenTranscript)

        if (expectedPairs.isEmpty()) {
            return DetailedPronunciationResult(0, emptyList(), emptyList())
        }

        val m = expectedPairs.size
        val n = spokenTokens.size

        // Dynamic programming edit distance at word level
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (expectedPairs[i - 1].second == spokenTokens[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        val aligned = ArrayList<DetailedWordMatch>(m)
        var i = m
        var j = n
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && expectedPairs[i - 1].second == spokenTokens[j - 1] -> {
                    val (disp, norm) = expectedPairs[i - 1]
                    aligned += DetailedWordMatch(disp, norm, WordMatchStatus.CORRECT, spokenTokens[j - 1])
                    i--
                    j--
                }
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1 -> {
                    val (disp, norm) = expectedPairs[i - 1]
                    aligned += DetailedWordMatch(disp, norm, WordMatchStatus.MISPRONOUNCED, spokenTokens[j - 1])
                    i--
                    j--
                }
                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> {
                    val (disp, norm) = expectedPairs[i - 1]
                    aligned += DetailedWordMatch(disp, norm, WordMatchStatus.MISSED, null)
                    i--
                }
                else -> {
                    // Extra inserted spoken word — skip in target highlight
                    j--
                }
            }
        }
        aligned.reverse()

        // Score: full credit for CORRECT, 40% partial credit for close MISPRONOUNCED words
        var weightedScore = 0.0
        for (w in aligned) {
            when (w.status) {
                WordMatchStatus.CORRECT -> weightedScore += 1.0
                WordMatchStatus.MISPRONOUNCED -> {
                    val heard = w.heardWord.orEmpty()
                    val similarity = charSimilarity(w.normalizedWord, heard)
                    if (similarity >= 0.5) weightedScore += 0.4
                }
                WordMatchStatus.MISSED -> Unit
            }
        }

        val percent = ((weightedScore / m.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        val tricky = aligned
            .filter { it.status != WordMatchStatus.CORRECT }
            .map { it.normalizedWord }
            .distinct()

        return DetailedPronunciationResult(
            scorePercent = percent,
            wordMatches = aligned,
            trickyWords = tricky
        )
    }

    private fun charSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dist = charEditDistance(a, b)
        return 1.0 - (dist.toDouble() / max(a.length, b.length).toDouble())
    }

    private fun charEditDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    internal fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim('\'', ' ') }
            .filter { it.isNotEmpty() }
    }
}
