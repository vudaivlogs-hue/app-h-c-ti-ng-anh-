package com.enziidict.app.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class GeminiGrammarServiceTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `missing api key returns MissingApiKey without calling network`() = runTest {
        var called = false
        val service = GeminiGrammarService(json) { _, _ ->
            called = true
            HttpPostResponse(200, "{}")
        }

        val outcome = service.analyze(
            text = "Let's call it a day.",
            enToVi = true,
            apiKey = "   ",
            model = GeminiModelPresets.FLASH_25
        )

        assertTrue(outcome is GrammarAnalysisOutcome.MissingApiKey)
        assertTrue("HTTP executor must not be invoked when API key is missing", !called)
    }

    @Test
    fun `offline network failure returns NetworkError with Vietnamese guidance`() = runTest {
        val service = GeminiGrammarService(json) { _, _ ->
            throw UnknownHostException("generativelanguage.googleapis.com")
        }

        val outcome = service.analyze(
            text = "Had I known earlier, I would have helped.",
            enToVi = true,
            apiKey = "AIzaTestKey123",
            model = GeminiModelPresets.FLASH_25
        )

        assertTrue(outcome is GrammarAnalysisOutcome.NetworkError)
        val msg = (outcome as GrammarAnalysisOutcome.NetworkError).messageVi
        assertTrue(msg.contains("Internet"))
    }

    @Test
    fun `unauthorized api key returns ApiError 401`() = runTest {
        val service = GeminiGrammarService(json) { _, _ ->
            HttpPostResponse(401, """{"error":{"message":"API key not valid"}}""")
        }

        val outcome = service.analyze(
            text = "Hello world",
            enToVi = true,
            apiKey = "bad-key",
            model = GeminiModelPresets.FLASH_25
        )

        assertTrue(outcome is GrammarAnalysisOutcome.ApiError)
        assertEquals(401, (outcome as GrammarAnalysisOutcome.ApiError).httpCode)
    }

    @Test
    fun `valid Gemini structured JSON response parses into GrammarAnalysisResult`() = runTest {
        val innerJson = """
            ```json
            {
              "natural_translation": "Hôm nay dừng ở đây thôi nhé.",
              "sentence_structure": "Câu đề nghị với Let's + V nguyên thể",
              "grammar_points": [
                { "pattern": "Let's + V-inf", "explanation_vi": "Dùng để rủ hoặc đề xuất cùng làm gì." }
              ],
              "phrases_and_idioms": [
                { "phrase": "call it a day", "meaning_vi": "kết thúc công việc trong ngày", "nuance_vi": "Tự nhiên, thân mật trong công sở" }
              ],
              "natural_alternatives": [
                { "expression": "Let's wrap things up for today.", "context_vi": "Trang trọng hơn một chút trong cuộc họp" }
              ]
            }
            ```
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

        val service = GeminiGrammarService(json) { _, _ -> HttpPostResponse(200, envelope) }
        val outcome = service.analyze(
            text = "Let's call it a day.",
            enToVi = true,
            apiKey = "AIzaValidTest",
            model = GeminiModelPresets.FLASH_25
        )

        assertTrue(outcome is GrammarAnalysisOutcome.Success)
        val result = (outcome as GrammarAnalysisOutcome.Success).analysis
        assertEquals("Hôm nay dừng ở đây thôi nhé.", result.naturalTranslation)
        assertEquals(1, result.grammarPoints.size)
        assertEquals("call it a day", result.phrasesAndIdioms.first().phrase)
        assertEquals("Let's wrap things up for today.", result.naturalAlternatives.first().expression)
    }
}
