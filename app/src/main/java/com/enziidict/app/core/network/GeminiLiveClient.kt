package com.enziidict.app.core.network

import android.util.Base64
import com.enziidict.app.core.model.AiVoice
import com.enziidict.app.core.model.SpeakingLevel
import com.enziidict.app.core.model.SpeakingTopic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GeminiLiveEvent {
    data object Connected : GeminiLiveEvent
    data object SetupComplete : GeminiLiveEvent
    data class InputTranscript(val text: String) : GeminiLiveEvent
    data class OutputTranscript(val text: String) : GeminiLiveEvent
    data class AudioChunk(val pcm24kBytes: ByteArray) : GeminiLiveEvent
    data object Interrupted : GeminiLiveEvent
    data object TurnComplete : GeminiLiveEvent
    data class Error(val message: String) : GeminiLiveEvent
    data object Disconnected : GeminiLiveEvent
}

@Singleton
class GeminiLiveClient @Inject constructor(
    private val json: Json
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep WebSocket stream open
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var webSocket: WebSocket? = null

    private val setupCompleted = AtomicBoolean(false)

    val isReadyForAudio: Boolean
        get() = webSocket != null && setupCompleted.get()

    private val _events = MutableSharedFlow<GeminiLiveEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<GeminiLiveEvent> = _events.asSharedFlow()

    fun connect(
        apiKey: String,
        model: String,
        topic: SpeakingTopic,
        level: SpeakingLevel,
        voice: AiVoice,
        customTopicPrompt: String = ""
    ) {
        disconnect()
        val cleanedKey = apiKey.trim()
        if (cleanedKey.isEmpty()) {
            _events.tryEmit(GeminiLiveEvent.Error("Chưa nhập Gemini API Key."))
            return
        }

        val normalizedModel = GeminiModelCatalogService.normalizeFullModelName(model)
            .ifEmpty { GeminiModelPresets.DEFAULT_LIVE_MODEL }
        val encodedKey = URLEncoder.encode(cleanedKey, "UTF-8")
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$encodedKey"
        val request = Request.Builder().url(url).build()

        setupCompleted.set(false)
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (ws !== webSocket) return
                _events.tryEmit(GeminiLiveEvent.Connected)
                val setupPayload = buildSetupPayload(
                    model = normalizedModel,
                    topic = topic,
                    level = level,
                    voice = voice,
                    customTopicPrompt = customTopicPrompt
                )
                ws.send(setupPayload)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (ws !== webSocket) return
                parseServerMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // Gemini Live API sends BidiGenerateContentServerMessage envelopes as UTF-8 binary frames.
                if (ws !== webSocket) return
                parseServerMessage(bytes.utf8())
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (ws !== webSocket) return
                setupCompleted.set(false)
                val detail = response?.message?.takeIf { it.isNotBlank() }
                    ?: t.message
                    ?: "Lỗi kết nối WebSocket"
                _events.tryEmit(GeminiLiveEvent.Error(detail))
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                if (ws !== webSocket) return
                setupCompleted.set(false)
                if (code != 1000) {
                    val detail = reason.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    _events.tryEmit(GeminiLiveEvent.Error("Gemini Live đóng kết nối (mã $code$detail)"))
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (ws !== webSocket) return
                setupCompleted.set(false)
                if (code != 1000) {
                    val detail = reason.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    _events.tryEmit(GeminiLiveEvent.Error("Gemini Live đã đóng (mã $code$detail)"))
                } else {
                    _events.tryEmit(GeminiLiveEvent.Disconnected)
                }
            }
        })
    }

    fun sendPcm16kAudioChunk(pcmBytes: ByteArray) {
        val ws = webSocket ?: return
        if (!setupCompleted.get() || pcmBytes.isEmpty()) return
        ws.send(buildAudioChunkPayload(pcmBytes))
    }

    fun sendTextTurn(text: String) {
        val ws = webSocket ?: return
        if (!setupCompleted.get() || text.isBlank()) return
        val payload = buildJsonObject {
            putJsonObject("clientContent") {
                put("turns", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", text) })
                        })
                    })
                })
                put("turnComplete", true)
            }
        }.toString()
        ws.send(payload)
    }

    fun disconnect() {
        setupCompleted.set(false)
        val ws = webSocket
        webSocket = null
        runCatching { ws?.close(1000, "Session ended") }
    }

    internal fun buildAudioChunkPayload(pcmBytes: ByteArray): String {
        val base64Audio = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        return buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonObject("audio") {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Audio)
                }
            }
        }.toString()
    }

    internal fun buildSetupPayload(
        model: String,
        topic: SpeakingTopic,
        level: SpeakingLevel,
        voice: AiVoice,
        customTopicPrompt: String
    ): String {
        val normalizedModel = GeminiModelCatalogService.normalizeFullModelName(model)
            .ifEmpty { GeminiModelPresets.DEFAULT_LIVE_MODEL }
        val extraTopic = if (topic == SpeakingTopic.FREE && customTopicPrompt.isNotBlank()) {
            " The learner wants to discuss: ${customTopicPrompt.trim()}."
        } else {
            ""
        }
        val systemPrompt = buildString {
            append("You are Enzii, a friendly, natural English conversation tutor speaking with a Vietnamese learner. ")
            append(topic.promptInstruction)
            append(extraTopic)
            append(" Learner level: ${level.titleVi} (${level.cefrHint}). ")
            append(level.instruction)
            append(" Keep the conversation flowing naturally like a real spoken phone call, avoid lecturing mid-call, and end your turn with a natural follow-up question.")
        }

        return buildJsonObject {
            putJsonObject("setup") {
                put("model", normalizedModel)
                putJsonObject("generationConfig") {
                    put("responseModalities", buildJsonArray {
                        add(JsonPrimitive("AUDIO"))
                    })
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", voice.voiceName)
                            }
                        }
                    }
                }
                putJsonObject("inputAudioTranscription") {}
                putJsonObject("outputAudioTranscription") {}
                putJsonObject("systemInstruction") {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemPrompt) })
                    })
                }
            }
        }.toString()
    }

    internal fun parseServerMessage(rawJson: String) {
        runCatching {
            val root: JsonObject = json.parseToJsonElement(rawJson).jsonObject
            root["error"]?.let { errorEl ->
                val errMsg = runCatching {
                    errorEl.jsonObject["message"]?.jsonPrimitive?.content
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: errorEl.toString()
                setupCompleted.set(false)
                _events.tryEmit(GeminiLiveEvent.Error(errMsg))
                return
            }
            if (root.containsKey("setupComplete")) {
                setupCompleted.set(true)
                _events.tryEmit(GeminiLiveEvent.SetupComplete)
            }
            val serverContent = root["serverContent"]?.jsonObject ?: return
            if (serverContent["interrupted"]?.jsonPrimitive?.content == "true") {
                _events.tryEmit(GeminiLiveEvent.Interrupted)
            }
            serverContent["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.content?.let {
                if (it.isNotBlank()) _events.tryEmit(GeminiLiveEvent.InputTranscript(it))
            }
            serverContent["outputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.content?.let {
                if (it.isNotBlank()) _events.tryEmit(GeminiLiveEvent.OutputTranscript(it))
            }
            val parts = serverContent["modelTurn"]?.jsonObject?.get("parts")?.jsonArray
            parts?.forEach { element ->
                val inlineData = element.jsonObject["inlineData"]?.jsonObject
                val b64 = inlineData?.get("data")?.jsonPrimitive?.content
                if (!b64.isNullOrEmpty()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    _events.tryEmit(GeminiLiveEvent.AudioChunk(bytes))
                }
            }
            if (serverContent["turnComplete"]?.jsonPrimitive?.content == "true") {
                _events.tryEmit(GeminiLiveEvent.TurnComplete)
            }
        }
    }
}
