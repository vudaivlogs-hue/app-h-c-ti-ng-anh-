package com.enziidict.app.feature.speaking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.model.WordFolders
import com.enziidict.app.core.network.GeminiModelPresets
import com.enziidict.app.core.network.HttpPostResponse
import com.enziidict.app.core.network.SpeakingEvaluationOutcome
import com.enziidict.app.core.network.SpeakingEvaluationService
import com.enziidict.app.data.local.EnziiDatabase
import com.enziidict.app.data.local.entity.SpeakingMessage
import com.enziidict.app.data.local.entity.SpeakingSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeakingEvaluationAndHistoryTest {

    private lateinit var db: EnziiDatabase
    private lateinit var repository: DictionaryRepository
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.databaseBuilder(context, EnziiDatabase::class.java, "test_speaking_history.db")
            .createFromAsset("dictionary.db")
            .allowMainThreadQueries()
            .build()
        repository = DictionaryRepository(
            dictionaryDao = db.dictionaryDao(),
            wordFolderDao = db.wordFolderDao(),
            savedWordDao = db.savedWordDao(),
            json = json
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `missing api key and offline failure are handled gracefully`() = runTest {
        val sampleMessages = listOf(
            SpeakingMessage(sessionId = 1, speaker = "USER", transcript = "I go to cafe yesterday.", suggestionNote = null, createdAt = 1L)
        )

        val noKeyService = SpeakingEvaluationService(json) { _, _ -> HttpPostResponse(200, "{}") }
        val missingOutcome = noKeyService.evaluateSession("Quán cà phê", sampleMessages, "", GeminiModelPresets.FLASH_25)
        assertTrue(missingOutcome is SpeakingEvaluationOutcome.MissingApiKey)

        val offlineService = SpeakingEvaluationService(json) { _, _ -> throw UnknownHostException("offline") }
        val offlineOutcome = offlineService.evaluateSession("Quán cà phê", sampleMessages, "AIzaTest", GeminiModelPresets.FLASH_25)
        assertTrue(offlineOutcome is SpeakingEvaluationOutcome.NetworkError)
    }

    @Test
    fun `end to end speaking history evaluation and save new conversation vocab to SRS folder`() = runTest {
        val sessionId = db.speakingDao().insertSession(
            SpeakingSession(
                topic = "Quán cà phê • Trung cấp",
                voiceName = "Aoede",
                mode = "GEMINI_LIVE",
                startedAt = 10_000L,
                endedAt = null,
                durationSec = 0,
                correctionSuggestionsJson = null
            )
        )
        db.speakingDao().insertMessage(
            SpeakingMessage(
                sessionId = sessionId,
                speaker = "AI",
                transcript = "Welcome! What can I get for you today?",
                suggestionNote = null,
                createdAt = 11_000L
            )
        )
        db.speakingDao().insertMessage(
            SpeakingMessage(
                sessionId = sessionId,
                speaker = "USER",
                transcript = "I want one ice coffee less sweet please.",
                suggestionNote = null,
                createdAt = 12_000L
            )
        )

        val savedMessages = db.speakingDao().getMessagesForSession(sessionId)
        assertEquals(2, savedMessages.size)

        val analysisJson = """
            {
              "score_out_of_10": 8,
              "overall_feedback_vi": "Bạn diễn đạt rõ ý khi gọi đồ uống, chỉ cần chỉnh cụm tính từ tự nhiên hơn.",
              "mistakes": [
                {
                  "original": "I want one ice coffee less sweet please.",
                  "correction": "Could I have an iced coffee with less sugar, please?",
                  "explanation_vi": "Dùng 'iced coffee' thay vì 'ice coffee' và 'Could I have...' lịch sự, tự nhiên hơn."
                }
              ],
              "suggested_vocabulary": [
                {
                  "word": "oat milk latte",
                  "pos": "noun phrase",
                  "meaning_vi": "cà phê latte sữa yến mạch"
                }
              ]
            }
        """.trimIndent()
        val envelope = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": ${json.encodeToString(kotlinx.serialization.serializer<String>(), analysisJson)} }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val evalService = SpeakingEvaluationService(json) { _, _ -> HttpPostResponse(200, envelope) }
        val outcome = evalService.evaluateSession(
            topicTitle = "Quán cà phê • Trung cấp",
            messages = savedMessages,
            apiKey = "AIzaValid",
            model = GeminiModelPresets.FLASH_25
        )
        assertTrue(outcome is SpeakingEvaluationOutcome.Success)
        val success = outcome as SpeakingEvaluationOutcome.Success

        // Persist evaluation to speaking_sessions
        db.speakingDao().completeSession(
            sessionId = sessionId,
            endedAt = 45_000L,
            durationSec = 35,
            suggestionsJson = success.serializedJson
        )
        val storedSession = db.speakingDao().getSessionById(sessionId)!!
        assertEquals(35, storedSession.durationSec)
        assertTrue(storedSession.correctionSuggestionsJson!!.contains("iced coffee"))

        // Save brand-new AI-suggested vocabulary into "Từ lưu từ hội thoại" SRS folder
        val vocab = success.analysis.suggestedVocabulary.first()
        val saved = repository.saveSuggestedWordToConversationFolder(
            word = vocab.word,
            pos = vocab.pos,
            meaningVi = vocab.meaningVi
        )
        assertTrue(saved)

        val dueInConversationFolder = db.savedWordDao().getDueWordsByFolder(
            folderName = WordFolders.CONVERSATION,
            nowMillis = System.currentTimeMillis() + 60_000L
        )
        assertTrue(dueInConversationFolder.any { it.entry.word == "oat milk latte" })
    }
}
