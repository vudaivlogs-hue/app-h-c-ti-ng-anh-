package com.enziidict.app.feature.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.network.GeminiGrammarService
import com.enziidict.app.core.network.GeminiModelPresets
import com.enziidict.app.core.network.GrammarAnalysisOutcome
import com.enziidict.app.core.network.GrammarAnalysisResult
import com.enziidict.app.core.network.OfflineMlKitTranslator
import com.enziidict.app.core.network.SecureApiKeyStore
import com.enziidict.app.core.network.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AiAnalysisError {
    data object MissingApiKey : AiAnalysisError
    data class Message(val text: String) : AiAnalysisError
}

data class TranslateUiState(
    val inputText: String = "",
    val enToVi: Boolean = true,
    val offlineTranslation: String = "",
    val isOfflineTranslating: Boolean = false,
    val offlineError: String? = null,
    val isAnalyzingAi: Boolean = false,
    val aiAnalysis: GrammarAnalysisResult? = null,
    val aiError: AiAnalysisError? = null,
    val hasApiKey: Boolean = false,
    val selectedModel: String = GeminiModelPresets.FLASH_25
)

@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val mlKitTranslator: OfflineMlKitTranslator,
    private val geminiGrammarService: GeminiGrammarService,
    private val secureApiKeyStore: SecureApiKeyStore,
    settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val inputFlow = MutableStateFlow("")
    private val enToViFlow = MutableStateFlow(true)
    private val offlineTranslationFlow = MutableStateFlow("")
    private val isOfflineTranslatingFlow = MutableStateFlow(false)
    private val offlineErrorFlow = MutableStateFlow<String?>(null)
    private val isAnalyzingAiFlow = MutableStateFlow(false)
    private val aiAnalysisFlow = MutableStateFlow<GrammarAnalysisResult?>(null)
    private val aiErrorFlow = MutableStateFlow<AiAnalysisError?>(null)

    val uiState: StateFlow<TranslateUiState> = combine(
        inputFlow,
        enToViFlow,
        offlineTranslationFlow,
        isOfflineTranslatingFlow,
        offlineErrorFlow,
        isAnalyzingAiFlow,
        aiAnalysisFlow,
        aiErrorFlow,
        secureApiKeyStore.apiKeyFlow,
        settingsDataStore.settingsFlow
    ) { values ->
        val input = values[0] as String
        val enToVi = values[1] as Boolean
        val offlineOut = values[2] as String
        val offlineLoading = values[3] as Boolean
        val offlineErr = values[4] as String?
        val aiLoading = values[5] as Boolean
        val aiResult = values[6] as GrammarAnalysisResult?
        val aiErr = values[7] as AiAnalysisError?
        val apiKey = values[8] as String
        val settings = values[9] as com.enziidict.app.core.network.UserSettings

        TranslateUiState(
            inputText = input,
            enToVi = enToVi,
            offlineTranslation = offlineOut,
            isOfflineTranslating = offlineLoading,
            offlineError = offlineErr,
            isAnalyzingAi = aiLoading,
            aiAnalysis = aiResult,
            aiError = aiErr,
            hasApiKey = apiKey.isNotBlank(),
            selectedModel = settings.geminiModel
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslateUiState())

    fun onInputChange(text: String) {
        inputFlow.value = text
        offlineErrorFlow.value = null
        aiErrorFlow.value = null
    }

    fun clearInput() {
        inputFlow.value = ""
        offlineTranslationFlow.value = ""
        offlineErrorFlow.value = null
        aiAnalysisFlow.value = null
        aiErrorFlow.value = null
    }

    fun swapDirection() {
        enToViFlow.value = !enToViFlow.value
        val previousTranslation = offlineTranslationFlow.value
        if (previousTranslation.isNotBlank()) {
            inputFlow.value = previousTranslation
            offlineTranslationFlow.value = ""
        }
        offlineErrorFlow.value = null
        aiAnalysisFlow.value = null
        aiErrorFlow.value = null
    }

    fun translateOffline() {
        val text = inputFlow.value.trim()
        if (text.isEmpty()) {
            offlineErrorFlow.value = "Vui lòng nhập nội dung cần dịch."
            return
        }
        viewModelScope.launch {
            isOfflineTranslatingFlow.value = true
            offlineErrorFlow.value = null
            val result = mlKitTranslator.translate(text, enToVi = enToViFlow.value)
            isOfflineTranslatingFlow.value = false
            result.onSuccess { translated ->
                offlineTranslationFlow.value = translated
            }.onFailure { err ->
                offlineErrorFlow.value = err.message ?: "Không thể dịch offline lúc này."
            }
        }
    }

    fun analyzeGrammarWithAi() {
        val text = inputFlow.value.trim()
        if (text.isEmpty()) {
            aiErrorFlow.value = AiAnalysisError.Message("Vui lòng nhập câu cần phân tích ngữ pháp.")
            return
        }
        val apiKey = secureApiKeyStore.getApiKey()
        if (apiKey.isBlank()) {
            aiErrorFlow.value = AiAnalysisError.MissingApiKey
            return
        }

        val model = uiState.value.selectedModel
        viewModelScope.launch {
            isAnalyzingAiFlow.value = true
            aiErrorFlow.value = null
            when (
                val outcome = geminiGrammarService.analyze(
                    text = text,
                    enToVi = enToViFlow.value,
                    apiKey = apiKey,
                    model = model
                )
            ) {
                is GrammarAnalysisOutcome.Success -> {
                    aiAnalysisFlow.value = outcome.analysis
                }
                GrammarAnalysisOutcome.MissingApiKey -> {
                    aiErrorFlow.value = AiAnalysisError.MissingApiKey
                }
                is GrammarAnalysisOutcome.NetworkError -> {
                    aiErrorFlow.value = AiAnalysisError.Message(outcome.messageVi)
                }
                is GrammarAnalysisOutcome.ApiError -> {
                    aiErrorFlow.value = AiAnalysisError.Message(outcome.messageVi)
                }
            }
            isAnalyzingAiFlow.value = false
        }
    }

    fun speakEnglish(text: String) {
        ttsManager.speak(text, TtsAccent.US, speechRate = 0.95f)
    }
}
