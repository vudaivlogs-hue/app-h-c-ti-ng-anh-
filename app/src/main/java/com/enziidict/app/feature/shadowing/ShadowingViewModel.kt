package com.enziidict.app.feature.shadowing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.DetailedPronunciationResult
import com.enziidict.app.core.audio.PronunciationScorer
import com.enziidict.app.core.audio.ShadowingAudioController
import com.enziidict.app.core.audio.ShadowingSpeechRecognizer
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.database.LearningProgressRepository
import com.enziidict.app.core.network.GeminiModelPresets
import com.enziidict.app.core.network.GeminiShadowingCoachService
import com.enziidict.app.core.network.SecureApiKeyStore
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.core.network.ShadowingAiFeedback
import com.enziidict.app.core.network.ShadowingAiOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ShadowingUiState(
    val selectedCategory: ShadowingCategory = ShadowingCategory.DAILY,
    val categoryItems: List<ShadowingItem> = ShadowingPresets.ALL.filter { it.category == ShadowingCategory.DAILY },
    val activeItem: ShadowingItem = ShadowingPresets.ALL.first(),
    val accent: TtsAccent = TtsAccent.US,
    val speed: ShadowingSpeed = ShadowingSpeed.NORMAL,
    val isLoopEnabled: Boolean = false,
    val autoRecordAfterListen: Boolean = false,
    val showVietnamese: Boolean = true,
    val isPlayingModel: Boolean = false,
    val isComparingSequence: Boolean = false,
    val countdownSec: Int? = null,
    val isRecording: Boolean = false,
    val isPlayingUserAudio: Boolean = false,
    val recordingAmplitude: Float = 0f,
    val recordingDurationMs: Long = 0L,
    val hasLatestTake: Boolean = false,
    val hasBestTake: Boolean = false,
    val hasApiKey: Boolean = false,
    val isSpeechListening: Boolean = false,
    val speechRmsAmplitude: Float = 0f,
    val partialSpeechText: String = "",
    val recognizedTranscript: String = "",
    val wordDiffResult: DetailedPronunciationResult? = null,
    val isEvaluatingAi: Boolean = false,
    val aiFeedback: ShadowingAiFeedback? = null,
    val practiceLog: ShadowingPracticeLog? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ShadowingViewModel @Inject constructor(
    private val ttsManager: TtsManager,
    private val audioController: ShadowingAudioController,
    private val speechRecognizer: ShadowingSpeechRecognizer,
    private val aiCoachService: GeminiShadowingCoachService,
    private val historyStore: ShadowingHistoryStore,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val dictionaryRepository: DictionaryRepository,
    private val progressRepository: LearningProgressRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val categoryFlow = MutableStateFlow(ShadowingCategory.DAILY)
    private val activeItemFlow = MutableStateFlow(ShadowingPresets.ALL.first())
    private val accentFlow = MutableStateFlow(TtsAccent.US)
    private val speedFlow = MutableStateFlow(ShadowingSpeed.NORMAL)
    private val loopFlow = MutableStateFlow(false)
    private val autoRecordFlow = MutableStateFlow(false)
    private val showViFlow = MutableStateFlow(true)
    private val isPlayingModelFlow = MutableStateFlow(false)
    private val isComparingFlow = MutableStateFlow(false)
    private val countdownFlow = MutableStateFlow<Int?>(null)
    private val latestTakeFlow = MutableStateFlow<File?>(null)
    private val bestTakeFlow = MutableStateFlow<File?>(
        audioController.getBestTakeFile(ShadowingPresets.ALL.first().id)
    )
    private val partialSpeechFlow = MutableStateFlow("")
    private val recognizedTranscriptFlow = MutableStateFlow("")
    private val wordDiffFlow = MutableStateFlow<DetailedPronunciationResult?>(null)
    private val isEvaluatingAiFlow = MutableStateFlow(false)
    private val aiFeedbackFlow = MutableStateFlow<ShadowingAiFeedback?>(null)
    private val statusFlow = MutableStateFlow<String?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    private var sequenceJob: Job? = null
    private var latestGeminiModel: String = GeminiModelPresets.FLASH_25

    private val prefsSnapshotFlow = combine(
        accentFlow,
        speedFlow,
        loopFlow,
        autoRecordFlow,
        showViFlow
    ) { accent, speed, loop, autoRec, showVi ->
        PrefsSnapshot(accent, speed, loop, autoRec, showVi)
    }

    private val playbackStateFlow = combine(
        isPlayingModelFlow,
        isComparingFlow,
        countdownFlow,
        latestTakeFlow,
        bestTakeFlow
    ) { playingModel, comparing, countdown, latest, best ->
        PlaybackSnapshot(playingModel, comparing, countdown, latest, best)
    }

    private val audioEngineFlow = combine(
        audioController.isRecording,
        audioController.isPlayingUserAudio,
        audioController.recordingAmplitude,
        audioController.recordingDurationMs,
        speechRecognizer.isListening,
        speechRecognizer.rmsAmplitude
    ) { values ->
        AudioEngineSnapshot(
            isRecording = values[0] as Boolean,
            isPlayingUserAudio = values[1] as Boolean,
            recordingAmplitude = values[2] as Float,
            recordingDurationMs = values[3] as Long,
            isSpeechListening = values[4] as Boolean,
            speechRmsAmplitude = values[5] as Float
        )
    }

    private val aiEvalSnapshotFlow = combine(
        partialSpeechFlow,
        recognizedTranscriptFlow,
        wordDiffFlow,
        isEvaluatingAiFlow,
        aiFeedbackFlow
    ) { partial, recognized, diff, loading, feedback ->
        AiEvalSnapshot(partial, recognized, diff, loading, feedback)
    }

    val uiState: StateFlow<ShadowingUiState> = combine(
        categoryFlow,
        activeItemFlow,
        prefsSnapshotFlow,
        playbackStateFlow,
        audioEngineFlow,
        aiEvalSnapshotFlow,
        historyStore.logsFlow,
        secureApiKeyStore.apiKeyFlow,
        settingsDataStore.settingsFlow,
        statusFlow,
        errorFlow
    ) { values ->
        val category = values[0] as ShadowingCategory
        val item = values[1] as ShadowingItem
        val prefs = values[2] as PrefsSnapshot
        val playback = values[3] as PlaybackSnapshot
        val engine = values[4] as AudioEngineSnapshot
        val eval = values[5] as AiEvalSnapshot
        @Suppress("UNCHECKED_CAST")
        val logs = values[6] as Map<String, ShadowingPracticeLog>
        val apiKey = values[7] as String
        val settings = values[8] as com.enziidict.app.core.network.UserSettings
        val status = values[9] as String?
        val error = values[10] as String?

        latestGeminiModel = settings.geminiModel

        ShadowingUiState(
            selectedCategory = category,
            categoryItems = ShadowingPresets.ALL.filter { it.category == category },
            activeItem = item,
            accent = prefs.accent,
            speed = prefs.speed,
            isLoopEnabled = prefs.loop,
            autoRecordAfterListen = prefs.autoRecord,
            showVietnamese = prefs.showVi,
            isPlayingModel = playback.isPlayingModel,
            isComparingSequence = playback.isComparing,
            countdownSec = playback.countdownSec,
            isRecording = engine.isRecording,
            isPlayingUserAudio = engine.isPlayingUserAudio,
            recordingAmplitude = engine.recordingAmplitude,
            recordingDurationMs = engine.recordingDurationMs,
            hasLatestTake = playback.latestTake != null && playback.latestTake.exists(),
            hasBestTake = playback.bestTake != null && playback.bestTake.exists(),
            hasApiKey = apiKey.isNotBlank(),
            isSpeechListening = engine.isSpeechListening,
            speechRmsAmplitude = engine.speechRmsAmplitude,
            partialSpeechText = eval.partialSpeechText,
            recognizedTranscript = eval.recognizedTranscript,
            wordDiffResult = eval.wordDiffResult,
            isEvaluatingAi = eval.isEvaluatingAi,
            aiFeedback = eval.aiFeedback,
            practiceLog = logs[item.id],
            statusMessage = status,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShadowingUiState())

    fun loadExternalItem(item: ShadowingItem) {
        resetItemSession(item)
    }

    fun selectCategory(category: ShadowingCategory) {
        categoryFlow.value = category
        val firstInCat = ShadowingPresets.ALL.firstOrNull { it.category == category } ?: return
        resetItemSession(firstInCat)
    }

    fun selectItem(item: ShadowingItem) {
        resetItemSession(item)
    }

    private fun resetItemSession(item: ShadowingItem) {
        stopAllPlaybackAndCountdown()
        speechRecognizer.stopAndDestroy()
        audioController.releaseAll()
        activeItemFlow.value = item
        latestTakeFlow.value = null
        bestTakeFlow.value = audioController.getBestTakeFile(item.id)
        partialSpeechFlow.value = ""
        recognizedTranscriptFlow.value = ""
        wordDiffFlow.value = null
        aiFeedbackFlow.value = null
        isEvaluatingAiFlow.value = false
        statusFlow.value = null
        errorFlow.value = null
    }

    fun setAccent(accent: TtsAccent) {
        accentFlow.value = accent
    }

    fun setSpeed(speed: ShadowingSpeed) {
        speedFlow.value = speed
    }

    fun toggleLoop() {
        loopFlow.value = !loopFlow.value
        if (loopFlow.value && autoRecordFlow.value) {
            autoRecordFlow.value = false
        }
    }

    fun toggleAutoRecordAfterListen() {
        autoRecordFlow.value = !autoRecordFlow.value
        if (autoRecordFlow.value && loopFlow.value) {
            loopFlow.value = false
        }
    }

    fun toggleVietnamese() {
        showViFlow.value = !showViFlow.value
    }

    fun speakChunk(chunk: String) {
        stopAllPlaybackAndCountdown()
        audioController.stopPlayback()
        errorFlow.value = null
        ttsManager.speak(
            text = chunk,
            accent = accentFlow.value,
            speechRate = speedFlow.value.rate,
            onError = { err -> errorFlow.value = err }
        )
    }

    fun toggleModelAudio(hasMicPermission: Boolean, onNeedMicPermission: () -> Unit) {
        if (isPlayingModelFlow.value || countdownFlow.value != null) {
            stopAllPlaybackAndCountdown()
            return
        }
        if (audioController.isRecording.value) {
            stopRecordingNow()
        }
        speechRecognizer.stopAndDestroy()
        audioController.stopPlayback()
        errorFlow.value = null
        playModelSentenceOnce(hasMicPermission, onNeedMicPermission)
    }

    private fun playModelSentenceOnce(hasMicPermission: Boolean, onNeedMicPermission: () -> Unit) {
        val item = activeItemFlow.value
        isPlayingModelFlow.value = true
        ttsManager.speak(
            text = item.sentenceEn,
            accent = accentFlow.value,
            speechRate = speedFlow.value.rate,
            onDone = {
                isPlayingModelFlow.value = false
                if (loopFlow.value) {
                    sequenceJob?.cancel()
                    sequenceJob = viewModelScope.launch {
                        delay(750L)
                        if (isActive && loopFlow.value && !audioController.isRecording.value) {
                            playModelSentenceOnce(hasMicPermission, onNeedMicPermission)
                        }
                    }
                } else if (autoRecordFlow.value) {
                    if (hasMicPermission) {
                        startCountdownThenRecord()
                    } else {
                        onNeedMicPermission()
                    }
                }
            },
            onError = { msg ->
                isPlayingModelFlow.value = false
                errorFlow.value = msg
            }
        )
    }

    fun startCountdownThenRecord() {
        sequenceJob?.cancel()
        sequenceJob = viewModelScope.launch {
            for (sec in 3 downTo 1) {
                countdownFlow.value = sec
                audioController.playSubtleBeep(highPitch = false)
                delay(700L)
            }
            countdownFlow.value = null
            audioController.playSubtleBeep(highPitch = true)
            startRecordingNow()
        }
    }

    fun startRecordingNow() {
        stopAllPlaybackAndCountdown()
        speechRecognizer.stopAndDestroy()
        audioController.stopPlayback()
        errorFlow.value = null
        statusFlow.value = null

        val result = audioController.startRecording(activeItemFlow.value.id)
        result.onFailure { err ->
            errorFlow.value =
                "Không thể mở micro ghi âm (${err.localizedMessage ?: "Thiết bị âm thanh bận hoặc không khả dụng"})."
        }
    }

    fun stopRecordingNow() {
        val recordedFile = audioController.stopRecording()
        if (recordedFile != null) {
            latestTakeFlow.value = recordedFile
            statusFlow.value = if (secureApiKeyStore.getApiKey().isNotBlank()) {
                "Đã ghi xong! Bấm \"Phân tích AI\" để chấm trọng âm/nối âm hoặc nghe đối chiếu."
            } else {
                "Đã ghi xong! Bấm \"Giọng của bạn\" hoặc \"So sánh\" để đối chiếu (thuần offline)."
            }
        } else {
            errorFlow.value = "Đoạn ghi quá ngắn hoặc không thu được âm thanh. Hãy giữ nói ít nhất 1 giây."
        }
    }

    /**
     * Live SpeechRecognizer check: works offline (without Gemini API key) to highlight
     * CORRECT / MISPRONOUNCED / MISSED words, and enriches with Gemini AI tips if API key is configured.
     */
    fun toggleSpeechRecognitionCheck() {
        if (speechRecognizer.isListening.value) {
            speechRecognizer.stopListening()
            return
        }
        stopAllPlaybackAndCountdown()
        audioController.releaseAll()
        errorFlow.value = null
        statusFlow.value = "Đang nghe bạn đọc câu mục tiêu..."
        partialSpeechFlow.value = ""

        val currentItem = activeItemFlow.value
        speechRecognizer.startListening(
            accent = accentFlow.value,
            onPartialResult = { partial ->
                partialSpeechFlow.value = partial
            },
            onFinalResult = { spoken ->
                partialSpeechFlow.value = ""
                recognizedTranscriptFlow.value = spoken
                val detailed = PronunciationScorer.evaluateDetailed(currentItem.sentenceEn, spoken)
                wordDiffFlow.value = detailed

                historyStore.recordAttempt(
                    itemId = currentItem.id,
                    sentenceEn = currentItem.sentenceEn,
                    score = detailed.scorePercent,
                    trickyWords = detailed.trickyWords
                )
                viewModelScope.launch {
                    progressRepository.recordShadowingCompleted(detailed.scorePercent)
                }

                val apiKey = secureApiKeyStore.getApiKey()
                if (apiKey.isNotBlank()) {
                    statusFlow.value = "Khớp ${detailed.scorePercent}% — đang hỏi Gemini mẹo trọng âm & nối âm..."
                    requestAiCoaching(
                        item = currentItem,
                        spokenTranscript = spoken,
                        audioFile = null,
                        offlineScoreFallback = detailed,
                        alreadyLoggedProgress = true
                    )
                } else {
                    aiFeedbackFlow.value = null
                    statusFlow.value = "Chấm điểm offline: ${detailed.scorePercent}/100 (đã lưu tiến bộ)."
                }
            },
            onError = { msg ->
                partialSpeechFlow.value = ""
                errorFlow.value = msg
                statusFlow.value = null
            }
        )
    }

    /**
     * Evaluates the recorded .m4a audio (or recognized transcript) using Gemini BYOK API
     * for stress, linking, reduction & fluency coaching, with seamless offline fallback.
     */
    fun analyzeWithGeminiAi() {
        val item = activeItemFlow.value
        val audioFile = latestTakeFlow.value ?: bestTakeFlow.value
        val transcript = recognizedTranscriptFlow.value.takeIf { it.isNotBlank() }

        if (audioFile == null && transcript == null) {
            errorFlow.value = "Hãy ghi âm giọng của bạn hoặc bấm \"Đọc chấm điểm (ASR)\" trước khi phân tích AI."
            return
        }

        requestAiCoaching(
            item = item,
            spokenTranscript = transcript,
            audioFile = audioFile,
            offlineScoreFallback = wordDiffFlow.value
        )
    }

    private fun requestAiCoaching(
        item: ShadowingItem,
        spokenTranscript: String?,
        audioFile: File?,
        offlineScoreFallback: DetailedPronunciationResult?,
        alreadyLoggedProgress: Boolean = false
    ) {
        viewModelScope.launch {
            isEvaluatingAiFlow.value = true
            errorFlow.value = null

            val outcome = aiCoachService.evaluateShadowing(
                targetSentence = item.sentenceEn,
                spokenTranscript = spokenTranscript,
                audioFile = audioFile,
                apiKey = secureApiKeyStore.getApiKey(),
                model = latestGeminiModel
            )

            isEvaluatingAiFlow.value = false

            when (outcome) {
                is ShadowingAiOutcome.Success -> {
                    val fb = outcome.feedback
                    aiFeedbackFlow.value = fb
                    val heard = fb.recognizedTranscript.ifBlank { spokenTranscript.orEmpty() }
                    recognizedTranscriptFlow.value = heard

                    val detailed = if (heard.isNotBlank()) {
                        PronunciationScorer.evaluateDetailed(item.sentenceEn, heard)
                    } else {
                        offlineScoreFallback
                    }
                    wordDiffFlow.value = detailed

                    historyStore.recordAttempt(
                        itemId = item.id,
                        sentenceEn = item.sentenceEn,
                        score = fb.fluencyScore,
                        trickyWords = detailed?.trickyWords ?: emptyList()
                    )
                    if (!alreadyLoggedProgress) {
                        progressRepository.recordShadowingCompleted(fb.fluencyScore)
                    }
                    statusFlow.value = "AI đánh giá xong: ${fb.fluencyScore}/100 (đã lưu lịch sử)."
                }
                is ShadowingAiOutcome.OfflineFallback -> {
                    statusFlow.value = outcome.reasonVi
                }
                is ShadowingAiOutcome.ApiError -> {
                    errorFlow.value = outcome.messageVi
                }
            }
        }
    }

    fun playUserAudio(useBestTake: Boolean = false) {
        stopAllPlaybackAndCountdown()
        if (audioController.isPlayingUserAudio.value) {
            audioController.stopPlayback()
            return
        }
        val target = if (useBestTake) bestTakeFlow.value else (latestTakeFlow.value ?: bestTakeFlow.value)
        if (target == null) {
            errorFlow.value = "Chưa có bản ghi giọng của bạn để phát lại."
            return
        }
        errorFlow.value = null
        audioController.playAudioFile(
            file = target,
            onError = { msg -> errorFlow.value = msg }
        )
    }

    fun playSideBySideCompare() {
        val target = latestTakeFlow.value ?: bestTakeFlow.value
        if (target == null) {
            errorFlow.value = "Hãy ghi âm giọng của bạn trước khi so sánh với giọng mẫu."
            return
        }
        if (isComparingFlow.value) {
            stopAllPlaybackAndCountdown()
            audioController.stopPlayback()
            return
        }
        stopAllPlaybackAndCountdown()
        audioController.stopPlayback()
        errorFlow.value = null
        isComparingFlow.value = true
        isPlayingModelFlow.value = true
        statusFlow.value = "Đang phát: 1/2 Giọng mẫu..."

        ttsManager.speak(
            text = activeItemFlow.value.sentenceEn,
            accent = accentFlow.value,
            speechRate = speedFlow.value.rate,
            onDone = {
                isPlayingModelFlow.value = false
                sequenceJob?.cancel()
                sequenceJob = viewModelScope.launch {
                    delay(450L)
                    if (!isActive || !isComparingFlow.value) return@launch
                    statusFlow.value = "Đang phát: 2/2 Giọng của bạn..."
                    audioController.playAudioFile(
                        file = target,
                        onCompleted = {
                            isComparingFlow.value = false
                            statusFlow.value = "Hoàn tất đối chiếu Giọng mẫu ↔ Giọng của bạn."
                        },
                        onError = { msg ->
                            isComparingFlow.value = false
                            errorFlow.value = msg
                        }
                    )
                }
            },
            onError = { msg ->
                isPlayingModelFlow.value = false
                isComparingFlow.value = false
                errorFlow.value = msg
            }
        )
    }

    fun saveBestTake() {
        val source = latestTakeFlow.value
        if (source == null || !source.exists()) {
            errorFlow.value = "Hãy thu âm một lượt mới trước khi lưu bản tốt nhất."
            return
        }
        audioController.saveAsBestTake(activeItemFlow.value.id, source)
            .onSuccess { dest ->
                bestTakeFlow.value = dest
                errorFlow.value = null
                statusFlow.value = "Đã lưu bản thu tốt nhất cho câu này!"
                viewModelScope.launch { settingsDataStore.recordStudyActivity() }
            }
            .onFailure { err ->
                errorFlow.value = "Không thể lưu bản thu: ${err.localizedMessage ?: "Lỗi lưu tệp"}"
            }
    }

    fun saveCurrentItemToSrs() {
        val item = activeItemFlow.value
        val phraseKey = item.keywordForSrs.ifBlank { item.sentenceEn }
        viewModelScope.launch {
            val folderName = "Luyện Shadowing"
            settingsDataStore.addCustomFolder(folderName)
            val inserted = dictionaryRepository.savePhraseToFolder(
                word = phraseKey,
                pos = "shadowing",
                meaningVi = item.meaningVi,
                folderName = folderName
            )
            if (inserted) {
                progressRepository.recordWordSaved()
            }
            statusFlow.value = if (inserted) {
                "Đã lưu \"$phraseKey\" vào Sổ tay SRS ($folderName)"
            } else {
                "\"$phraseKey\" đã có sẵn trong Sổ tay ($folderName)"
            }
        }
    }

    fun releaseOnExit() {
        stopAllPlaybackAndCountdown()
        speechRecognizer.stopAndDestroy()
        audioController.releaseAll()
    }

    override fun onCleared() {
        releaseOnExit()
        super.onCleared()
    }

    private fun stopAllPlaybackAndCountdown() {
        sequenceJob?.cancel()
        sequenceJob = null
        countdownFlow.value = null
        isPlayingModelFlow.value = false
        isComparingFlow.value = false
        ttsManager.stop()
    }

    private data class PrefsSnapshot(
        val accent: TtsAccent,
        val speed: ShadowingSpeed,
        val loop: Boolean,
        val autoRecord: Boolean,
        val showVi: Boolean
    )

    private data class PlaybackSnapshot(
        val isPlayingModel: Boolean,
        val isComparing: Boolean,
        val countdownSec: Int?,
        val latestTake: File?,
        val bestTake: File?
    )

    private data class AudioEngineSnapshot(
        val isRecording: Boolean,
        val isPlayingUserAudio: Boolean,
        val recordingAmplitude: Float,
        val recordingDurationMs: Long,
        val isSpeechListening: Boolean,
        val speechRmsAmplitude: Float
    )

    private data class AiEvalSnapshot(
        val partialSpeechText: String,
        val recognizedTranscript: String,
        val wordDiffResult: DetailedPronunciationResult?,
        val isEvaluatingAi: Boolean,
        val aiFeedback: ShadowingAiFeedback?
    )
}
