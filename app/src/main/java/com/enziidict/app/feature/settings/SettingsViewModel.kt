package com.enziidict.app.feature.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.BuildConfig
import com.enziidict.app.core.network.AppReleaseInfo
import com.enziidict.app.core.network.AppThemeMode
import com.enziidict.app.core.network.AppUpdateManager
import com.enziidict.app.core.network.DailyWordSource
import com.enziidict.app.core.network.GeminiModelPresets
import com.enziidict.app.core.network.OfflineMlKitTranslator
import com.enziidict.app.core.network.SecureApiKeyStore
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.core.network.UpdateCheckOutcome
import com.enziidict.app.core.network.UpdateDownloadState
import com.enziidict.app.core.network.UserSettings
import com.enziidict.app.core.notification.PreviewNotificationKind
import com.enziidict.app.core.notification.StudyNotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val isEncryptedStorage: Boolean = true,
    val selectedModel: String = GeminiModelPresets.FLASH_25,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isMlKitPackDownloaded: Boolean = false,
    val isMlKitBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val isCheckingUpdate: Boolean = false,
    val availableRelease: AppReleaseInfo? = null,
    val updateStatusText: String? = null,
    val downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderHour: Int = 20,
    val dailyReminderMinute: Int = 0,
    val dailyWordEnabled: Boolean = false,
    val dailyWordFrequency: Int = 1,
    val dailyWordSource: DailyWordSource = DailyWordSource.MIXED
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureApiKeyStore: SecureApiKeyStore,
    private val settingsDataStore: SettingsDataStore,
    private val mlKitTranslator: OfflineMlKitTranslator,
    private val appUpdateManager: AppUpdateManager,
    private val notificationScheduler: StudyNotificationScheduler
) : ViewModel() {

    private val mlKitDownloadedFlow = MutableStateFlow(false)
    private val mlKitBusyFlow = MutableStateFlow(false)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val errorMessageFlow = MutableStateFlow<String?>(null)

    private val checkingUpdateFlow = MutableStateFlow(false)
    private val availableReleaseFlow = MutableStateFlow<AppReleaseInfo?>(null)
    private val updateStatusTextFlow = MutableStateFlow<String?>(null)
    private val downloadStateFlow = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        secureApiKeyStore.apiKeyFlow,
        settingsDataStore.settingsFlow,
        mlKitDownloadedFlow,
        mlKitBusyFlow,
        statusMessageFlow,
        errorMessageFlow,
        checkingUpdateFlow,
        availableReleaseFlow,
        updateStatusTextFlow,
        downloadStateFlow
    ) { values ->
        val apiKey = values[0] as String
        val settings = values[1] as UserSettings
        val downloaded = values[2] as Boolean
        val busy = values[3] as Boolean
        val status = values[4] as String?
        val error = values[5] as String?
        val checkingUpdate = values[6] as Boolean
        val release = values[7] as AppReleaseInfo?
        val updateText = values[8] as String?
        val downloadState = values[9] as UpdateDownloadState

        SettingsUiState(
            apiKey = apiKey,
            isEncryptedStorage = secureApiKeyStore.isEncryptedStorage,
            selectedModel = settings.geminiModel,
            themeMode = settings.themeMode,
            isMlKitPackDownloaded = downloaded,
            isMlKitBusy = busy,
            statusMessage = status,
            errorMessage = error,
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE,
            isCheckingUpdate = checkingUpdate,
            availableRelease = release,
            updateStatusText = updateText,
            downloadState = downloadState,
            dailyReminderEnabled = settings.dailyReminderEnabled,
            dailyReminderHour = settings.dailyReminderHour,
            dailyReminderMinute = settings.dailyReminderMinute,
            dailyWordEnabled = settings.dailyWordEnabled,
            dailyWordFrequency = settings.dailyWordFrequency,
            dailyWordSource = settings.dailyWordSource
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

    fun updateDailyReminder(
        enabled: Boolean,
        hour: Int = uiState.value.dailyReminderHour,
        minute: Int = uiState.value.dailyReminderMinute
    ) {
        viewModelScope.launch {
            settingsDataStore.saveDailyReminderConfig(enabled, hour, minute)
            val updated = settingsDataStore.settingsFlow.first()
            notificationScheduler.syncSchedules(updated)
            errorMessageFlow.value = null
            statusMessageFlow.value = if (enabled) {
                String.format(
                    Locale.US,
                    "Đã bật nhắc học hằng ngày lúc %02d:%02d.",
                    updated.dailyReminderHour,
                    updated.dailyReminderMinute
                )
            } else {
                "Đã tắt thông báo nhắc học hằng ngày."
            }
        }
    }

    fun updateDailyWord(
        enabled: Boolean,
        frequency: Int = uiState.value.dailyWordFrequency,
        source: DailyWordSource = uiState.value.dailyWordSource
    ) {
        viewModelScope.launch {
            settingsDataStore.saveDailyWordConfig(enabled, frequency, source)
            val updated = settingsDataStore.settingsFlow.first()
            notificationScheduler.syncSchedules(updated)
            errorMessageFlow.value = null
            statusMessageFlow.value = if (enabled) {
                val freqLabel = if (updated.dailyWordFrequency >= 2) {
                    "2 lần/ngày (08:30 & 13:00)"
                } else {
                    "1 lần/ngày (08:30)"
                }
                "Đã bật Từ vựng/Mẫu câu mỗi ngày: $freqLabel • ${updated.dailyWordSource.labelVi}."
            } else {
                "Đã tắt thông báo Từ vựng / Mẫu câu trong ngày."
            }
        }
    }

    fun sendTestReminderNow() {
        notificationScheduler.triggerPreviewNow(PreviewNotificationKind.REMINDER)
        errorMessageFlow.value = null
        statusMessageFlow.value = "Đã gửi yêu cầu thông báo nhắc học thử — hãy kéo thanh trạng thái để xem."
    }

    fun sendTestWordNotificationNow() {
        notificationScheduler.triggerPreviewNow(PreviewNotificationKind.WORD_OR_SENTENCE)
        errorMessageFlow.value = null
        statusMessageFlow.value = "Đã gửi thông báo Từ vựng / Mẫu câu hôm nay — chạm vào để mở thẳng chi tiết."
    }

    fun notifyNotificationPermissionDenied() {
        statusMessageFlow.value = null
        errorMessageFlow.value =
            "Bạn chưa cấp quyền Thông báo (POST_NOTIFICATIONS). Hãy bật quyền trong Cài đặt ứng dụng Android để nhận nhắc học."
    }

    fun checkForUpdates() {
        if (checkingUpdateFlow.value) return
        viewModelScope.launch {
            checkingUpdateFlow.value = true
            updateStatusTextFlow.value = "Đang kiểm tra bản phát hành mới từ GitHub..."
            when (val outcome = appUpdateManager.checkLatestRelease()) {
                is UpdateCheckOutcome.UpdateAvailable -> {
                    availableReleaseFlow.value = outcome.release
                    updateStatusTextFlow.value = "Đã có phiên bản mới v${outcome.release.versionName}!"
                }
                is UpdateCheckOutcome.UpToDate -> {
                    availableReleaseFlow.value = null
                    updateStatusTextFlow.value = "Bạn đang dùng phiên bản mới nhất (v${outcome.currentVersion})."
                }
                is UpdateCheckOutcome.NoReleaseYet -> {
                    availableReleaseFlow.value = null
                    updateStatusTextFlow.value = outcome.messageVi
                }
                is UpdateCheckOutcome.Error -> {
                    updateStatusTextFlow.value = outcome.messageVi
                }
            }
            checkingUpdateFlow.value = false
        }
    }

    fun downloadAndPrepareUpdate() {
        val release = availableReleaseFlow.value ?: return
        if (downloadStateFlow.value is UpdateDownloadState.Downloading) return

        viewModelScope.launch {
            val totalMb = release.apkSizeBytes / (1024.0 * 1024.0)
            downloadStateFlow.value = UpdateDownloadState.Downloading(0, 0.0, totalMb)
            updateStatusTextFlow.value = "Đang tải bản cập nhật v${release.versionName}..."

            val result = appUpdateManager.downloadApk(release) { pct, dlMb, totMb ->
                downloadStateFlow.value = UpdateDownloadState.Downloading(pct, dlMb, totMb)
            }
            result.onSuccess { file ->
                downloadStateFlow.value = UpdateDownloadState.ReadyToInstall(file, release.versionName)
                updateStatusTextFlow.value = "Đã tải xong v${release.versionName}. Bấm Cài đặt để cập nhật giữ nguyên dữ liệu."
            }.onFailure { err ->
                val msg = "Tải bản cập nhật thất bại: ${err.localizedMessage ?: "Lỗi mạng"}"
                downloadStateFlow.value = UpdateDownloadState.Failed(msg)
                updateStatusTextFlow.value = msg
            }
        }
    }

    fun canInstallPackages(): Boolean = appUpdateManager.canRequestPackageInstalls()

    fun createInstallIntent(apkFile: File): Intent = appUpdateManager.createInstallApkIntent(apkFile)

    fun createInstallPermissionIntent(): Intent = appUpdateManager.createUnknownSourcesSettingsIntent()

    fun notifyNeedInstallPermission() {
        updateStatusTextFlow.value =
            "Vui lòng bật \"Cho phép từ nguồn này\" trong Cài đặt vừa mở, sau đó quay lại bấm \"Cài đặt ngay\"."
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
