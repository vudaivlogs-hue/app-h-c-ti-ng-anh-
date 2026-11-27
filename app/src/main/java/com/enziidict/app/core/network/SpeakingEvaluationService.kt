package com.enziidict.app.core.network

import com.enziidict.app.core.model.SpeakingAnalysis
import com.enziidict.app.data.local.entity.SpeakingMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
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

sealed interface SpeakingEvaluationOutcome {
    data class Success(val analysis: SpeakingAnalysis, val serializedJson: String) : SpeakingEvaluationOutcome
    data object MissingApiKey : SpeakingEvaluationOutcome
    data object EmptyTranscript : SpeakingEvaluationOutcome
    data class NetworkError(val messageVi: String) : SpeakingEvaluationOutcome
    data class ApiError(val httpCode: Int, val messageVi: String) : SpeakingEvaluationOutcome
}

@Singleton
class SpeakingEvaluationService(
    private val json: Json,
    private val httpExecutor: GeminiHttpExecutor
) {
    @Inject
    constructor(json: Json) : this(json, defaultExecutor())

    suspend fun evaluateSession(
        topicTitle: String,
        messages: List<SpeakingMessage>,
        apiKey: String,
        model: String
    ): SpeakingEvaluationOutcome {
        val cleanedKey = apiKey.trim()
        if (cleanedKey.isEmpty()) {
            return SpeakingEvaluationOutcome.MissingApiKey
        }
        val userTurns = messages.filter { it.speaker == "USER" && it.transcript.isNotBlank() }
        if (userTurns.isEmpty()) {
            return SpeakingEvaluationOutcome.EmptyTranscript
        }

        val selectedModel = model.trim().ifEmpty { GeminiModelPresets.FLASH_25 }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$cleanedKey"
        val body = buildEvaluationPayload(topicTitle, messages)

        return withContext(Dispatchers.IO) {
            try {
                val response = httpExecutor.postJson(url, body)
                when {
                    response.statusCode in listOf(400, 401, 403) -> SpeakingEvaluationOutcome.ApiError(
                        response.statusCode,
                        "API Key không hợp lệ hoặc không truy cập được model \"$selectedModel\" (HTTP ${response.statusCode})."
                    )
                    response.statusCode == 429 -> SpeakingEvaluationOutcome.ApiError(
                        429,
                        "Vượt hạn mức Gemini tạm thời (HTTP 429). Bạn có thể bấm Phân tích lại sau ít phút."
                    )
                    response.statusCode !in 200..299 -> SpeakingEvaluationOutcome.ApiError(
                        response.statusCode,
                        "Lỗi máy chủ Gemini khi đánh giá buổi nói (HTTP ${response.statusCode})."
                    )
                    else -> parseEvaluationEnvelope(response.body)
                }
            } catch (_: UnknownHostException) {
                SpeakingEvaluationOutcome.NetworkError(
                    "Không có kết nối Internet để phân tích buổi nói. Hội thoại đã được lưu, bạn có thể phân tích lại khi có mạng."
                )
            } catch (_: SocketTimeoutException) {
                SpeakingEvaluationOutcome.NetworkError(
                    "Yêu cầu phân tích quá thời gian chờ. Vui lòng thử lại trong Lịch sử buổi nói."
                )
            } catch (e: IOException) {
                SpeakingEvaluationOutcome.NetworkError(
                    "Lỗi kết nối mạng (${e.message ?: "IO error"})."
                )
            }
        }
    }

    internal fun parseEvaluationEnvelope(rawEnvelope: String): SpeakingEvaluationOutcome {
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
                ?: return SpeakingEvaluationOutcome.ApiError(500, "Gemini không trả về dữ liệu đánh giá.")

            val cleaned = candidateText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val parsed = json.decodeFromString<SpeakingAnalysis>(cleaned)
            val normalizedJson = json.encodeToString(parsed)
            SpeakingEvaluationOutcome.Success(parsed, normalizedJson)
        }.getOrElse { err ->
            SpeakingEvaluationOutcome.ApiError(
                500,
                "Không thể đọc kết quả đánh giá buổi nói: ${err.message ?: "JSON không hợp lệ"}."
            )
        }
    }

    private fun buildEvaluationPayload(topicTitle: String, messages: List<SpeakingMessage>): String {
        val transcriptText = messages.joinToString("\n") { msg ->
            val role = if (msg.speaker == "USER") "Learner" else "Tutor"
            "$role: ${msg.transcript}"
        }

        val prompt = """
            You are an encouraging English speaking coach evaluating a Vietnamese learner's spoken conversation.
            Topic: $topicTitle

            Conversation transcript:
            $transcriptText

            Return ONLY valid JSON matching this exact schema (feedback and explanations in natural Vietnamese):
            {
              "score_out_of_10": 8,
              "overall_feedback_vi": "Nhận xét tổng quan ngắn gọn về độ tự nhiên, từ vựng và ngữ pháp của người học",
              "mistakes": [
                {
                  "original": "Câu người dùng nói chưa tự nhiên hoặc sai ngữ pháp",
                  "correction": "Câu tiếng Anh gợi ý chuẩn và tự nhiên hơn",
                  "explanation_vi": "Giải thích ngắn gọn lý do và sắc thái"
                }
              ],
              "suggested_vocabulary": [
                {
                  "word": "single word or short collocation from or relevant to the conversation",
                  "pos": "noun/verb/adjective/phrase",
                  "meaning_vi": "Nghĩa tiếng Việt ngắn gọn"
                }
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

        private fun defaultExecutor(): GeminiHttpExecutor {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
            return GeminiHttpExecutor { url, jsonBody ->
                val req = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(req).execute().use { resp ->
                    HttpPostResponse(resp.code, resp.body?.string().orEmpty())
                }
            }
        }
    }
}
