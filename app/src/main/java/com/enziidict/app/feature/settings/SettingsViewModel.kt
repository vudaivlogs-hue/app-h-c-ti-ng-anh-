package com.enziidict.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.network.AppThemeMode
import com.enziidict.app.core.network.GeminiModelPresets
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

data class SettingsUiState(
    val apiKey: String = "",
    val isEncryptedStorage: Boolean = true,
    val selectedModel: String = GeminiModelPresets.FLASH_25,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isMlKitPackDownloaded: Boolean = false,
    val isMlKitBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureApiKeyStore: SecureApiKeyStore,
    private val settingsDataStore: SettingsDataStore,
    private val mlKitTranslator: OfflineMlKitTranslator
) : ViewModel() {

    private val mlKitDownloadedFlow = MutableStateFlow(false)
    private val mlKitBusyFlow = MutableStateFlow(false)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val errorMessageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        secureApiKeyStore.apiKeyFlow,
        settingsDataStore.settingsFlow,
        mlKitDownloadedFlow,
        mlKitBusyFlow,
        statusMessageFlow,
        errorMessageFlow
    ) { values ->
        val apiKey = values[0] as String
        val settings = values[1] as com.enziidict.app.core.network.UserSettings
        val downloaded = values[2] as Boolean
        val busy = values[3] as Boolean
        val status = values[4] as String?
        val error = values[5] as String?

        SettingsUiState(
            apiKey = apiKey,
            isEncryptedStorage = secureApiKeyStore.isEncryptedStorage,
            selectedModel = settings.geminiModel,
            themeMode = settings.themeMode,
            isMlKitPackDownloaded = downloaded,
            isMlKitBusy = busy,
            statusMessage = status,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshMlKitPackStatus()
    }

    fun refreshMlKitPackStatus() {
        viewModelScope.launch {
            mlKitDownloadedFlow.value = mlKitTranslator.isEnViPackDownloaded()
        }
    }

    fun saveApiKey(rawKey: String) {
        val cleaned = rawKey.trim()
        secureApiKeyStore.saveApiKey(cleaned)
        errorMessageFlow.value = null
        statusMessageFlow.value = if (cleaned.isNotEmpty()) {
            "Đã mã hóa và lưu Gemini API Key an toàn trên thiết bị."
        } else {
            "Đã xóa Gemini API Key."
        }
    }

    fun clearApiKey() {
        secureApiKeyStore.clearApiKey()
        errorMessageFlow.value = null
        statusMessageFlow.value = "Đã xóa Gemini API Key khỏi thiết bị."
    }

    fun selectGeminiModel(model: String) {
        val cleaned = model.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            settingsDataStore.saveGeminiModel(cleaned)
            errorMessageFlow.value = null
            statusMessageFlow.value = "Đã đặt model mặc định: $cleaned"
        }
    }

    fun selectThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsDataStore.saveThemeMode(mode)
            statusMessageFlow.value = "Đã chuyển giao diện: ${mode.labelVi}"
        }
    }

    fun downloadMlKitPack() {
        if (mlKitBusyFlow.value) return
        viewModelScope.launch {
            mlKitBusyFlow.value = true
            errorMessageFlow.value = null
            statusMessageFlow.value = "Đang tải gói ngôn ngữ offline Anh – Việt (~30MB)..."
            val result = mlKitTranslator.downloadEnViPack()
            mlKitBusyFlow.value = false
            mlKitDownloadedFlow.value = mlKitTranslator.isEnViPackDownloaded()
            result.onSuccess {
                statusMessageFlow.value = "Đã tải xong gói ngôn ngữ offline Anh – Việt (ML Kit)."
            }.onFailure { err ->
                statusMessageFlow.value = null
                errorMessageFlow.value =
                    "Không thể tải gói offline (kiểm tra kết nối mạng): ${err.localizedMessage ?: "Lỗi mạng"}"
            }
        }
    }

    fun deleteMlKitPack() {
        if (mlKitBusyFlow.value) return
        viewModelScope.launch {
            mlKitBusyFlow.value = true
            errorMessageFlow.value = null
            val result = mlKitTranslator.deleteEnViPack()
            mlKitBusyFlow.value = false
            mlKitDownloadedFlow.value = mlKitTranslator.isEnViPackDownloaded()
            result.onSuccess {
                statusMessageFlow.value = "Đã xóa gói ngôn ngữ offline Anh – Việt để giải phóng bộ nhớ."
            }.onFailure { err ->
                errorMessageFlow.value = "Không thể xóa gói offline: ${err.localizedMessage ?: "Lỗi không xác định"}"
            }
        }
    }
}
