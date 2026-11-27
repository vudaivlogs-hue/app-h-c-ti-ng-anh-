package com.enziidict.app.core.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class TtsAccent(val locale: Locale, val label: String) {
    UK(Locale.UK, "UK"),
    US(Locale.US, "US")
}

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val completionCallbacks = ConcurrentHashMap<String, Pair<() -> Unit, (String) -> Unit>>()

    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false

    private fun ensureInitialized(onReady: () -> Unit, onInitError: (String) -> Unit = {}) {
        if (tts != null && isReady) {
            onReady()
            return
        }
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                isReady = status == TextToSpeech.SUCCESS
                if (isReady) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            val cb = utteranceId?.let { completionCallbacks.remove(it) } ?: return
                            mainHandler.post { cb.first() }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            val cb = utteranceId?.let { completionCallbacks.remove(it) } ?: return
                            mainHandler.post { cb.second("Lỗi phát giọng mẫu TTS.") }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            val cb = utteranceId?.let { completionCallbacks.remove(it) } ?: return
                            mainHandler.post { cb.second("Lỗi phát giọng mẫu TTS (mã $errorCode).") }
                        }
                    })
                    mainHandler.post { onReady() }
                } else {
                    mainHandler.post { onInitError("Thiết bị chưa sẵn sàng bộ đọc Text-to-Speech.") }
                }
            }
        }
    }

    fun speak(
        text: String,
        accent: TtsAccent = TtsAccent.US,
        speechRate: Float = 1.0f,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        ensureInitialized(
            onReady = {
                val engine = tts ?: return@ensureInitialized
                val utteranceId = "enzii_${System.nanoTime()}"
                completionCallbacks.clear()
                if (onDone != null || onError != null) {
                    completionCallbacks[utteranceId] = Pair(
                        onDone ?: {},
                        onError ?: {}
                    )
                }
                engine.language = accent.locale
                engine.setSpeechRate(speechRate)
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (result == TextToSpeech.ERROR) {
                    completionCallbacks.remove(utteranceId)
                    onError?.invoke("Không thể phát giọng mẫu TTS.")
                }
            },
            onInitError = { msg -> onError?.invoke(msg) }
        )
    }

    fun stop() {
        completionCallbacks.clear()
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        completionCallbacks.clear()
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        isReady = false
    }
}
