package com.enziidict.app.core.network

import android.util.Base64
import com.enziidict.app.core.model.AiVoice
import com.enziidict.app.core.model.SpeakingLevel
import com.enziidict.app.core.model.SpeakingTopic
import com.enziidict.app.data.local.entity.SpeakingMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiModelDiscoveryAndLiveProtocolTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `discoverModels filters non-chat models and ranks current Flash text & native audio models first`() = runTest {
        val modelsPayload = """
            {
              "models": [
                {
                  "name": "models/text-embedding-004",
                  "displayName": "Text Embedding",
                  "supportedGenerationMethods": ["embedContent"]
                },
                {
                  "name": "models/gemini-2.0-flash",
                  "displayName": "Gemini 2.0 Flash",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-2.5-pro",
                  "displayName": "Gemini 2.5 Pro",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-3.5-flash-lite",
                  "displayName": "Gemini 3.5 Flash Lite",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-3.8-flash",
                  "displayName": "Gemini 3.8 Flash",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-2.5-flash-preview-tts",
                  "displayName": "Gemini TTS",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-2.5-flash-native-audio-preview-09-2025",
                  "displayName": "Live Audio 09",
                  "supportedGenerationMethods": ["bidiGenerateContent"]
                },
                {
                  "name": "models/gemini-2.5-flash-native-audio-preview-12-2025",
                  "displayName": "Live Audio 12",
                  "supportedGenerationMethods": ["bidiGenerateContent"]
                }
              ]
            }
        """.trimIndent()

        val service = GeminiModelCatalogService(json) { _ ->
            HttpPostResponse(200, modelsPayload)
        }

        val outcome = service.discoverModels("AIzaValidKey")
        assertTrue(outcome is ModelDiscoveryOutcome.Success)
        val catalog = (outcome as ModelDiscoveryOutcome.Success).catalog

        assertEquals("gemini-3.8-flash", catalog.recommendedTextModel)
        assertTrue(catalog.textModels.none { it.id.contains("embedding") })
        assertTrue(catalog.textModels.none { it.id.contains("tts") })
        assertEquals(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            catalog.recommendedLiveModel
        )
        assertEquals(2, catalog.liveAudioModels.size)
    }

    @Test
    fun `extractApiErrorMessage extracts Google JSON error details cleanly`() {
        val raw = """
            {
              "error": {
                "code": 404,
                "message": "models/gemini-2.5-flash is not found for API version v1beta",
                "status": "NOT_FOUND"
              }
            }
        """.trimIndent()
        val msg = GeminiModelCatalogService.extractApiErrorMessage(json, raw)
        assertEquals("models/gemini-2.5-flash is not found for API version v1beta", msg)
    }

    @Test
    fun `grammar service recovers from HTTP 404 by discovering active model and retrying`() = runTest {
        val catalogService = GeminiModelCatalogService(json) { _ ->
            HttpPostResponse(
                200,
                """
                {
                  "models": [
                    {
                      "name": "models/gemini-3.8-flash",
                      "displayName": "Gemini 3.8 Flash",
                      "supportedGenerationMethods": ["generateContent"]
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        val requestedUrls = mutableListOf<String>()
        val innerJson = """
            {
              "natural_translation": "Bạn khỏe không?",
              "sentence_structure": "Wh-question (Present Simple)",
              "grammar_points": [],
              "phrases_and_idioms": [],
              "natural_alternatives": []
            }
        """.trimIndent()
        val okEnvelope = """
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

        val grammarService = GeminiGrammarService(
            json = json,
            httpExecutor = GeminiHttpExecutor { url, _ ->
                requestedUrls += url
                if (url.contains("gemini-2.0-flash")) {
                    HttpPostResponse(404, """{"error":{"message":"Model gemini-2.0-flash is retired"}}""")
                } else {
                    HttpPostResponse(200, okEnvelope)
                }
            },
            modelCatalogService = catalogService,
            settingsDataStore = null
        )

        val outcome = grammarService.analyze(
            text = "How are you?",
            enToVi = true,
            apiKey = "AIzaTestKey",
            model = GeminiModelPresets.FLASH_20
        )

        assertTrue(outcome is GrammarAnalysisOutcome.Success)
        assertEquals("Bạn khỏe không?", (outcome as GrammarAnalysisOutcome.Success).analysis.naturalTranslation)
        assertEquals(2, requestedUrls.size)
        assertTrue(requestedUrls[0].contains("gemini-2.0-flash"))
        assertTrue(requestedUrls[1].contains("gemini-3.8-flash"))
    }

    @Test
    fun `GeminiLiveClient normalizes setup model, builds realtimeInput audio payload, and parses server messages`() = runTest {
        val client = GeminiLiveClient(json)

        val setupJson = client.buildSetupPayload(
            model = "gemini-2.5-flash-native-audio-preview-12-2025",
            topic = SpeakingTopic.CAFE,
            level = SpeakingLevel.INTERMEDIATE,
            voice = AiVoice.AOEDE,
            customTopicPrompt = ""
        )
        val setupObj = json.parseToJsonElement(setupJson).jsonObject["setup"]!!.jsonObject
        assertEquals(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            setupObj["model"]!!.jsonPrimitive.content
        )

        val audioPayload = client.buildAudioChunkPayload(byteArrayOf(1, 2, 3, 4))
        val realtimeInput = json.parseToJsonElement(audioPayload).jsonObject["realtimeInput"]!!.jsonObject
        assertNotNull(realtimeInput["audio"])
        assertFalse(realtimeInput.containsKey("mediaChunks"))

        assertFalse(client.isReadyForAudio)

        val setupDeferred = async(start = CoroutineStart.UNDISPATCHED) { client.events.first() }
        client.parseServerMessage("""{"setupComplete":{}}""")
        assertTrue(setupDeferred.await() is GeminiLiveEvent.SetupComplete)

        val samplePcm = Base64.encodeToString(byteArrayOf(10, 20, 30), Base64.NO_WRAP)
        val transcriptDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            client.events.first { it is GeminiLiveEvent.OutputTranscript }
        }
        client.parseServerMessage(
            """
            {
              "serverContent": {
                "outputTranscription": { "text": "Welcome to our cafe!" },
                "modelTurn": {
                  "parts": [
                    { "inlineData": { "mimeType": "audio/pcm;rate=24000", "data": "$samplePcm" } }
                  ]
                }
              }
            }
            """.trimIndent()
        )
        val outEvent = transcriptDeferred.await() as GeminiLiveEvent.OutputTranscript
        assertEquals("Welcome to our cafe!", outEvent.text)

        val errorDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            client.events.first { it is GeminiLiveEvent.Error }
        }
        client.parseServerMessage("""{"error":{"message":"Invalid model requested"}}""")
        assertEquals("Invalid model requested", (errorDeferred.await() as GeminiLiveEvent.Error).message)
    }

    @Test
    fun `speaking turn service builds multi-turn payload and recovers from HTTP 404`() = runTest {
        val catalogService = GeminiModelCatalogService(json) { _ ->
            HttpPostResponse(
                200,
                """
                {
                  "models": [
                    {
                      "name": "models/gemini-3.5-flash-lite",
                      "displayName": "Gemini 3.5 Flash Lite",
                      "supportedGenerationMethods": ["generateContent"]
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        val turnService = GeminiSpeakingTurnService(
            json = json,
            httpExecutor = GeminiHttpExecutor { url, _ ->
                if (url.contains("gemini-2.0-flash")) {
                    HttpPostResponse(404, """{"error":{"message":"Model not found"}}""")
                } else {
                    HttpPostResponse(
                        200,
                        """
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "Great choice! Would you like your iced coffee with oat milk or regular milk?" }
                                ]
                              }
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                }
            },
            modelCatalogService = catalogService,
            settingsDataStore = null
        )

        val history = listOf(
            SpeakingMessage(sessionId = 1, speaker = "AI", transcript = "Welcome!", suggestionNote = null, createdAt = 1L),
            SpeakingMessage(sessionId = 1, speaker = "USER", transcript = "I want an iced coffee.", suggestionNote = null, createdAt = 2L)
        )
        val payloadObj = json.parseToJsonElement(
            turnService.buildConversationPayload(SpeakingTopic.CAFE, SpeakingLevel.BEGINNER, "", history)
        ).jsonObject
        val contents = payloadObj["contents"]!!.jsonArray
        assertEquals(2, contents.size)
        assertEquals("model", contents[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", contents[1].jsonObject["role"]!!.jsonPrimitive.content)

        val outcome = turnService.generateReply(
            topic = SpeakingTopic.CAFE,
            level = SpeakingLevel.BEGINNER,
            customTopicPrompt = "",
            history = history,
            apiKey = "AIzaTestKey",
            model = GeminiModelPresets.FLASH_20
        )
        assertTrue(outcome is SpeakingTurnOutcome.Success)
        assertTrue((outcome as SpeakingTurnOutcome.Success).replyText.contains("oat milk"))
    }
}
