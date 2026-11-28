package com.enziidict.app.feature.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.network.AppThemeMode
import com.enziidict.app.core.network.DailyWordSource
import com.enziidict.app.core.network.GeminiModelPresets
import com.enziidict.app.core.network.UpdateDownloadState
import com.enziidict.app.core.notification.EnziiNotificationHelper
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var keyDraft by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var showKey by remember { mutableStateOf(false) }

    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            pendingPermissionAction?.invoke()
        } else {
            permissionDenied = true
            viewModel.notifyNotificationPermissionDenied()
        }
        pendingPermissionAction = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !EnziiNotificationHelper.hasNotificationPermission(context)
        ) {
            pendingPermissionAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionDenied = false
            action()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt Enzii Dict") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.statusMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            state.errorMessage?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 0. Theme Mode (Light / Dark / System)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Giao diện (Light / Dark Mode)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val selected = state.themeMode == mode
                            if (selected) {
                                Button(
                                    onClick = { viewModel.selectThemeMode(mode) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(mode.labelVi, maxLines = 1)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.selectThemeMode(mode) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(mode.labelVi, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // 1. Smart Study Notifications & Word of the Day
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nhắc nhở & Từ vựng mỗi ngày",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Chạy nền bền bỉ bằng WorkManager: giữ chuỗi Streak và gợi ý từ/mẫu câu mở thẳng vào thẻ ôn hoặc Chi tiết từ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (permissionDenied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Mở Cài đặt Thông báo Android")
                        }
                    }

                    HorizontalDivider()

                    // Daily Study Reminder Toggle & Time Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nhắc học hằng ngày",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Chỉ nhắc nếu hôm nay bạn chưa học hoặc còn thẻ SRS đến hạn.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = state.dailyReminderEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    runWithNotificationPermission {
                                        viewModel.updateDailyReminder(enabled = true)
                                    }
                                } else {
                                    viewModel.updateDailyReminder(enabled = false)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Khung giờ nhắc:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        runWithNotificationPermission {
                                            viewModel.updateDailyReminder(
                                                enabled = true,
                                                hour = hour,
                                                minute = minute
                                            )
                                        }
                                    },
                                    state.dailyReminderHour,
                                    state.dailyReminderMinute,
                                    true
                                ).show()
                            }
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                String.format(
                                    Locale.US,
                                    "%02d:%02d",
                                    state.dailyReminderHour,
                                    state.dailyReminderMinute
                                )
                            )
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(19 to 0, 20 to 0, 21 to 0, 22 to 0).forEach { (h, m) ->
                            val isSelected = state.dailyReminderHour == h && state.dailyReminderMinute == m
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    runWithNotificationPermission {
                                        viewModel.updateDailyReminder(
                                            enabled = true,
                                            hour = h,
                                            minute = m
                                        )
                                    }
                                },
                                label = { Text(String.format(Locale.US, "%02d:%02d", h, m)) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // Daily Word / Sentence Toggle, Frequency & Source
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Từ vựng / Mẫu câu trong ngày",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Đẩy thông báo mở rộng (IPA, nghĩa Việt, ví dụ) để học nhanh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = state.dailyWordEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    runWithNotificationPermission {
                                        viewModel.updateDailyWord(enabled = true)
                                    }
                                } else {
                                    viewModel.updateDailyWord(enabled = false)
                                }
                            }
                        )
                    }

                    Text(
                        text = "Tần suất gửi mỗi ngày:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.dailyWordFrequency == 1,
                            onClick = {
                                runWithNotificationPermission {
                                    viewModel.updateDailyWord(enabled = true, frequency = 1)
                                }
                            },
                            label = { Text("1 lần/ngày (08:30)") }
                        )
                        FilterChip(
                            selected = state.dailyWordFrequency >= 2,
                            onClick = {
                                runWithNotificationPermission {
                                    viewModel.updateDailyWord(enabled = true, frequency = 2)
                                }
                            },
                            label = { Text("2 lần/ngày (08:30 & 13:00)") }
                        )
                    }

                    Text(
                        text = "Nguồn nội dung:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DailyWordSource.entries.forEach { src ->
                            FilterChip(
                                selected = state.dailyWordSource == src,
                                onClick = {
                                    runWithNotificationPermission {
                                        viewModel.updateDailyWord(enabled = true, source = src)
                                    }
                                },
                                label = { Text(src.labelVi) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runWithNotificationPermission { viewModel.sendTestReminderNow() }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Thử nhắc học", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                runWithNotificationPermission { viewModel.sendTestWordNotificationNow() }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Thử từ hôm nay", maxLines = 1)
                        }
                    }
                }
            }

            // 2. Gemini API Key (BYOK Encrypted)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gemini API Key cá nhân (BYOK)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isEncryptedStorage) {
                                "Lưu mã hóa AES-256 (EncryptedSharedPreferences) trên máy"
                            } else {
                                "Lưu cục bộ riêng tư trên thiết bị"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIza...") },
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Hiện/Ẩn API Key"
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveApiKey(keyDraft) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Lưu API Key")
                        }
                        if (state.apiKey.isNotEmpty()) {
                            OutlinedButton(onClick = {
                                keyDraft = ""
                                viewModel.clearApiKey()
                            }) {
                                Text("Xóa")
                            }
                        }
                    }
                }
            }

            // 3. Default Gemini Model Selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Model Gemini mặc định",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dùng cho Phân tích ngữ pháp & sắc thái từ ở màn hình Dịch thuật.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    GeminiModelPresets.ALL.forEach { (modelId, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectGeminiModel(modelId) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.selectedModel == modelId,
                                onClick = { viewModel.selectGeminiModel(modelId) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(modelId, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 4. Offline ML Kit Language Pack Management
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Gói ngôn ngữ Offline (Google ML Kit)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.isMlKitPackDownloaded) {
                            "Trạng thái: Đã tải gói Anh ↔ Việt (dịch tức thì không cần mạng)."
                        } else {
                            "Trạng thái: Chưa tải gói Anh ↔ Việt (~30MB). Tải trước để dịch offline mọi lúc."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isMlKitPackDownloaded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = viewModel::downloadMlKitPack,
                            enabled = !state.isMlKitBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isMlKitBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.isMlKitPackDownloaded) "Cập nhật gói" else "Tải gói Anh – Việt")
                        }

                        OutlinedButton(
                            onClick = viewModel::deleteMlKitPack,
                            enabled = !state.isMlKitBusy && state.isMlKitPackDownloaded
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xóa gói")
                        }
                    }
                }
            }

            // 5. In-App Update via GitHub Releases
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cập nhật ứng dụng (GitHub Releases)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Phiên bản hiện tại: v${state.currentVersionName} (build ${state.currentVersionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    state.updateStatusText?.let { statusText ->
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val release = state.availableRelease
                    if (release != null) {
                        HorizontalDivider()
                        val sizeText = if (release.apkSizeBytes > 0L) {
                            String.format(Locale.US, "%.1f MB", release.apkSizeBytes / (1024.0 * 1024.0))
                        } else {
                            ""
                        }
                        Text(
                            text = listOf("Bản mới: v${release.versionName}", sizeText)
                                .filter { it.isNotEmpty() }
                                .joinToString(" • "),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (release.releaseNotes.isNotBlank()) {
                            Text(
                                text = release.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    when (val dl = state.downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { (dl.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Đang tải: %d%% (%.1f / %.1f MB)",
                                    dl.progressPercent,
                                    dl.downloadedMb,
                                    dl.totalMb
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is UpdateDownloadState.ReadyToInstall -> {
                            Button(
                                onClick = {
                                    if (viewModel.canInstallPackages()) {
                                        context.startActivity(viewModel.createInstallIntent(dl.apkFile))
                                    } else {
                                        viewModel.notifyNeedInstallPermission()
                                        context.startActivity(viewModel.createInstallPermissionIntent())
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cài đặt bản v${dl.versionName} ngay")
                            }
                        }
                        else -> Unit
                    }

                    val isDownloading = state.downloadState is UpdateDownloadState.Downloading
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::checkForUpdates,
                            enabled = !state.isCheckingUpdate && !isDownloading,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Kiểm tra cập nhật")
                        }

                        if (release != null && state.downloadState !is UpdateDownloadState.ReadyToInstall) {
                            Button(
                                onClick = viewModel::downloadAndPrepareUpdate,
                                enabled = !isDownloading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tải bản mới")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
