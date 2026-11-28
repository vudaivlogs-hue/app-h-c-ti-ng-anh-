package com.enziidict.app.core.network

import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.model.CollocationItem
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.model.ExampleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class OnlineDefinitionItem(
    val definitionEn: String,
    val definitionVi: String = "",
    val exampleEn: String = "",
    val exampleVi: String = ""
)

data class OnlineMeaningGroup(
    val partOfSpeech: String,
    val definitions: List<OnlineDefinitionItem>,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList()
)

data class OnlineWordExpansion(
    val word: String,
    val ipaUk: String,
    val ipaUs: String,
    val audioUrlUk: String?,
    val audioUrlUs: String?,
    val meaningGroups: List<OnlineMeaningGroup>,
    val collocations: List<CollocationItem> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val sourceLabel: String
)

sealed interface OnlineLookupOutcome {
    data class Success(
        val entry: DictionaryEntry,
        val expansion: OnlineWordExpansion,
        val newlyCached: Boolean
    ) : OnlineLookupOutcome

    data class NotFound(
        val query: String,
        val messageVi: String
    ) : OnlineLookupOutcome

    data class OfflineError(val messageVi: String) : OnlineLookupOutcome
    data class ApiError(val messageVi: String) : OnlineLookupOutcome
}

fun interface OnlineHttpGetExecutor {
    @Throws(IOException::class)
    fun get(url: String): HttpPostResponse
}

@Serializable
private data class GeminiEnrichPayload(
    @SerialName("meaning_vi") val meaningVi: String = "",
    @SerialName("definition_translations") val definitionTranslations: List<String> = emptyList(),
    @SerialName("example_translations") val exampleTranslations: List<String> = emptyList(),
    val collocations: List<CollocationItem> = emptyList(),
    @SerialName("cefr_level") val cefrLevel: String? = null
)

@Serializable
private data class GeminiFallbackDefPayload(
    @SerialName("definition_en") val definitionEn: String = "",
    @SerialName("definition_vi") val definitionVi: String = "",
    @SerialName("example_en") val exampleEn: String = "",
    @SerialName("example_vi") val exampleVi: String = ""
)

@Serializable
private data class GeminiFallbackWordPayload(
    val word: String = "",
    @SerialName("ipa_uk") val ipaUk: String = "",
    @SerialName("ipa_us") val ipaUs: String = "",
    val pos: String = "phrase",
    @SerialName("meaning_vi") val meaningVi: String = "",
    val definitions: List<GeminiFallbackDefPayload> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val collocations: List<CollocationItem> = emptyList(),
    @SerialName("cefr_level") val cefrLevel: String? = null
)

@Singleton
class OnlineDictionaryRepository internal constructor(
    private val dictionaryRepository: DictionaryRepository,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val settingsDataStore: SettingsDataStore,
    private val json: Json,
    private val getExecutor: OnlineHttpGetExecutor,
    private val geminiPostExecutor: GeminiHttpExecutor,
    private val mlKitTranslate: suspend (String) -> Result<String>,
    private val modelCatalogService: GeminiModelCatalogService? = null
) {

    @Inject
    constructor(
        dictionaryRepository: DictionaryRepository,
        offlineMlKitTranslator: OfflineMlKitTranslator,
        secureApiKeyStore: SecureApiKeyStore,
        settingsDataStore: SettingsDataStore,
        modelCatalogService: GeminiModelCatalogService,
        json: Json
    ) : this(
        dictionaryRepository = dictionaryRepository,
        secureApiKeyStore = secureApiKeyStore,
        settingsDataStore = settingsDataStore,
        json = json,
        getExecutor = defaultGetExecutor(),
        geminiPostExecutor = defaultGeminiPostExecutor(),
        mlKitTranslate = { text -> offlineMlKitTranslator.translate(text, enToVi = true) },
        modelCatalogService = modelCatalogService
    )

    suspend fun lookupOnline(rawQuery: String): OnlineLookupOutcome {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return OnlineLookupOutcome.NotFound(query, "Vui lòng nhập từ khóa cần tra cứu.")
        }

        val apiKey = secureApiKeyStore.getApiKey().trim()
        val model = settingsDataStore.settingsFlow.first().geminiModel
        val encoded = withContext(Dispatchers.IO) {
            URLEncoder.encode(query.lowercase(), "UTF-8").replace("+", "%20")
        }
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/$encoded"

        val response = try {
            withContext(Dispatchers.IO) { getExecutor.get(url) }
        } catch (_: UnknownHostException) {
            return OnlineLookupOutcome.OfflineError(
                "Thiết bị đang không có kết nối Internet. Bạn vẫn có thể tra toàn bộ kho từ Offline đã lưu."
            )
        } catch (_: SocketTimeoutException) {
            return OnlineLookupOutcome.OfflineError(
                "Kết nối tới từ điển trực tuyến quá thời gian chờ. Vui lòng thử lại khi mạng ổn định."
            )
        } catch (e: IOException) {
            return OnlineLookupOutcome.OfflineError(
                "Không thể kết nối Internet (${e.message ?: "lỗi mạng"}). Bạn vẫn có thể dùng từ điển Offline."
            )
        }

        return when {
            response.statusCode in 200..299 -> {
                handleFreeDictionarySuccess(
                    rawBody = response.body,
                    fallbackQuery = query,
                    apiKey = apiKey,
                    model = model
                )
            }
            response.statusCode == 404 -> {
                handleFreeDictionaryNotFound(
                    query = query,
                    apiKey = apiKey,
                    model = model
                )
            }
            else -> {
                OnlineLookupOutcome.ApiError(
                    "Máy chủ từ điển trực tuyến tạm thời phản hồi lỗi (HTTP ${response.statusCode}). Vui lòng thử lại."
                )
            }
        }
    }

    private suspend fun handleFreeDictionarySuccess(
        rawBody: String,
        fallbackQuery: String,
        apiKey: String,
        model: String
    ): OnlineLookupOutcome {
        val parsed = parseFreeDictionaryPayload(rawBody, fallbackQuery)
            ?: return OnlineLookupOutcome.ApiError("Không đọc được dữ liệu từ Free Dictionary API.")

        val topDefsEn = parsed.meaningGroups
            .flatMap { it.definitions }
            .map { it.definitionEn }
            .filter { it.isNotBlank() }
            .take(4)

        val topExamplesEn = parsed.meaningGroups
            .flatMap { it.definitions }
            .map { it.exampleEn }
            .filter { it.isNotBlank() }
            .take(4)

        val primaryPos = parsed.meaningGroups.firstOrNull()?.partOfSpeech ?: "noun"

        var meaningVi = ""
        var defTranslations = emptyList<String>()
        var exTranslations = emptyList<String>()
        var collocations = emptyList<CollocationItem>()
        var cefrLevel: String? = null
        var sourceLabel = "Online • Free Dictionary + ML Kit"

        if (apiKey.isNotBlank()) {
            val geminiEnrich = enrichWithGemini(
                word = parsed.word,
                pos = primaryPos,
                defsEn = topDefsEn,
                examplesEn = topExamplesEn,
                apiKey = apiKey,
                model = model
            )
            if (geminiEnrich != null) {
                meaningVi = geminiEnrich.meaningVi.trim()
                defTranslations = geminiEnrich.definitionTranslations
                exTranslations = geminiEnrich.exampleTranslations
                collocations = geminiEnrich.collocations
                cefrLevel = geminiEnrich.cefrLevel
                sourceLabel = "Online • Free Dictionary + Gemini AI"
            }
        }

        if (meaningVi.isBlank()) {
            val firstDef = topDefsEn.firstOrNull().orEmpty()
            val translatedWordOrDef = if (firstDef.isNotBlank()) {
                mlKitTranslate(firstDef).getOrNull()?.trim().orEmpty()
            } else {
                ""
            }
            val translatedWordOnly = mlKitTranslate(parsed.word).getOrNull()?.trim().orEmpty()
            meaningVi = when {
                translatedWordOrDef.isNotBlank() -> translatedWordOrDef
                translatedWordOnly.isNotBlank() -> translatedWordOnly
                firstDef.isNotBlank() -> firstDef
                else -> parsed.word
            }
            if (defTranslations.isEmpty() && translatedWordOrDef.isNotBlank()) {
                defTranslations = listOf(translatedWordOrDef)
            }
            if (exTranslations.isEmpty() && topExamplesEn.isNotEmpty()) {
                exTranslations = topExamplesEn.take(2).map { ex ->
                    mlKitTranslate(ex).getOrNull()?.trim().orEmpty()
                }
            }
        }

        val defMap = HashMap<String, String>()
        topDefsEn.forEachIndexed { index, def ->
            val tr = defTranslations.getOrNull(index).orEmpty().trim()
            if (tr.isNotEmpty()) defMap[def] = tr
        }
        val exMap = HashMap<String, String>()
        topExamplesEn.forEachIndexed { index, ex ->
            val tr = exTranslations.getOrNull(index).orEmpty().trim()
            if (tr.isNotEmpty()) exMap[ex] = tr
        }

        val enrichedGroups = parsed.meaningGroups.map { group ->
            group.copy(
                definitions = group.definitions.map { item ->
                    item.copy(
                        definitionVi = defMap[item.definitionEn].orEmpty(),
                        exampleVi = exMap[item.exampleEn].orEmpty()
                    )
                }
            )
        }

        val examplesForCache = enrichedGroups
            .flatMap { it.definitions }
            .filter { it.exampleEn.isNotBlank() }
            .distinctBy { it.exampleEn.lowercase() }
            .take(4)
            .map { ExampleItem(en = it.exampleEn, vi = it.exampleVi) }

        val (cachedEntry, newlyCached) = dictionaryRepository.cacheOnlineEntry(
            word = parsed.word,
            ipaUk = parsed.ipaUk,
            ipaUs = parsed.ipaUs,
            pos = primaryPos,
            meaningVi = meaningVi,
            examples = examplesForCache,
            synonyms = parsed.synonyms,
            collocations = collocations,
            cefrLevel = cefrLevel
        )

        val expansion = parsed.copy(
            meaningGroups = enrichedGroups,
            collocations = collocations,
            sourceLabel = sourceLabel
        )
        return OnlineLookupOutcome.Success(
            entry = cachedEntry,
            expansion = expansion,
            newlyCached = newlyCached
        )
    }

    private suspend fun handleFreeDictionaryNotFound(
        query: String,
        apiKey: String,
        model: String
    ): OnlineLookupOutcome {
        if (apiKey.isBlank()) {
            return OnlineLookupOutcome.NotFound(
                query = query,
                messageVi = "Không tìm thấy \"$query\" trên Free Dictionary API. Hãy kiểm tra lại chính tả hoặc thêm Gemini API Key trong Cài đặt để tra cụm từ, thành ngữ và tiếng lóng."
            )
        }

        val fallback = lookupWithGeminiFallback(query, apiKey, model)
        return when (fallback) {
            is GeminiFallbackResult.Success -> {
                val payload = fallback.payload
                val resolvedWord = payload.word.trim().ifEmpty { query }
                val examples = payload.definitions
                    .filter { it.exampleEn.isNotBlank() }
                    .map { ExampleItem(en = it.exampleEn.trim(), vi = it.exampleVi.trim()) }

                val (cachedEntry, newlyCached) = dictionaryRepository.cacheOnlineEntry(
                    word = resolvedWord,
                    ipaUk = payload.ipaUk,
                    ipaUs = payload.ipaUs,
                    pos = payload.pos,
                    meaningVi = payload.meaningVi.ifBlank { query },
                    examples = examples,
                    synonyms = payload.synonyms,
                    collocations = payload.collocations,
                    cefrLevel = payload.cefrLevel
                )

                val meaningGroup = OnlineMeaningGroup(
                    partOfSpeech = payload.pos.ifBlank { "phrase" },
                    definitions = payload.definitions.map {
                        OnlineDefinitionItem(
                            definitionEn = it.definitionEn,
                            definitionVi = it.definitionVi,
                            exampleEn = it.exampleEn,
                            exampleVi = it.exampleVi
                        )
                    },
                    synonyms = payload.synonyms,
                    antonyms = payload.antonyms
                )

                val expansion = OnlineWordExpansion(
                    word = resolvedWord,
                    ipaUk = payload.ipaUk,
                    ipaUs = payload.ipaUs,
                    audioUrlUk = null,
                    audioUrlUs = null,
                    meaningGroups = listOf(meaningGroup),
                    collocations = payload.collocations,
                    synonyms = payload.synonyms,
                    antonyms = payload.antonyms,
                    sourceLabel = "Online • Gemini AI"
                )
                OnlineLookupOutcome.Success(cachedEntry, expansion, newlyCached)
            }
            is GeminiFallbackResult.Offline -> OnlineLookupOutcome.OfflineError(fallback.messageVi)
            is GeminiFallbackResult.Failure -> OnlineLookupOutcome.NotFound(
                query = query,
                messageVi = fallback.messageVi
            )
        }
    }

    internal fun parseFreeDictionaryPayload(rawBody: String, fallbackWord: String): OnlineWordExpansion? {
        return runCatching {
            val array = json.parseToJsonElement(rawBody).jsonArray
            if (array.isEmpty()) return null

            val firstObj = array.first().jsonObject
            val word = firstObj.stringOrNull("word").orEmpty().ifBlank { fallbackWord }

            var ipaUk = ""
            var ipaUs = ""
            var fallbackIpa = firstObj.stringOrNull("phonetic").orEmpty()
            var audioUk: String? = null
            var audioUs: String? = null
            var fallbackAudio: String? = null

            val groupsByPos = LinkedHashMap<String, MutableList<OnlineDefinitionItem>>()
            val posSynonyms = LinkedHashMap<String, LinkedHashSet<String>>()
            val posAntonyms = LinkedHashMap<String, LinkedHashSet<String>>()
            val allSynonyms = LinkedHashSet<String>()
            val allAntonyms = LinkedHashSet<String>()

            for (element in array) {
                val entryObj = element.jsonObject
                if (fallbackIpa.isBlank()) {
                    fallbackIpa = entryObj.stringOrNull("phonetic").orEmpty()
                }
                val phonetics = entryObj["phonetics"]?.jsonArray.orEmpty()
                for (pEl in phonetics) {
                    val pObj = pEl.jsonObject
                    val text = pObj.stringOrNull("text").orEmpty()
                    val audio = normalizeAudioUrl(pObj.stringOrNull("audio"))
                    if (fallbackIpa.isBlank() && text.isNotBlank()) fallbackIpa = text
                    if (fallbackAudio == null && audio != null) fallbackAudio = audio

                    val lowerAudio = audio.orEmpty().lowercase()
                    when {
                        lowerAudio.contains("-uk") || lowerAudio.contains("_uk") || lowerAudio.contains("/uk") -> {
                            if (audioUk == null && audio != null) audioUk = audio
                            if (ipaUk.isBlank() && text.isNotBlank()) ipaUk = text
                        }
                        lowerAudio.contains("-us") || lowerAudio.contains("_us") || lowerAudio.contains("/us") -> {
                            if (audioUs == null && audio != null) audioUs = audio
                            if (ipaUs.isBlank() && text.isNotBlank()) ipaUs = text
                        }
                    }
                }

                val meanings = entryObj["meanings"]?.jsonArray.orEmpty()
                for (mEl in meanings) {
                    val mObj = mEl.jsonObject
                    val pos = mObj.stringOrNull("partOfSpeech").orEmpty().ifBlank { "noun" }.lowercase()
                    val list = groupsByPos.getOrPut(pos) { mutableListOf() }
                    val synSet = posSynonyms.getOrPut(pos) { LinkedHashSet() }
                    val antSet = posAntonyms.getOrPut(pos) { LinkedHashSet() }

                    mObj["synonyms"]?.jsonArray?.forEach {
                        val s = it.jsonPrimitive.content.trim()
                        if (s.isNotEmpty()) {
                            synSet.add(s)
                            allSynonyms.add(s)
                        }
                    }
                    mObj["antonyms"]?.jsonArray?.forEach {
                        val a = it.jsonPrimitive.content.trim()
                        if (a.isNotEmpty()) {
                            antSet.add(a)
                            allAntonyms.add(a)
                        }
                    }

                    val defs = mObj["definitions"]?.jsonArray.orEmpty()
                    for (dEl in defs) {
                        if (list.size >= 4) break
                        val dObj = dEl.jsonObject
                        val defText = dObj.stringOrNull("definition").orEmpty()
                        if (defText.isBlank()) continue
                        if (list.any { it.definitionEn.equals(defText, ignoreCase = true) }) continue

                        val exText = dObj.stringOrNull("example").orEmpty()
                        list.add(
                            OnlineDefinitionItem(
                                definitionEn = defText,
                                exampleEn = exText
                            )
                        )

                        dObj["synonyms"]?.jsonArray?.forEach {
                            val s = it.jsonPrimitive.content.trim()
                            if (s.isNotEmpty()) {
                                synSet.add(s)
                                allSynonyms.add(s)
                            }
                        }
                        dObj["antonyms"]?.jsonArray?.forEach {
                            val a = it.jsonPrimitive.content.trim()
                            if (a.isNotEmpty()) {
                                antSet.add(a)
                                allAntonyms.add(a)
                            }
                        }
                    }
                }
            }

            if (ipaUk.isBlank()) ipaUk = ipaUs.ifBlank { fallbackIpa }
            if (ipaUs.isBlank()) ipaUs = ipaUk.ifBlank { fallbackIpa }
            if (audioUs == null && audioUk == null && fallbackAudio != null) {
                audioUs = fallbackAudio
            }

            val meaningGroups = groupsByPos.entries
                .take(4)
                .map { (pos, defs) ->
                    OnlineMeaningGroup(
                        partOfSpeech = pos,
                        definitions = defs,
                        synonyms = posSynonyms[pos]?.take(6).orEmpty(),
                        antonyms = posAntonyms[pos]?.take(6).orEmpty()
                    )
                }

            OnlineWordExpansion(
                word = word,
                ipaUk = ipaUk,
                ipaUs = ipaUs,
                audioUrlUk = audioUk,
                audioUrlUs = audioUs,
                meaningGroups = meaningGroups,
                synonyms = allSynonyms.take(8),
                antonyms = allAntonyms.take(8),
                sourceLabel = "Online • Free Dictionary"
            )
        }.getOrNull()
    }

    private suspend fun postGeminiWithAutoRecovery(
        apiKey: String,
        initialModel: String,
        body: String
    ): HttpPostResponse {
        val normalizedInitial = GeminiModelCatalogService.normalizeModelId(initialModel)
            .ifEmpty { GeminiModelPresets.FLASH_25 }
        val firstUrl = "https://generativelanguage.googleapis.com/v1beta/models/$normalizedInitial:generateContent?key=$apiKey"
        val firstResp = geminiPostExecutor.postJson(firstUrl, body)
        if (firstResp.statusCode != 404 || modelCatalogService == null) {
            return firstResp
        }

        val discovered = modelCatalogService.discoverModels(apiKey, forceRefresh = true)
        val fallbackModel = (discovered as? ModelDiscoveryOutcome.Success)
            ?.catalog
            ?.recommendedTextModel
            ?.let { GeminiModelCatalogService.normalizeModelId(it) }
            ?.takeIf { it.isNotEmpty() && it != normalizedInitial }
            ?: return firstResp

        val retryUrl = "https://generativelanguage.googleapis.com/v1beta/models/$fallbackModel:generateContent?key=$apiKey"
        val retryResp = geminiPostExecutor.postJson(retryUrl, body)
        if (retryResp.statusCode in 200..299) {
            settingsDataStore.saveGeminiModel(fallbackModel)
        }
        return retryResp
    }

    private suspend fun enrichWithGemini(
        word: String,
        pos: String,
        defsEn: List<String>,
        examplesEn: List<String>,
        apiKey: String,
        model: String
    ): GeminiEnrichPayload? {
        val prompt = buildString {
            appendLine("You are an English-Vietnamese lexicographer for Enzii Dict.")
            appendLine("Word: \"$word\" (primary POS: $pos)")
            appendLine("English definitions:")
            defsEn.forEachIndexed { idx, d -> appendLine("${idx + 1}. $d") }
            if (examplesEn.isNotEmpty()) {
                appendLine("Examples:")
                examplesEn.forEachIndexed { idx, ex -> appendLine("${idx + 1}. $ex") }
            }
            appendLine(
                """
                Respond ONLY with valid JSON:
                {
                  "meaning_vi": "1-3 concise, natural Vietnamese meanings separated by '; '",
                  "definition_translations": ["Vietnamese translation matching each definition above in order"],
                  "example_translations": ["Vietnamese translation matching each example above in order"],
                  "collocations": [
                    { "phrase": "natural English collocation", "meaning_vi": "nghĩa tiếng Việt", "example": "short English example" }
                  ],
                  "cefr_level": "A1|A2|B1|B2|C1|C2"
                }
                """.trimIndent()
            )
        }

        val body = buildGeminiRequest(prompt)
        return withContext(Dispatchers.IO) {
            runCatching {
                val resp = postGeminiWithAutoRecovery(apiKey = apiKey, initialModel = model, body = body)
                if (resp.statusCode !in 200..299) return@runCatching null
                val candidate = extractGeminiText(resp.body) ?: return@runCatching null
                json.decodeFromString<GeminiEnrichPayload>(stripMarkdownFences(candidate))
            }.getOrNull()
        }
    }

    private sealed interface GeminiFallbackResult {
        data class Success(val payload: GeminiFallbackWordPayload) : GeminiFallbackResult
        data class Offline(val messageVi: String) : GeminiFallbackResult
        data class Failure(val messageVi: String) : GeminiFallbackResult
    }

    private suspend fun lookupWithGeminiFallback(
        query: String,
        apiKey: String,
        model: String
    ): GeminiFallbackResult {
        val prompt = """
            You are an English-Vietnamese dictionary editor in Enzii Dict.
            Explain the English word, idiom, slang, or phrase: "$query"
            Respond ONLY with valid JSON matching this schema:
            {
              "word": "$query",
              "ipa_uk": "/.../",
              "ipa_us": "/.../",
              "pos": "noun|verb|adjective|adverb|idiom|phrase|slang",
              "meaning_vi": "Nghĩa tiếng Việt tự nhiên, rõ ràng",
              "definitions": [
                {
                  "definition_en": "Clear English definition",
                  "definition_vi": "Nghĩa tiếng Việt tương ứng",
                  "example_en": "Natural real-life English sentence",
                  "example_vi": "Dịch nghĩa tiếng Việt"
                }
              ],
              "synonyms": ["synonym1", "synonym2"],
              "antonyms": ["antonym1"],
              "collocations": [
                { "phrase": "common collocation", "meaning_vi": "nghĩa", "example": "short example" }
              ],
              "cefr_level": "B1"
            }
        """.trimIndent()

        val body = buildGeminiRequest(prompt)
        return withContext(Dispatchers.IO) {
            try {
                val resp = postGeminiWithAutoRecovery(apiKey = apiKey, initialModel = model, body = body)
                if (resp.statusCode !in 200..299) {
                    val detail = GeminiModelCatalogService.extractApiErrorMessage(json, resp.body)
                        ?.let { " (${GeminiModelCatalogService.truncateDetail(it)})" }
                        .orEmpty()
                    return@withContext GeminiFallbackResult.Failure(
                        "Không tìm thấy \"$query\" trên Free Dictionary API và Gemini phản hồi mã lỗi ${resp.statusCode}$detail."
                    )
                }
                val candidate = extractGeminiText(resp.body)
                    ?: return@withContext GeminiFallbackResult.Failure("Gemini không trả về dữ liệu cho \"$query\".")
                val parsed = json.decodeFromString<GeminiFallbackWordPayload>(stripMarkdownFences(candidate))
                GeminiFallbackResult.Success(parsed)
            } catch (_: UnknownHostException) {
                GeminiFallbackResult.Offline("Thiết bị đang ngoại tuyến. Vui lòng bật mạng để tra cứu.")
            } catch (_: SocketTimeoutException) {
                GeminiFallbackResult.Offline("Kết nối tới Gemini quá thời gian chờ. Vui lòng thử lại.")
            } catch (e: IOException) {
                GeminiFallbackResult.Offline("Lỗi kết nối mạng (${e.message ?: "IO error"}). Vui lòng thử lại.")
            } catch (e: Throwable) {
                GeminiFallbackResult.Failure("Không thể đọc dữ liệu từ Gemini: ${e.message ?: "Lỗi định dạng"}.")
            }
        }
    }

    private fun extractGeminiText(rawEnvelope: String): String? {
        val root = json.parseToJsonElement(rawEnvelope).jsonObject
        return root["candidates"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("content")
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
    }

    private fun buildGeminiRequest(prompt: String): String = buildJsonObject {
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", prompt) })
                })
            })
        })
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("temperature", 0.25)
        }
    }.toString()

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun normalizeAudioUrl(raw: String?): String? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        return if (cleaned.startsWith("//")) "https:$cleaned" else cleaned
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val el = this[key] ?: return null
        return runCatching { el.jsonPrimitive.content }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun createOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        private fun defaultGetExecutor(): OnlineHttpGetExecutor {
            val client = createOkHttpClient()
            return OnlineHttpGetExecutor { url ->
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    HttpPostResponse(
                        statusCode = resp.code,
                        body = resp.body?.string().orEmpty()
                    )
                }
            }
        }

        private fun defaultGeminiPostExecutor(): GeminiHttpExecutor {
            val client = createOkHttpClient()
            return GeminiHttpExecutor { url, jsonBody ->
                val req = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(req).execute().use { resp ->
                    HttpPostResponse(
                        statusCode = resp.code,
                        body = resp.body?.string().orEmpty()
                    )
                }
            }
        }
    }
}
