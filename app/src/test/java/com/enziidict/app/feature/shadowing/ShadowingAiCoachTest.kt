package com.enziidict.app.feature.shadowing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.audio.PronunciationScorer
import com.enziidict.app.core.audio.WordMatchStatus
import com.enziidict.app.core.network.GeminiModelPresets
import com.enziidict.app.core.network.GeminiShadowingCoachService
import com.enziidict.app.core.network.HttpPostResponse
import com.enziidict.app.core.network.ShadowingAiOutcome
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShadowingAiCoachTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `detailed pronunciation scorer classifies correct, mispronounced, and missed words`() {
        val result = PronunciationScorer.evaluateDetailed(
            expectedSentence = "Let's call it a day tomorrow.",
            spokenTranscript = "Let's cool it tomorrow"
        )

        val byWord = result.wordMatches.associateBy { it.normalizedWord }
        assertEquals(WordMatchStatus.CORRECT, byWord["let's"]?.status)
        assertEquals(WordMatchStatus.MISPRONOUNCED, byWord["call"]?.status)
        assertEquals(WordMatchStatus.CORRECT, byWord["it"]?.status)
        assertEquals(WordMatchStatus.MISSED, byWord["a"]?.status)
        assertEquals(WordMatchStatus.MISSED, byWord["day"]?.status)
        assertEquals(WordMatchStatus.CORRECT, byWord["tomorrow"]?.status)
        assertTrue(result.trickyWords.containsAll(listOf("call", "a", "day")))
    }

    @Test
    fun `gemini shadowing coach falls back offline when API key missing or network unavailable`() = runTest {
        val noKeyCoach = GeminiShadowingCoachService(json) { _, _ -> HttpPostResponse(200, "{}") }
        val noKeyOutcome = noKeyCoach.evaluateShadowing(
            targetSentence = "Let's call it a day.",
            spokenTranscript = "Let's call it a day",
            audioFile = null,
            apiKey = "",
            model = GeminiModelPresets.FLASH_25
        )
        assertTrue(noKeyOutcome is ShadowingAiOutcome.OfflineFallback)

        val offlineCoach = GeminiShadowingCoachService(json) { _, _ ->
            throw UnknownHostException("offline")
        }
        val offlineOutcome = offlineCoach.evaluateShadowing(
            targetSentence = "Let's call it a day.",
            spokenTranscript = "Let's call it a day",
            audioFile = null,
            apiKey = "AIzaTestKey",
            model = GeminiModelPresets.FLASH_25
        )
        assertTrue(offlineOutcome is ShadowingAiOutcome.OfflineFallback)
    }

    @Test
    fun `gemini shadowing coach parses structured fluency score, stress notes, and linking tips`() = runTest {
        val innerJson = """
            {
              "recognized_transcript": "Let's call it a day",
              "fluency_score": 88,
              "summary_vi": "Nhịp nói khá tự nhiên và rõ ràng.",
              "stress_notes": ["Nhấn rõ hơn vào từ 'call' và 'day'."],
              "linking_and_rhythm_tips": ["Nối âm 'call it a' thành /kɔː-lɪ-tə/ để câu trôi chảy hơn."]
            }
        """.trimIndent()
        val envelope = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": ${json.encodeToString(kotlinx.serialization.serializer<String>(), innerJson)} }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val coach = GeminiShadowingCoachService(json) { _, _ -> HttpPostResponse(200, envelope) }
        val outcome = coach.evaluateShadowing(
            targetSentence = "Let's call it a day.",
            spokenTranscript = "Let's call it a day",
            audioFile = null,
            apiKey = "AIzaValid",
            model = GeminiModelPresets.FLASH_25
        )

        assertTrue(outcome is ShadowingAiOutcome.Success)
        val fb = (outcome as ShadowingAiOutcome.Success).feedback
        assertEquals(88, fb.fluencyScore)
        assertEquals(1, fb.stressNotes.size)
        assertTrue(fb.linkingAndRhythmTips.first().contains("call it a"))
    }

    @Test
    fun `shadowing history store tracks attempts, last score, best score, and tricky words`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ShadowingHistoryStore(context, json)

        val first = store.recordAttempt(
            itemId = "idiom_1",
            sentenceEn = "Let's call it a day.",
            score = 68,
            trickyWords = listOf("call", "day"),
            nowMillis = 1_000L
        )
        assertEquals(1, first.attemptsCount)
        assertEquals(68, first.lastScore)
        assertEquals(68, first.bestScore)

        val second = store.recordAttempt(
            itemId = "idiom_1",
            sentenceEn = "Let's call it a day.",
            score = 92,
            trickyWords = listOf("it"),
            nowMillis = 2_000L
        )
        assertEquals(2, second.attemptsCount)
        assertEquals(92, second.lastScore)
        assertEquals(92, second.bestScore)
        assertTrue(second.trickyWords.containsAll(listOf("it", "call", "day")))
    }
}
