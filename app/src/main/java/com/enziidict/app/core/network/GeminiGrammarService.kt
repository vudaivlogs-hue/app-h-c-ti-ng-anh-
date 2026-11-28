package com.enziidict.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GrammarRuleItem(
    val pattern: String,
    @SerialName("explanation_vi") val explanationVi: String
)

@Serializable
data class PhraseNuanceItem(
    val phrase: String,
    @SerialName("meaning_vi") val meaningVi: String,
    @SerialName("nuance_vi") val nuanceVi: String = ""
)

@Serializable
data class NaturalAlternativeItem(
    val expression: String,
    @SerialName("context_vi") val contextVi: String
)

@Serializable
data class GrammarAnalysisResult(
    @SerialName("natural_translation") val naturalTranslation: String,
    @SerialName("sentence_structure") val sentenceStructure: String = "",
    @SerialName("grammar_points") val grammarPoints: List<GrammarRuleItem> = emptyList(),
    @SerialName("phrases_and_idioms") val phrasesAndIdioms: List<PhraseNuanceItem> = emptyList(),
    @SerialName("natural_alternatives") val naturalAlternatives: List<NaturalAlternativeItem> = emptyList()
)

sealed interface GrammarAnalysisOutcome {
    data class Success(val analysis: GrammarAnalysisResult) : GrammarAnalysisOutcome
    data object MissingApiKey : GrammarAnalysisOutcome
    data class NetworkError(val messageVi: String) : GrammarAnalysisOutcome
    data class ApiError(val httpCode: Int, val messageVi: String) : GrammarAnalysisOutcome
}

data class HttpPostResponse(
    val statusCode: Int,
    val body: String
)

fun interface GeminiHttpExecutor {
    @Throws(IOException::class)
    fun postJson(url: String, jsonBody: String): HttpPostResponse
}

@Singleton
class GeminiGrammarService(
    private val json: Json,
    private val httpExecutor: GeminiHttpExecutor,
    private val modelCatalogService: GeminiModelCatalogService? = null,
    private val settingsDataStore: SettingsDataStore? = null
) {
    constructor(
        json: Json,
        httpExecutor: GeminiHttpExecutor
    ) : this(json, httpExecutor, null, null)

    @Inject
    constructor(
        json: Json,
        modelCatalogService: GeminiModelCatalogService,
        settingsDataStore: SettingsDataStore
    ) : this(json, defaultHttpExecutor(), modelCatalogService, settingsDataStore)

    suspend fun analyze(
        text: String,
        enToVi: Boolean,
        apiKey: String,
        model: String
    ): GrammarAnalysisOutcome {
        val cleanedKey = apiKey.trim()
        if (cleanedKey.isEmpty()) {
            return GrammarAnalysisOutcome.MissingApiKey
        }

        val cleanedText = text.trim()
        if (cleanedText.isEmpty()) {
            return GrammarAnalysisOutcome.ApiError(400, "Vui lòng nhập nội dung cần phân tích.")
        }

        val initialModel = GeminiModelCatalogService.normalizeModelId(model)
            .ifEmpty { GeminiModelPresets.FLASH_25 }
        val requestBody = buildRequestJson(cleanedText, enToVi)

        return withContext(Dispatchers.IO) {
            try {
                var attemptedModel = initialModel
                var response = postGenerateContent(attemptedModel, cleanedKey, requestBody)

                if (response.statusCode == 404 && modelCatalogService != null) {
                    val discovered = modelCatalogService.discoverModels(cleanedKey, forceRefresh = true)
                    val fallbackModel = (discovered as? ModelDiscoveryOutcome.Success)
                        ?.catalog
                        ?.recommendedTextModel
                        ?.let { GeminiModelCatalogService.normalizeModelId(it) }
                        ?.takeIf { it.isNotEmpty() && it != attemptedModel }

                    if (fallbackModel != null) {
                        val retryResponse = postGenerateContent(fallbackModel, cleanedKey, requestBody)
                        if (retryResponse.statusCode in 200..299) {
                            settingsDataStore?.saveGeminiModel(fallbackModel)
                        }
                        attemptedModel = fallbackModel
                        response = retryResponse
                    }
                }

                val googleDetail = GeminiModelCatalogService.extractApiErrorMessage(json, response.body)
                    ?.let { " Chi tiết: ${GeminiModelCatalogService.truncateDetail(it)}" }
                    .orEmpty()

                when {
                    response.statusCode == 400 || response.statusCode == 401 || response.statusCode == 403 -> {
                        GrammarAnalysisOutcome.ApiError(
                            response.statusCode,
                            "API Key không hợp lệ hoặc không có quyền truy cập model \"$attemptedModel\" (HTTP ${response.statusCode}).$googleDetail"
                        )
                    }
                    response.statusCode == 404 -> {
                        GrammarAnalysisOutcome.ApiError(
                            404,
                            "Model \"$attemptedModel\" không còn khả dụng (HTTP 404). Hãy vào Cài đặt → bấm \"Kiểm tra Key & Tải model\" để chọn model mới.$googleDetail"
                        )
                    }
                    response.statusCode == 429 -> {
                        GrammarAnalysisOutcome.ApiError(
                            429,
                            "API Key đã vượt hạn mức tạm thời (HTTP 429). Vui lòng thử lại sau ít phút hoặc đổi model.$googleDetail"
                        )
                    }
                    response.statusCode !in 200..299 -> {
                        GrammarAnalysisOutcome.ApiError(
                            response.statusCode,
                            "Lỗi máy chủ Gemini (HTTP ${response.statusCode}).$googleDetail"
                        )
                    }
                    else -> parseGeminiResponse(response.body)
                }
            } catch (_: UnknownHostException) {
                GrammarAnalysisOutcome.NetworkError(
                    "Không có kết nối Internet. Bạn vẫn có thể dùng Dịch Offline (ML Kit) ở phía trên."
                )
            } catch (_: SocketTimeoutException) {
                GrammarAnalysisOutcome.NetworkError(
                    "Kết nối tới Gemini quá thời gian chờ. Vui lòng kiểm tra mạng và thử lại."
                )
            } catch (e: IOException) {
                GrammarAnalysisOutcome.NetworkError(
                    "Mất kết nối mạng (${e.message ?: "IO error"}). Bạn vẫn có thể dùng Dịch Offline (ML Kit)."
                )
            }
        }
    }

    private fun postGenerateContent(modelId: String, apiKey: String, requestBody: String): HttpPostResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
        return httpExecutor.postJson(url, requestBody)
    }

    internal fun parseGeminiResponse(rawEnvelope: String): GrammarAnalysisOutcome {
        return runCatching {
            val root = json.parseToJsonElement(rawEnvelope).jsonObject
            val candidateText = root["candidates"]
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
                ?: return GrammarAnalysisOutcome.ApiError(500, "Gemini không trả về nội dung phân tích.")

            val cleanedJson = stripMarkdownFences(candidateText)
            val parsed = json.decodeFromString<GrammarAnalysisResult>(cleanedJson)
            GrammarAnalysisOutcome.Success(parsed)
        }.getOrElse { err ->
            GrammarAnalysisOutcome.ApiError(
                500,
                "Không thể đọc kết quả phân tích từ Gemini: ${err.message ?: "Định dạng không hợp lệ"}."
            )
        }
    }

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun buildRequestJson(text: String, enToVi: Boolean): String {
        val direction = if (enToVi) "English to Vietnamese" else "Vietnamese to English"
        val prompt = """
            You are an expert English-Vietnamese linguist and teacher inside the Enzii Dict app.
            Analyze the following text ($direction):
            "$text"

            Respond ONLY with valid JSON matching this exact schema (explanations in clear, concise Vietnamese):
            {
              "natural_translation": "Bản dịch tự nhiên nhất theo ngữ cảnh",
              "sentence_structure": "Tóm tắt cấu trúc câu / thì ngữ pháp chính",
              "grammar_points": [
                { "pattern": "Cấu trúc / cụm ngữ pháp", "explanation_vi": "Giải thích dễ hiểu bằng tiếng Việt" }
              ],
              "phrases_and_idioms": [
                { "phrase": "Cụm từ / idiom / từ đáng chú ý", "meaning_vi": "Nghĩa tiếng Việt", "nuance_vi": "Sắc thái sử dụng (trang trọng / thân mật / lưu ý)" }
              ],
              "natural_alternatives": [
                { "expression": "Cách diễn đạt tiếng Anh tự nhiên hơn", "context_vi": "Ngữ cảnh sử dụng phù hợp" }
              ]
            }
        """.trimIndent()

        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.3)
            }
        }.toString()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpExecutor(): GeminiHttpExecutor {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            return GeminiHttpExecutor { url, jsonBody ->
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { resp ->
                    HttpPostResponse(
                        statusCode = resp.code,
                        body = resp.body?.string().orEmpty()
                    )
                }
            }
        }
    }
}
