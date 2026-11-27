package com.enziidict.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationScorerTest {

    @Test
    fun `exact sentence ignores punctuation and casing`() {
        val result = PronunciationScorer.evaluate(
            expectedSentence = "An apple a day, keeps the doctor away!",
            spokenTranscript = "an apple a day keeps the doctor away"
        )

        assertEquals(100, result.scorePercent)
        assertTrue(result.wordMatches.all { it.isMatched })
    }

    @Test
    fun `partial match flags missing or mismatched words`() {
        val result = PronunciationScorer.evaluate(
            expectedSentence = "We need to make a decision",
            spokenTranscript = "We need make decision"
        )

        assertEquals(66, result.scorePercent)
        assertFalse(result.wordMatches.first { it.expectedWord == "to" }.isMatched)
        assertFalse(result.wordMatches.first { it.expectedWord == "a" }.isMatched)
        assertTrue(result.wordMatches.first { it.expectedWord == "decision" }.isMatched)
    }
}
