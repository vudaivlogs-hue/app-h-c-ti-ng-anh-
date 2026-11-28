package com.enziidict.app.feature.speaking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.LiveAudioManager
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.database.LearningProgressRepository
import com.enziidict.app.core.model.AiVoice
import com.enziidict.app.core.model.SpeakingAnalysis
import com.enziidict.app.core.model.SpeakingLevel
import com.enziidict.app.core.model.SpeakingMode
import com.enziidict.app.core.model.SpeakingTopic
import com.enziidict.app.core.model.SuggestedVocab
import com.enziidict.app.core.network.GeminiLiveClient
import com.enziidict.app.core.network.GeminiLiveEvent
import com.enziidict.app.core.network.OfflineMlKitTranslator
import com.enziidict.app.core.network.SecureApiKeyStore
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.core.network.SpeakingEvaluationOutcome
import com.enziidict.app.core.network.SpeakingEvaluationService
import com.enziidict.app.data.local.dao.SpeakingDao
import com.enziidict.app.data.local.entity.SpeakingMessage
import com.enziidict.app.data.local.entity.SpeakingSession
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
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class HistoryDetailUiState(
    val session: SpeakingSession,
    val messages: List<SpeakingMessage>,
    val analysis: SpeakingAnalysis?,
    val isEvaluating: Boolean = false,
    val evaluationError: String? = null,
    val savedWordsSet: Set<String> = emptySet(),
    val actionMessage: String? = null
)

data class SpeakingSessionRecapUi(
    val sessionId: Long,
    val durationSec: Int,
    val speakingMinutes: Int,
    val userTurns: Int,
    val xpEarned: Int,
    val streakAfter: Int,
    val streakIncreased: Boolean,
    val freezeUsedCount: Int,
    val goalJustCompleted: Boolean
)

data class SpeakingUiState(
    val selectedTopic: SpeakingTopic = SpeakingTopic.DAILY,
    val customTopicText: String = "",
    val selectedLevel: SpeakingLevel = SpeakingLevel.INTERMEDIATE,
    val selectedVoice: AiVoice = AiVoice.AOEDE,
    val showBilingualSubtitles: Boolean = true,
    val isInCall: Boolean = false,
    val isMuted: Boolean = false,
    val elapsedSeconds: Int = 0,
    val micAmplitude: Float = 0f,
    val aiAmplitude: Float = 0f,
    val aecEnabled: Boolean = false,
    val activeMode: SpeakingMode = SpeakingMode.FALLBACK_STT_TTS,
    val hasApiKey: Boolean = false,
    val connectionNote: String = "",
    val liveUserDraft: String = "",
    val liveAiDraft: String = "",
    val activeMessages: List<SpeakingMessage> = emptyList(),
    val subtitleTranslations: Map<Long, String> = emptyMap(),
    val pastSessions: List<SpeakingSession> = emptyList(),
    val showHistoryScreen: Boolean = false,
    val historyDetail: HistoryDetailUiState? = null,
    val sessionRecap: SpeakingSessionRecapUi? = null
)

@HiltViewModel
class SpeakingViewModel @Inject constructor(
    private val speakingDao: SpeakingDao,
    private val dictionaryRepository: DictionaryRepository,
    private val progressRepository: LearningProgressRepository,
    private val settingsDataStore: SettingsDataStore,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val geminiLiveClient: GeminiLiveClient,
    private val liveAudioManager: LiveAudioManager,
    private val evaluationService: SpeakingEvaluationService,
    private val mlKitTranslator: OfflineMlKitTranslator,
    private val ttsManager: TtsManager,
    private val json: Json
) : ViewModel() {

    private val topicFlow = MutableStateFlow(SpeakingTopic.DAILY)
    private val customTopicFlow = MutableStateFlow("")
    private val levelFlow = MutableStateFlow(SpeakingLevel.INTERMEDIATE)
    private val voiceFlow = MutableStateFlow(AiVoice.AOEDE)
    private val showSubtitlesFlow = MutableStateFlow(true)
    private val activeSessionIdFlow = MutableStateFlow<Long?>(null)
    private val isMutedFlow = MutableStateFlow(false)
    private val elapsedSecondsFlow = MutableStateFlow(0)
    private val connectionNoteFlow = MutableStateFlow("")
    private val liveUserDraftFlow = MutableStateFlow("")
    private val liveAiDraftFlow = MutableStateFlow("")
    private val activeMessagesFlow = MutableStateFlow<List<SpeakingMessage>>(emptyList())
    private val subtitleTranslationsFlow = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val showHistoryFlow = MutableStateFlow(false)
    private val historyDetailFlow = MutableStateFlow<HistoryDetailUiState?>(null)
    private val sessionRecapFlow = MutableStateFlow<SpeakingSessionRecapUi?>(null)

    private var sessionStartedAtMillis: Long = 0L
    private var timerJob: Job? = null
    private var latestGeminiModel: String = "gemini-2.5-flash"
    private var latestLiveModel: String = "models/gemini-2.5-flash-native-audio-preview-09-2025"

    private val setupConfigFlow = combine(
        topicFlow,
        customTopicFlow,
        levelFlow,
        voiceFlow,
        showSubtitlesFlow
    ) { topic, custom, level, voice, showSubs ->
        SetupConfig(topic, custom, level, voice, showSubs)
    }

    private val liveAudioFlow = combine(
        liveAudioManager.micAmplitude,
        liveAudioManager.aiAmplitude,
        liveAudioManager.aecEnabled,
        isMutedFlow,
        elapsedSecondsFlow
    ) { mic, ai, aec, muted, elapsed ->
        LiveAudioSnapshot(mic, ai, aec, muted, elapsed)
    }

    private val transcriptFlow = combine(
        liveUserDraftFlow,
        liveAiDraftFlow,
        activeMessagesFlow,
        subtitleTranslationsFlow,
        connectionNoteFlow
    ) { userDraft, aiDraft, messages, translations, note ->
        TranscriptSnapshot(userDraft, aiDraft, messages, translations, note)
    }

    val uiState: StateFlow<SpeakingUiState> = combine(
        setupConfigFlow,
        liveAudioFlow,
        transcriptFlow,
        activeSessionIdFlow,
        settingsDataStore.settingsFlow,
        secureApiKeyStore.apiKeyFlow,
        speakingDao.observeSessions(),
        showHistoryFlow,
        historyDetailFlow,
        sessionRecapFlow
    ) { values ->
        val setup = values[0] as SetupConfig
        val audio = values[1] as LiveAudioSnapshot
        val transcript = values[2] as TranscriptSnapshot
        val activeSessionId = values[3] as Long?
        val settings = values[4] as com.enziidict.app.core.network.UserSettings
        val apiKey = values[5] as String
        @Suppress("UNCHECKED_CAST")
        val sessions = values[6] as List<SpeakingSession>
        val showHistory = values[7] as Boolean
        val detail = values[8] as HistoryDetailUiState?
        val recap = values[9] as SpeakingSessionRecapUi?

        latestGeminiModel = settings.geminiModel
        latestLiveModel = settings.geminiLiveModel

        val hasKey = apiKey.isNotBlank()
        SpeakingUiState(
            selectedTopic = setup.topic,
            customTopicText = setup.customTopic,
            selectedLevel = setup.level,
            selectedVoice = setup.voice,
            showBilingualSubtitles = setup.showSubtitles,
            isInCall = activeSessionId != null,
            isMuted = audio.isMuted,
            elapsedSeconds = audio.elapsedSeconds,
            micAmplitude = audio.micAmplitude,
            aiAmplitude = audio.aiAmplitude,
            aecEnabled = audio.aecEnabled,
            activeMode = if (hasKey && !settings.preferOfflineFallback) {
                SpeakingMode.GEMINI_LIVE
            } else {
                SpeakingMode.FALLBACK_STT_TTS
            },
            hasApiKey = hasKey,
            connectionNote = transcript.connectionNote,
            liveUserDraft = transcript.liveUserDraft,
            liveAiDraft = transcript.liveAiDraft,
            activeMessages = transcript.activeMessages,
            subtitleTranslations = transcript.translations,
            pastSessions = sessions,
            showHistoryScreen = showHistory,
            historyDetail = detail,
            sessionRecap = recap
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpeakingUiState())

    init {
        viewModelScope.launch {
            geminiLiveClient.events.collect { event ->
                val sessionId = activeSessionIdFlow.value ?: return@collect
                when (event) {
                    GeminiLiveEvent.Connected -> {
                        connectionNoteFlow.value = "Đã mở WebSocket, đang thiết lập gia sư AI..."
                    }
                    GeminiLiveEvent.SetupComplete -> {
                        connectionNoteFlow.value = "Đang trò chuyện trực tiếp (Gemini Live 16k/24k PCM)"
                    }
                    is GeminiLiveEvent.AudioChunk -> {
                        liveAudioManager.enqueueAiAudioPcm24k(event.pcm24kBytes)
                    }
                    GeminiLiveEvent.Interrupted -> {
                        liveAudioManager.flushPlayback()
                        commitAiDraftIfPresent(sessionId)
                    }
                    is GeminiLiveEvent.InputTranscript -> {
                        commitAiDraftIfPresent(sessionId)
                        liveUserDraftFlow.value = appendFragment(liveUserDraftFlow.value, event.text)
                    }
                    is GeminiLiveEvent.OutputTranscript -> {
                        commitUserDraftIfPresent(sessionId)
                        liveAiDraftFlow.value = appendFragment(liveAiDraftFlow.value, event.text)
                    }
                    GeminiLiveEvent.TurnComplete -> {
                        commitUserDraftIfPresent(sessionId)
                        commitAiDraftIfPresent(sessionId)
                    }
                    is GeminiLiveEvent.Error -> {
                        liveAudioManager.stopAll()
                        connectionNoteFlow.value = "Mất kết nối Live (${event.message}). Đang dùng chế độ dự phòng."
                    }
                    GeminiLiveEvent.Disconnected -> {
                        if (activeSessionIdFlow.value != null) {
                            connectionNoteFlow.value = "Kết nối WebSocket đã đóng."
                        }
                    }
                }
            }
        }
    }

    fun selectTopic(topic: SpeakingTopic) {
        topicFlow.value = topic
    }

    fun updateCustomTopic(text: String) {
        customTopicFlow.value = text
    }

    fun selectLevel(level: SpeakingLevel) {
        levelFlow.value = level
    }

    fun selectVoice(voice: AiVoice) {
        voiceFlow.value = voice
    }

    fun toggleSubtitles() {
        showSubtitlesFlow.value = !showSubtitlesFlow.value
    }

    fun toggleMute() {
        val nextMuted = !isMutedFlow.value
        isMutedFlow.value = nextMuted
        liveAudioManager.setMuted(nextMuted)
    }

    fun startCallWithMicPermission() {
        if (activeSessionIdFlow.value != null) return
        viewModelScope.launch {
            val apiKey = secureApiKeyStore.getApiKey()
            val mode = if (apiKey.isNotBlank()) SpeakingMode.GEMINI_LIVE else SpeakingMode.FALLBACK_STT_TTS
            val now = System.currentTimeMillis()
            val topicLabel = buildTopicStorageLabel()

            val sessionId = speakingDao.insertSession(
                SpeakingSession(
                    topic = topicLabel,
                    voiceName = voiceFlow.value.voiceName,
                    mode = mode.name,
                    startedAt = now,
                    endedAt = null,
                    durationSec = 0,
                    correctionSuggestionsJson = null
                )
            )
            sessionStartedAtMillis = now
            activeSessionIdFlow.value = sessionId
            isMutedFlow.value = false
            elapsedSecondsFlow.value = 0
            liveUserDraftFlow.value = ""
            liveAiDraftFlow.value = ""
            activeMessagesFlow.value = emptyList()
            subtitleTranslationsFlow.value = emptyMap()
            showHistoryFlow.value = false
            historyDetailFlow.value = null

            startTimer()

            if (mode == SpeakingMode.GEMINI_LIVE) {
                connectionNoteFlow.value = "Đang kết nối Gemini Live & khởi tạo micro..."
                geminiLiveClient.connect(
                    apiKey = apiKey,
                    model = latestLiveModel,
                    topic = topicFlow.value,
                    level = levelFlow.value,
                    voice = voiceFlow.value,
                    customTopicPrompt = customTopicFlow.value
                )
                val audioResult = liveAudioManager.start { pcmChunk ->
                    geminiLiveClient.sendPcm16kAudioChunk(pcmChunk)
                }
                audioResult.onFailure { err ->
                    connectionNoteFlow.value =
                        "Không khởi tạo được micro (${err.localizedMessage ?: "Audio error"})."
                }
            } else {
                connectionNoteFlow.value = "Chế độ dự phòng (chưa nhập Gemini API Key trong Cài đặt)"
                val greeting = buildFallbackGreeting(topicFlow.value, levelFlow.value)
                appendCommittedMessage(sessionId, "AI", greeting)
                ttsManager.speak(greeting, TtsAccent.US, speechRate = 0.95f)
            }
        }
    }

    fun sendFallbackUserMessage(userText: String) {
        val sessionId = activeSessionIdFlow.value ?: return
        val cleaned = userText.trim()
        if (cleaned.isEmpty()) return

        viewModelScope.launch {
            appendCommittedMessage(sessionId, "USER", cleaned)
            if (secureApiKeyStore.getApiKey().isNotBlank()) {
                geminiLiveClient.sendTextTurn(cleaned)
            } else {
                val reply = "That's a great point! Could you tell me a little more about what you mean?"
                appendCommittedMessage(sessionId, "AI", reply)
                ttsManager.speak(reply, TtsAccent.US, speechRate = 0.95f)
            }
        }
    }

    fun endSession(openReviewAfter: Boolean = true) {
        val sessionId = activeSessionIdFlow.value ?: return
        activeSessionIdFlow.value = null

        stopHardwareResources()

        viewModelScope.launch {
            commitUserDraftIfPresent(sessionId)
            commitAiDraftIfPresent(sessionId)

            val endedAt = System.currentTimeMillis()
            val durationSec = ((endedAt - sessionStartedAtMillis) / 1000L).toInt().coerceAtLeast(1)
            speakingDao.completeSession(
                sessionId = sessionId,
                endedAt = endedAt,
                durationSec = durationSec,
                suggestionsJson = null
            )
            val award = progressRepository.recordSpeakingSession(durationSec)

            if (openReviewAfter) {
                val userTurns = speakingDao.getMessagesForSession(sessionId)
                    .count { it.speaker.equals("USER", ignoreCase = true) }
                val minutes = ((durationSec + 30) / 60).coerceAtLeast(1)
                sessionRecapFlow.value = SpeakingSessionRecapUi(
                    sessionId = sessionId,
                    durationSec = durationSec,
                    speakingMinutes = minutes,
                    userTurns = userTurns,
                    xpEarned = award.xpAdded,
                    streakAfter = award.streakAfter,
                    streakIncreased = award.streakIncreased,
                    freezeUsedCount = award.freezeUsedCount,
                    goalJustCompleted = award.goalJustCompleted
                )
            } else {
                connectionNoteFlow.value = "Đã kết thúc và lưu phiên nói (${durationSec}s)."
            }
        }
    }

    fun dismissRecap(openDetailAfter: Boolean) {
        val sessionId = sessionRecapFlow.value?.sessionId
        sessionRecapFlow.value = null
        if (openDetailAfter && sessionId != null) {
            showHistoryFlow.value = true
            openHistoryDetail(sessionId, triggerAutoEvaluation = true)
        }
    }

    /**
     * Lifecycle safety hook invoked from DisposableEffect (ON_STOP / onDispose) and ViewModel.onCleared().
     * Guarantees microphone, AcousticEchoCanceler, AudioTrack, and WebSocket are released immediately.
     */
    fun releaseResourcesOnExit() {
        if (activeSessionIdFlow.value != null) {
            endSession(openReviewAfter = false)
        } else {
            stopHardwareResources()
        }
    }

    fun openHistory() {
        showHistoryFlow.value = true
    }

    fun closeHistory() {
        historyDetailFlow.value = null
        showHistoryFlow.value = false
    }

    fun openHistoryDetail(sessionId: Long, triggerAutoEvaluation: Boolean = false) {
        viewModelScope.launch {
            val session = speakingDao.getSessionById(sessionId) ?: return@launch
            val messages = speakingDao.getMessagesForSession(sessionId)
            val parsedAnalysis = session.correctionSuggestionsJson?.let { raw ->
                runCatching { json.decodeFromString<SpeakingAnalysis>(raw) }.getOrNull()
            }

            historyDetailFlow.value = HistoryDetailUiState(
                session = session,
                messages = messages,
                analysis = parsedAnalysis
            )

            if (triggerAutoEvaluation && parsedAnalysis == null) {
                evaluateHistorySession(sessionId)
            }
        }
    }

    fun closeHistoryDetail() {
        historyDetailFlow.value = null
    }

    fun evaluateHistorySession(sessionId: Long) {
        viewModelScope.launch {
            val currentDetail = historyDetailFlow.value ?: return@launch
            if (currentDetail.session.id != sessionId) return@launch

            historyDetailFlow.value = currentDetail.copy(
                isEvaluating = true,
                evaluationError = null,
                actionMessage = null
            )

            val outcome = evaluationService.evaluateSession(
                topicTitle = currentDetail.session.topic,
                messages = currentDetail.messages,
                apiKey = secureApiKeyStore.getApiKey(),
                model = latestGeminiModel
            )

            val refreshed = historyDetailFlow.value ?: return@launch
            if (refreshed.session.id != sessionId) return@launch

            when (outcome) {
                is SpeakingEvaluationOutcome.Success -> {
                    speakingDao.updateAnalysis(sessionId, outcome.serializedJson)
                    historyDetailFlow.value = refreshed.copy(
                        session = refreshed.session.copy(correctionSuggestionsJson = outcome.serializedJson),
                        analysis = outcome.analysis,
                        isEvaluating = false,
                        evaluationError = null
                    )
                }
                SpeakingEvaluationOutcome.MissingApiKey -> {
                    historyDetailFlow.value = refreshed.copy(
                        isEvaluating = false,
                        evaluationError = "Chưa nhập Gemini API Key. Hãy vào Cài đặt để nhập API Key rồi bấm \"Phân tích lại\"."
                    )
                }
                SpeakingEvaluationOutcome.EmptyTranscript -> {
                    historyDetailFlow.value = refreshed.copy(
                        isEvaluating = false,
                        evaluationError = "Phiên nói chưa có lượt phát biểu nào của bạn để AI đánh giá."
                    )
                }
                is SpeakingEvaluationOutcome.NetworkError -> {
                    historyDetailFlow.value = refreshed.copy(
                        isEvaluating = false,
                        evaluationError = outcome.messageVi
                    )
                }
                is SpeakingEvaluationOutcome.ApiError -> {
                    historyDetailFlow.value = refreshed.copy(
                        isEvaluating = false,
                        evaluationError = outcome.messageVi
                    )
                }
            }
        }
    }

    fun saveVocabToSrs(vocab: SuggestedVocab) {
        viewModelScope.launch {
            val inserted = dictionaryRepository.saveSuggestedWordToConversationFolder(
                word = vocab.word,
                pos = vocab.pos,
                meaningVi = vocab.meaningVi
            )
            val current = historyDetailFlow.value ?: return@launch
            historyDetailFlow.value = current.copy(
                savedWordsSet = current.savedWordsSet + vocab.word.lowercase(),
                actionMessage = if (inserted) {
                    "Đã lưu \"${vocab.word}\" vào thư mục \"Từ lưu từ hội thoại\""
                } else {
                    "\"${vocab.word}\" đã có sẵn trong \"Từ lưu từ hội thoại\""
                }
            )
        }
    }

    fun deleteHistorySession(sessionId: Long) {
        viewModelScope.launch {
            speakingDao.deleteSession(sessionId)
            if (historyDetailFlow.value?.session?.id == sessionId) {
                historyDetailFlow.value = null
            }
        }
    }

    fun speakSample(text: String) {
        ttsManager.speak(text, TtsAccent.US, speechRate = 0.95f)
    }

    override fun onCleared() {
        stopHardwareResources()
        super.onCleared()
    }

    private fun stopHardwareResources() {
        timerJob?.cancel()
        timerJob = null
        liveAudioManager.stopAll()
        geminiLiveClient.disconnect()
        ttsManager.stop()
        isMutedFlow.value = false
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && activeSessionIdFlow.value != null) {
                delay(1000L)
                elapsedSecondsFlow.value += 1
            }
        }
    }

    private suspend fun commitUserDraftIfPresent(sessionId: Long) {
        val text = liveUserDraftFlow.value.trim()
        if (text.isNotEmpty()) {
            liveUserDraftFlow.value = ""
            appendCommittedMessage(sessionId, "USER", text)
        }
    }

    private suspend fun commitAiDraftIfPresent(sessionId: Long) {
        val text = liveAiDraftFlow.value.trim()
        if (text.isNotEmpty()) {
            liveAiDraftFlow.value = ""
            appendCommittedMessage(sessionId, "AI", text)
        }
    }

    private suspend fun appendCommittedMessage(sessionId: Long, speaker: String, text: String) {
        val id = speakingDao.insertMessage(
            SpeakingMessage(
                sessionId = sessionId,
                speaker = speaker,
                transcript = text,
                suggestionNote = null,
                createdAt = System.currentTimeMillis()
            )
        )
        activeMessagesFlow.value = speakingDao.getMessagesForSession(sessionId)

        // Translate AI messages into Vietnamese asynchronously for bilingual subtitles
        if (speaker == "AI" && showSubtitlesFlow.value) {
            viewModelScope.launch {
                if (mlKitTranslator.isEnViPackDownloaded()) {
                    mlKitTranslator.translate(text, enToVi = true).onSuccess { vi ->
                        if (vi.isNotBlank()) {
                            subtitleTranslationsFlow.value = subtitleTranslationsFlow.value + (id to vi)
                        }
                    }
                }
            }
        }
    }

    private fun appendFragment(existing: String, fragment: String): String {
        if (existing.isEmpty()) return fragment.trimStart()
        val needsSpace = !existing.endsWith(" ") &&
            !fragment.startsWith(" ") &&
            fragment.firstOrNull()?.let { it.isLetterOrDigit() } == true
        return if (needsSpace) "$existing $fragment" else existing + fragment
    }

    private fun buildTopicStorageLabel(): String {
        val base = topicFlow.value.titleVi
        val level = levelFlow.value.titleVi
        val custom = customTopicFlow.value.trim()
        return if (topicFlow.value == SpeakingTopic.FREE && custom.isNotEmpty()) {
            "$custom ($level)"
        } else {
            "$base • $level"
        }
    }

    private fun buildFallbackGreeting(topic: SpeakingTopic, level: SpeakingLevel): String {
        return when (topic) {
            SpeakingTopic.CAFE -> "Welcome to Enzii Cafe! What can I get started for you today?"
            SpeakingTopic.INTERVIEW -> "Hello, thanks for joining today. Could you briefly introduce yourself?"
            SpeakingTopic.TRAVEL -> "Hi there! Where are you planning to travel on your next trip?"
            SpeakingTopic.FREE -> "Hi! I'm your English partner (${level.cefrHint}). What would you like to talk about today?"
            SpeakingTopic.DAILY -> "Hey! How has your day been going so far?"
        }
    }

    private data class SetupConfig(
        val topic: SpeakingTopic,
        val customTopic: String,
        val level: SpeakingLevel,
        val voice: AiVoice,
        val showSubtitles: Boolean
    )

    private data class LiveAudioSnapshot(
        val micAmplitude: Float,
        val aiAmplitude: Float,
        val aecEnabled: Boolean,
        val isMuted: Boolean,
        val elapsedSeconds: Int
    )

    private data class TranscriptSnapshot(
        val liveUserDraft: String,
        val liveAiDraft: String,
        val activeMessages: List<SpeakingMessage>,
        val translations: Map<Long, String>,
        val connectionNote: String
    )
}
