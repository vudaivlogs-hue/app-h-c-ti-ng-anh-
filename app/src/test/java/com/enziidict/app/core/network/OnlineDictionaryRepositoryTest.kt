package com.enziidict.app.core.network

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.data.local.EnziiDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineDictionaryRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: EnziiDatabase
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var secureApiKeyStore: SecureApiKeyStore
    private lateinit var settingsDataStore: SettingsDataStore
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.databaseBuilder(context, EnziiDatabase::class.java, "test_online_dict.db")
            .createFromAsset("dictionary.db")
            .allowMainThreadQueries()
            .build()
        dictionaryRepository = DictionaryRepository(
            dictionaryDao = db.dictionaryDao(),
            wordFolderDao = db.wordFolderDao(),
            savedWordDao = db.savedWordDao(),
            json = json
        )
        secureApiKeyStore = SecureApiKeyStore(context)
        secureApiKeyStore.clearApiKey()
        settingsDataStore = SettingsDataStore(context)
    }

    @After
    fun teardown() {
        secureApiKeyStore.clearApiKey()
        db.close()
        context.getDatabasePath("test_online_dict.db").delete()
    }

    @Test
    fun `lookupOnline fetches Free Dictionary word not in offline db, translates via ML Kit fallback, caches to Room, and serves offline next time`() = runTest {
        // Confirm "serendipity" does not exist in offline seeded DB yet
        val beforeOffline = dictionaryRepository.search("serendipity")
        assertTrue(beforeOffline.none { it.word.equals("serendipity", ignoreCase = true) })

        val sampleFreeDictResponse = """
            [
              {
                "word": "serendipity",
                "phonetics": [
                  {
                    "text": "/ˌsɛɹ.ənˈdɪp.ɪ.ti/",
                    "audio": "//ssl.gstatic.com/dictionary/static/sounds/20200429/serendipity--_us_1.mp3"
                  },
                  {
                    "text": "/ˌsɛr.ənˈdɪp.ə.ti/",
                    "audio": "https://api.dictionaryapi.dev/media/pronunciations/en/serendipity-uk.mp3"
                  }
                ],
                "meanings": [
                  {
                    "partOfSpeech": "noun",
                    "definitions": [
                      {
                        "definition": "The occurrence and development of events by chance in a happy or beneficial way.",
                        "example": "A fortunate stroke of serendipity brought the two founders together."
                      }
                    ],
                    "synonyms": ["fortune", "luck"],
                    "antonyms": ["misfortune"]
                  }
                ]
              }
            ]
        """.trimIndent()

        val onlineRepo = OnlineDictionaryRepository(
            dictionaryRepository = dictionaryRepository,
            secureApiKeyStore = secureApiKeyStore,
            settingsDataStore = settingsDataStore,
            json = json,
            getExecutor = { HttpPostResponse(200, sampleFreeDictResponse) },
            geminiPostExecutor = { _, _ -> HttpPostResponse(500, "") },
            mlKitTranslate = { source ->
                if (source.contains("occurrence", ignoreCase = true)) {
                    Result.success("Sự tình cờ may mắn mang lại kết quả tốt đẹp.")
                } else {
                    Result.success("Một cơ duyên may mắn đã đưa hai nhà sáng lập đến với nhau.")
                }
            }
        )

        val outcome = onlineRepo.lookupOnline("serendipity")
        assertTrue("Expected Success, got $outcome", outcome is OnlineLookupOutcome.Success)
        val success = outcome as OnlineLookupOutcome.Success

        assertTrue(success.newlyCached)
        assertTrue(success.entry.id > 0L)
        assertEquals("serendipity", success.entry.word)
        assertEquals("Sự tình cờ may mắn mang lại kết quả tốt đẹp.", success.entry.meaningVi)
        assertEquals("https://ssl.gstatic.com/dictionary/static/sounds/20200429/serendipity--_us_1.mp3", success.expansion.audioUrlUs)
        assertEquals("https://api.dictionaryapi.dev/media/pronunciations/en/serendipity-uk.mp3", success.expansion.audioUrlUk)
        assertEquals(listOf("misfortune"), success.expansion.antonyms)

        // Verify cached entry is now immediately searchable offline & saveable to a WordFolder
        val afterOffline = dictionaryRepository.search("serendipity")
        val cached = afterOffline.firstOrNull { it.word.equals("serendipity", ignoreCase = true) }
        assertTrue("Cached online word must be found offline afterwards", cached != null)
        assertTrue(dictionaryRepository.saveToDefaultWordbook(cached!!.id))
    }

    @Test
    fun `lookupOnline falls back to Gemini AI for idioms returning 404 on Free Dictionary and caches result`() = runTest {
        secureApiKeyStore.saveApiKey("AIzaTestKeyForIdiom")

        val geminiEnvelope = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\"word\":\"break a leg\",\"ipa_uk\":\"/breɪk ə leɡ/\",\"ipa_us\":\"/breɪk ə leɡ/\",\"pos\":\"idiom\",\"meaning_vi\":\"chúc may mắn (thường dùng trước khi biểu diễn)\",\"definitions\":[{\"definition_en\":\"Good luck! (said to actors before going on stage).\",\"definition_vi\":\"Chúc diễn tốt / chúc may mắn!\",\"example_en\":\"You've practiced hard — go out there and break a leg!\",\"example_vi\":\"Bạn đã tập rất chăm rồi — ra sân khấu và tỏa sáng nhé!\"}],\"synonyms\":[\"good luck\"],\"antonyms\":[\"bad luck\"],\"collocations\":[{\"phrase\":\"go break a leg\",\"meaning_vi\":\"hãy cố gắng hết mình nhé\",\"example\":\"Go break a leg tonight!\"}],\"cefr_level\":\"B2\"}"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val onlineRepo = OnlineDictionaryRepository(
            dictionaryRepository = dictionaryRepository,
            secureApiKeyStore = secureApiKeyStore,
            settingsDataStore = settingsDataStore,
            json = json,
            getExecutor = { HttpPostResponse(404, """{"title":"No Definitions Found"}""") },
            geminiPostExecutor = { _, _ -> HttpPostResponse(200, geminiEnvelope) },
            mlKitTranslate = { Result.failure(IllegalStateException("unused")) }
        )

        val outcome = onlineRepo.lookupOnline("break a leg")
        assertTrue("Expected Gemini fallback Success, got $outcome", outcome is OnlineLookupOutcome.Success)
        val success = outcome as OnlineLookupOutcome.Success
        assertEquals("break a leg", success.entry.word)
        assertEquals("idiom", success.entry.pos)
        assertTrue(success.entry.meaningVi.contains("chúc may mắn"))
        assertEquals("Online • Gemini AI", success.expansion.sourceLabel)
        assertTrue(dictionaryRepository.search("break a leg").any { it.word == "break a leg" })
    }

    @Test
    fun `lookupOnline returns friendly OfflineError when device has no internet connection`() = runTest {
        val onlineRepo = OnlineDictionaryRepository(
            dictionaryRepository = dictionaryRepository,
            secureApiKeyStore = secureApiKeyStore,
            settingsDataStore = settingsDataStore,
            json = json,
            getExecutor = { throw UnknownHostException("api.dictionaryapi.dev") },
            geminiPostExecutor = { _, _ -> HttpPostResponse(500, "") },
            mlKitTranslate = { Result.failure(IllegalStateException("offline")) }
        )

        val outcome = onlineRepo.lookupOnline("ephemeral")
        assertTrue("Expected OfflineError, got $outcome", outcome is OnlineLookupOutcome.OfflineError)
        val offline = outcome as OnlineLookupOutcome.OfflineError
        assertTrue(offline.messageVi.contains("Internet") || offline.messageVi.contains("Offline"))
    }

    @Test
    fun `expanding existing offline word preserves its Room id while returning online expansion`() = runTest {
        val existing = dictionaryRepository.search("decision").first { it.word == "decision" }

        val freeDictResponse = """
            [
              {
                "word": "decision",
                "phonetics": [
                  { "text": "/dɪˈsɪʒ.ən/", "audio": "https://api.dictionaryapi.dev/media/pronunciations/en/decision-us.mp3" }
                ],
                "meanings": [
                  {
                    "partOfSpeech": "noun",
                    "definitions": [
                      {
                        "definition": "A conclusion or resolution reached after consideration.",
                        "example": "She made the right decision under pressure."
                      }
                    ],
                    "synonyms": ["choice", "verdict"],
                    "antonyms": ["indecision"]
                  }
                ]
              }
            ]
        """.trimIndent()

        val onlineRepo = OnlineDictionaryRepository(
            dictionaryRepository = dictionaryRepository,
            secureApiKeyStore = secureApiKeyStore,
            settingsDataStore = settingsDataStore,
            json = json,
            getExecutor = { HttpPostResponse(200, freeDictResponse) },
            geminiPostExecutor = { _, _ -> HttpPostResponse(500, "") },
            mlKitTranslate = { Result.success("Kết luận hoặc sự lựa chọn đạt được sau khi cân nhắc.") }
        )

        val outcome = onlineRepo.lookupOnline("decision") as OnlineLookupOutcome.Success
        assertFalse("Existing offline entry should not insert a duplicate row", outcome.newlyCached)
        assertEquals(existing.id, outcome.entry.id)
        assertEquals(1, outcome.expansion.meaningGroups.size)
        assertEquals(listOf("indecision"), outcome.expansion.antonyms)
    }
}
