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
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ShadowingAiFeedback(
    @SerialName("recognized_transcript") val recognizedTranscript: String = "",
    @SerialName("fluency_score") val fluencyScore: Int = 0,
    @SerialName("summary_vi") val summaryVi: String = "",
    @SerialName("stress_notes") val stressNotes: List<String> = emptyList(),
    @SerialName("linking_and_rhythm_tips") val linkingAndRhythmTips: List<String> = emptyList()
)

sealed interface ShadowingAiOutcome {
    data class Success(val feedback: ShadowingAiFeedback) : ShadowingAiOutcome
    data class OfflineFallback(val reasonVi: String) : ShadowingAiOutcome
    data class ApiError(val httpCode: Int, val messageVi: String) : ShadowingAiOutcome
}

@Singleton
class GeminiShadowingCoachService(
    private val json: Json,
    private val httpExecutor: GeminiHttpExecutor
) {
    @Inject
    constructor(json: Json) : this(json, defaultExecutor())

    suspend fun evaluateShadowing(
        targetSentence: String,
        spokenTranscript: String?,
        audioFile: File?,
        apiKey: String,
        model: String
    ): ShadowingAiOutcome {
        val cleanedKey = apiKey.trim()
        if (cleanedKey.isEmpty()) {
            return ShadowingAiOutcome.OfflineFallback(
                "Chưa nhập Gemini API Key — đang dùng chế độ chấm điểm offline."
            )
        }

        val hasAudio = audioFile != null && audioFile.exists() && audioFile.length() > 0L
        val cleanedTranscript = spokenTranscript?.trim().orEmpty()
        if (!hasAudio && cleanedTranscript.isEmpty()) {
            return ShadowingAiOutcome.ApiError(400, "Chưa có bản ghi âm hoặc lời nhận diện để phân tích.")
        }

        val selectedModel = model.trim().ifEmpty { GeminiModelPresets.FLASH_25 }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$cleanedKey"

        return withContext(Dispatchers.IO) {
            try {
                val base64Audio = if (hasAudio) {
                    Base64.getEncoder().encodeToString(audioFile!!.readBytes())
                } else {
                    null
                }
                val requestBody = buildPayload(
                    targetSentence = targetSentence,
                    spokenTranscript = cleanedTranscript,
                    audioBase64 = base64Audio
                )
                val response = httpExecutor.postJson(url, requestBody)
                when {
                    response.statusCode in listOf(400, 401, 403) -> ShadowingAiOutcome.ApiError(
                        response.statusCode,
                        "API Key không hợp lệ hoặc không dùng được model \"$selectedModel\" (HTTP ${response.statusCode})."
                    )
                    response.statusCode == 429 -> ShadowingAiOutcome.ApiError(
                        429,
                        "API Key tạm hết hạn mức (HTTP 429). Bạn vẫn có thể tiếp tục luyện offline."
                    )
                    response.statusCode !in 200..299 -> ShadowingAiOutcome.ApiError(
                        response.statusCode,
                        "Lỗi máy chủ Gemini (HTTP ${response.statusCode})."
                    )
                    else -> parseEnvelope(response.body, fallbackTranscript = cleanedTranscript)
                }
            } catch (_: UnknownHostException) {
                ShadowingAiOutcome.OfflineFallback(
                    "Đang mất kết nối mạng — chuyển sang đánh giá thuần offline."
                )
            } catch (_: SocketTimeoutException) {
                ShadowingAiOutcome.OfflineFallback(
                    "Kết nối Gemini quá thời gian chờ — dùng kết quả đánh giá offline."
                )
            } catch (e: IOException) {
                ShadowingAiOutcome.OfflineFallback(
                    "Không kết nối được mạng (${e.message ?: "IO error"}) — dùng chế độ offline."
                )
            }
        }
    }

    internal fun parseEnvelope(rawEnvelope: String, fallbackTranscript: String): ShadowingAiOutcome {
        return runCatching {
            val root = json.parseToJsonElement(rawEnvelope).jsonObject
            val text = root["candidates"]
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
                ?: return ShadowingAiOutcome.ApiError(500, "Gemini không trả về phản hồi phát âm.")

            val cleaned = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val parsed = json.decodeFromString<ShadowingAiFeedback>(cleaned)
            val normalized = parsed.copy(
                recognizedTranscript = parsed.recognizedTranscript.ifBlank { fallbackTranscript },
                fluencyScore = parsed.fluencyScore.coerceIn(0, 100)
            )
            ShadowingAiOutcome.Success(normalized)
        }.getOrElse { err ->
            ShadowingAiOutcome.ApiError(
                500,
                "Không thể đọc phản hồi AI: ${err.message ?: "JSON không hợp lệ"}."
            )
        }
    }

    private fun buildPayload(
        targetSentence: String,
        spokenTranscript: String,
        audioBase64: String?
    ): String {
        val transcriptContext = if (spokenTranscript.isNotBlank()) {
            "ASR transcript from device: \"$spokenTranscript\""
        } else {
            "Transcribe what the learner actually spoke from the attached audio."
        }

        val prompt = """
            You are an expert English pronunciation and shadowing coach helping a Vietnamese learner.
            Target sentence: "$targetSentence"
            $transcriptContext

            Evaluate the learner's pronunciation match, word stress, linking, reduction, and natural rhythm.
            Respond ONLY with valid JSON matching this schema (all explanations in clear, concise Vietnamese):
            {
              "recognized_transcript": "Exact English words the learner actually said",
              "fluency_score": 84,
              "summary_vi": "Nhận xét ngắn gọn (1-2 câu) về độ rõ, nhịp điệu và độ tự nhiên",
              "stress_notes": [
                "Từ/cụm dễ đọc sai hoặc cần nhấn trọng âm rõ hơn (VD: 'schedule' nhấn âm 1)"
              ],
              "linking_and_rhythm_tips": [
                "Mẹo nối âm (linking), giảm âm (reduction) hoặc ngắt nhịp cho câu này (VD: 'pick it up' nối thành /pɪ-kɪ-tʌp/)"
              ]
            }
        """.trimIndent()

        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                        if (audioBase64 != null) {
                            add(buildJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "audio/mp4")
                                    put("data", audioBase64)
                                }
                            })
                        }
                    })
                })
            })
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.25)
            }
        }.toString()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultExecutor(): GeminiHttpExecutor {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
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
