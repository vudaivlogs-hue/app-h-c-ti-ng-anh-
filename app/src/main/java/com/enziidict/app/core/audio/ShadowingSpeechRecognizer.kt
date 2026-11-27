package com.enziidict.app.core.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight Android SpeechRecognizer wrapper for offline/on-device Shadowing
 * speech-to-text evaluation when Gemini API Key is not configured or offline.
 */
@Singleton
class ShadowingSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsAmplitude = MutableStateFlow(0f)
    val rmsAmplitude: StateFlow<Float> = _rmsAmplitude.asStateFlow()

    fun startListening(
        accent: TtsAccent,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopAndDestroy()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Thiết bị không hỗ trợ SpeechRecognizer. Bạn vẫn có thể ghi âm và dùng Gemini AI hoặc tự nghe đối chiếu.")
            return
        }

        runCatching {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _rmsAmplitude.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    _rmsAmplitude.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _rmsAmplitude.value = 0f
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Không nhận diện được lời nói rõ ràng. Hãy thử nói chậm và rõ hơn."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Hết thời gian chờ giọng nói. Hãy bấm thử lại."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Nhận diện giọng nói cần mạng hoặc gói Speech offline của Google."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thiếu quyền Micro (RECORD_AUDIO)."
                        else -> "Lỗi nhận diện giọng nói (mã $error)."
                    }
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _rmsAmplitude.value = 0f
                    val best = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (best.isBlank()) {
                        onError("Không nghe rõ nội dung câu nói. Hãy thử lại.")
                    } else {
                        onFinalResult(best)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        onPartialResult(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val langTag = if (accent == TtsAccent.UK) Locale.UK.toLanguageTag() else Locale.US.toLanguageTag()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer = recognizer
            _isListening.value = true
            recognizer.startListening(intent)
        }.onFailure { err ->
            stopAndDestroy()
            onError("Không thể khởi chạy SpeechRecognizer: ${err.localizedMessage ?: "Lỗi thiết bị"}")
        }
    }

    fun stopListening() {
        _isListening.value = false
        _rmsAmplitude.value = 0f
        runCatching { speechRecognizer?.stopListening() }
    }

    fun stopAndDestroy() {
        _isListening.value = false
        _rmsAmplitude.value = 0f
        val rec = speechRecognizer
        speechRecognizer = null
        if (rec != null) {
            runCatching { rec.cancel() }
            runCatching { rec.destroy() }
        }
    }
}
