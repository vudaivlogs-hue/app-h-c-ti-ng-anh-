package com.enziidict.app.core.network

import com.enziidict.app.core.model.SpeakingLevel
import com.enziidict.app.core.model.SpeakingTopic
import com.enziidict.app.data.local.entity.SpeakingMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

sealed interface SpeakingTurnOutcome {
    data class Success(val replyText: String) : SpeakingTurnOutcome
    data object MissingApiKey : SpeakingTurnOutcome
    data class NetworkError(val messageVi: String) : SpeakingTurnOutcome
    data class ApiError(val httpCode: Int, val messageVi: String) : SpeakingTurnOutcome
}

@Singleton
class GeminiSpeakingTurnService(
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
    ) : this(json, defaultExecutor(), modelCatalogService, settingsDataStore)

    suspend fun generateReply(
        topic: SpeakingTopic,
        level: SpeakingLevel,
        customTopicPrompt: String,
        history: List<SpeakingMessage>,
        apiKey: String,
        model: String
    ): SpeakingTurnOutcome {
        val cleanedKey = apiKey.trim()
        if (cleanedKey.isEmpty()) {
            return SpeakingTurnOutcome.MissingApiKey
        }

        val initialModel = GeminiModelCatalogService.normalizeModelId(model)
            .ifEmpty { GeminiModelPresets.FLASH_25 }
        val payload = buildConversationPayload(topic, level, customTopicPrompt, history)

        return withContext(Dispatchers.IO) {
            try {
                var attemptedModel = initialModel
                var response = postGenerateContent(attemptedModel, cleanedKey, payload)

                if (response.statusCode == 404 && modelCatalogService != null) {
                    val discovered = modelCatalogService.discoverModels(cleanedKey, forceRefresh = true)
                    val fallbackModel = (discovered as? ModelDiscoveryOutcome.Success)
                        ?.catalog
                        ?.recommendedTextModel
                        ?.let { GeminiModelCatalogService.normalizeModelId(it) }
                        ?.takeIf { it.isNotEmpty() && it != attemptedModel }

                    if (fallbackModel != null) {
                        val retryResp = postGenerateContent(fallbackModel, cleanedKey, payload)
                        if (retryResp.statusCode in 200..299) {
                            settingsDataStore?.saveGeminiModel(fallbackModel)
                        }
                        attemptedModel = fallbackModel
                        response = retryResp
                    }
                }

                val googleDetail = GeminiModelCatalogService.extractApiErrorMessage(json, response.body)
                    ?.let { " Chi tiết: ${GeminiModelCatalogService.truncateDetail(it)}" }
                    .orEmpty()

                when {
                    response.statusCode in listOf(400, 401, 403) -> SpeakingTurnOutcome.ApiError(
                        response.statusCode,
                        "API Key không hợp lệ hoặc không truy cập được model \"$attemptedModel\" (HTTP ${response.statusCode}).$googleDetail"
                    )
                    response.statusCode == 404 -> SpeakingTurnOutcome.ApiError(
                        404,
                        "Model \"$attemptedModel\" không còn khả dụng (HTTP 404).$googleDetail"
                    )
                    response.statusCode == 429 -> SpeakingTurnOutcome.ApiError(
                        429,
                        "Vượt hạn mức Gemini tạm thời (HTTP 429).$googleDetail"
                    )
                    response.statusCode !in 200..299 -> SpeakingTurnOutcome.ApiError(
                        response.statusCode,
                        "Lỗi máy chủ Gemini (HTTP ${response.statusCode}).$googleDetail"
                    )
                    else -> parseReplyEnvelope(response.body)
                }
            } catch (_: UnknownHostException) {
                SpeakingTurnOutcome.NetworkError("Không có kết nối Internet.")
            } catch (_: SocketTimeoutException) {
                SpeakingTurnOutcome.NetworkError("Kết nối tới Gemini quá thời gian chờ.")
            } catch (e: IOException) {
                SpeakingTurnOutcome.NetworkError("Lỗi mạng (${e.message ?: "IO error"}). ")
            }
        }
    }

    internal fun buildConversationPayload(
        topic: SpeakingTopic,
        level: SpeakingLevel,
        customTopicPrompt: String,
        history: List<SpeakingMessage>
    ): String {
        val extraTopic = if (topic == SpeakingTopic.FREE && customTopicPrompt.isNotBlank()) {
            " Custom topic: ${customTopicPrompt.trim()}."
        } else {
            ""
        }
        val systemInstruction = buildString {
            append("You are Enzii, a warm, natural English conversation tutor speaking with a Vietnamese learner. ")
            append(topic.promptInstruction)
            append(extraTopic)
            append(" Learner level: ${level.titleVi} (${level.cefrHint}). ")
            append(level.instruction)
            append(" Reply in plain spoken English (2 to 4 short sentences, no markdown, no bullet points, no asterisks) so text-to-speech reads cleanly, briefly acknowledge what the learner said, gently model better phrasing if needed, and end with one natural follow-up question.")
        }

        val recentTurns = history
            .filter { it.transcript.isNotBlank() }
            .takeLast(12)

        return buildJsonObject {
            putJsonObject("systemInstruction") {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", systemInstruction) })
                })
            }
            put("contents", buildJsonArray {
                if (recentTurns.isEmpty()) {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("text", "Hello! Please start the roleplay conversation with a friendly greeting.")
                            })
                        })
                    })
                } else {
                    recentTurns.forEach { msg ->
                        val role = if (msg.speaker.equals("USER", ignoreCase = true)) "user" else "model"
                        add(buildJsonObject {
                            put("role", role)
                            put("parts", buildJsonArray {
                                add(buildJsonObject { put("text", msg.transcript.trim()) })
                            })
                        })
                    }
                }
            })
            putJsonObject("generationConfig") {
                put("temperature", 0.7)
                put("maxOutputTokens", 180)
            }
        }.toString()
    }

    internal fun parseReplyEnvelope(rawEnvelope: String): SpeakingTurnOutcome {
        return runCatching {
            val root = json.parseToJsonElement(rawEnvelope).jsonObject
            val reply = root["candidates"]
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
                ?.replace("*", "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return SpeakingTurnOutcome.ApiError(500, "Gemini không trả về lời thoại.")
            SpeakingTurnOutcome.Success(reply)
        }.getOrElse { err ->
            SpeakingTurnOutcome.ApiError(500, "Không đọc được phản hồi AI: ${err.message ?: "Lỗi định dạng"}.")
        }
    }

    private fun postGenerateContent(modelId: String, apiKey: String, payload: String): HttpPostResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
        return httpExecutor.postJson(url, payload)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultExecutor(): GeminiHttpExecutor {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
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
