package com.enziidict.app.feature.speaking

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.model.AiVoice
import com.enziidict.app.core.model.SpeakingLevel
import com.enziidict.app.core.model.SpeakingMode
import com.enziidict.app.core.model.SpeakingTopic
import com.enziidict.app.feature.progress.SpeakingRecapDialog
import com.enziidict.app.ui.components.DuiMascotView
import com.enziidict.app.ui.components.MascotLevel
import com.enziidict.app.ui.components.MascotMood
import com.enziidict.app.ui.components.MascotState
import com.enziidict.app.ui.components.rememberEnziiHaptics
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakingScreen(
    initialTopic: SpeakingTopic? = null,
    onInitialTopicConsumed: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: SpeakingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberEnziiHaptics()
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialTopic) {
        if (initialTopic != null) {
            viewModel.selectTopic(initialTopic)
            onInitialTopicConsumed()
        }
    }

    // Lifecycle safety: always release mic/AudioRecord/AudioTrack & close WebSocket on ON_STOP or dispose
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.releaseResourcesOnExit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.releaseResourcesOnExit()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedMessage = null
            viewModel.startCallWithMicPermission()
        } else {
            permissionDeniedMessage =
                "Cần cấp quyền Micro (RECORD_AUDIO) để trò chuyện trực tiếp với gia sư AI."
        }
    }

    state.sessionRecap?.let { recap ->
        SpeakingRecapDialog(
            recap = recap,
            onViewAiReview = { viewModel.dismissRecap(openDetailAfter = true) },
            onDismiss = { viewModel.dismissRecap(openDetailAfter = false) }
        )
    }

    if (state.showHistoryScreen) {
        SpeakingHistoryScreen(
            sessions = state.pastSessions,
            detail = state.historyDetail,
            onBackFromHistory = viewModel::closeHistory,
            onSelectSession = { viewModel.openHistoryDetail(it, triggerAutoEvaluation = false) },
            onBackFromDetail = viewModel::closeHistoryDetail,
            onReEvaluate = viewModel::evaluateHistorySession,
            onSaveVocab = { vocab ->
                haptics.confirm()
                viewModel.saveVocabToSrs(vocab)
            },
            onDeleteSession = viewModel::deleteHistorySession,
            onSpeakSample = viewModel::speakSample,
            onOpenSettings = onOpenSettings
        )
        return
    }

    if (state.isInCall) {
        LiveCallView(
            state = state,
            onToggleMute = viewModel::toggleMute,
            onToggleFallbackVoiceInput = viewModel::toggleFallbackVoiceInput,
            onToggleSubtitles = viewModel::toggleSubtitles,
            onSendFallbackMessage = viewModel::sendFallbackUserMessage,
            onEndCall = {
                haptics.confirm()
                viewModel.endSession(openReviewAfter = true)
            }
        )
        return
    }

    // Setup view
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Speaking Partner",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.activeMode == SpeakingMode.GEMINI_LIVE) {
                        "Gemini Live API sẵn sàng (Audio 2 chiều + AEC)"
                    } else {
                        "Chế độ dự phòng (Nhập Gemini API Key để bật Live Audio)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = viewModel::openHistory) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lịch sử (${state.pastSessions.size})")
            }
        }

        if (!state.hasApiKey) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nhập Gemini API Key (BYOK) để bật gọi thoại Live Audio & chấm điểm tự động.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cài đặt")
                    }
                }
            }
        }

        permissionDeniedMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // 1. Kịch bản hội thoại (5 kịch bản)
        Text("Kịch bản hội thoại", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeakingTopic.entries.forEach { topic ->
                FilterChip(
                    selected = state.selectedTopic == topic,
                    onClick = { viewModel.selectTopic(topic) },
                    label = { Text(topic.titleVi) }
                )
            }
        }

        if (state.selectedTopic == SpeakingTopic.FREE) {
            OutlinedTextField(
                value = state.customTopicText,
                onValueChange = viewModel::updateCustomTopic,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Chủ đề tự do (tùy chọn)") },
                placeholder = { Text("VD: Công nghệ AI, Thói quen đọc sách, Phim ảnh...") }
            )
        }

        // 2. Trình độ (Cơ bản / Trung cấp / Nâng cao)
        Text("Trình độ luyện nói", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeakingLevel.entries.forEach { level ->
                FilterChip(
                    selected = state.selectedLevel == level,
                    onClick = { viewModel.selectLevel(level) },
                    label = { Text("${level.titleVi} (${level.cefrHint})") }
                )
            }
        }

        val setupMascotLevel = MascotLevel.fromSpeakingLevel(state.selectedLevel)
        val setupMascotMood = when (state.selectedLevel) {
            SpeakingLevel.BEGINNER -> MascotMood.INQUISITIVE
            SpeakingLevel.INTERMEDIATE -> MascotMood.FOCUSED
            SpeakingLevel.ADVANCED -> MascotMood.SMART
        }
        val setupMascotBubble = when (state.selectedLevel) {
            SpeakingLevel.BEGINNER -> "Trình độ Cơ bản (${state.selectedLevel.cefrHint}): nói chậm rãi, câu ngắn dễ hiểu nhé!"
            SpeakingLevel.INTERMEDIATE -> "Trình độ Trung cấp (${state.selectedLevel.cefrHint}): chú Dúi sẽ gợi ý nối ý tự nhiên cùng bạn!"
            SpeakingLevel.ADVANCED -> "Trình độ Nâng cao (${state.selectedLevel.cefrHint}): cùng tranh luận sâu và trau chuốt từ vựng học thuật!"
        }
        DuiMascotView(
            state = MascotState(mood = setupMascotMood, level = setupMascotLevel),
            size = 64.dp,
            messageBubble = setupMascotBubble,
            modifier = Modifier.fillMaxWidth()
        )

        // 3. Giọng AI
        Text("Giọng gia sư AI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiVoice.entries.forEach { voice ->
                FilterChip(
                    selected = state.selectedVoice == voice,
                    onClick = { viewModel.selectVoice(voice) },
                    label = { Text(voice.voiceName) }
                )
            }
        }
        Text(
            text = "${state.selectedVoice.voiceName}: ${state.selectedVoice.descriptionVi}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                val hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    permissionDeniedMessage = null
                    viewModel.startCallWithMicPermission()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Bắt đầu gọi thoại AI")
        }

        if (state.connectionNote.isNotBlank()) {
            Text(
                text = state.connectionNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LiveCallView(
    state: SpeakingUiState,
    onToggleMute: () -> Unit,
    onToggleFallbackVoiceInput: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onSendFallbackMessage: (String) -> Unit,
    onEndCall: () -> Unit
) {
    var fallbackInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Call Header & Waveform Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                when {
                                    state.isLiveSocketActive && state.aecEnabled -> "Live PCM • AEC ON"
                                    state.isLiveSocketActive -> "Live Audio 16k/24k"
                                    state.hasApiKey -> "AI Giọng nói (STT + TTS)"
                                    else -> "Dự phòng Offline"
                                }
                            )
                        }
                    )
                    Text(
                        text = formatDuration(state.elapsedSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                DuiMascotView(
                    state = MascotState(
                        mood = MascotMood.FOCUSED,
                        level = MascotLevel.fromSpeakingLevel(state.selectedLevel)
                    ),
                    size = 72.dp
                )

                Text(
                    text = "${state.selectedTopic.titleVi} • ${state.selectedLevel.titleVi}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.connectionNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                VoiceWaveformVisualizer(
                    micAmplitude = if (state.isMuted) 0f else state.micAmplitude,
                    aiAmplitude = state.aiAmplitude,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(vertical = 4.dp)
                )

                // Call Controls: Mute (Live) or Voice Mic (AI Voice Fallback), Bilingual Subtitles Toggle, End Call
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isLiveSocketActive) {
                        FilledTonalIconButton(
                            onClick = onToggleMute,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (state.isMuted) "Mở mic" else "Tắt mic",
                                tint = if (state.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onToggleFallbackVoiceInput,
                            enabled = !state.isAiReplyingFallback,
                            colors = if (state.isListeningSpeechFallback) {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            }
                        ) {
                            Icon(
                                if (state.isListeningSpeechFallback) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                when {
                                    state.isAiReplyingFallback -> "AI đang đáp..."
                                    state.isListeningSpeechFallback -> "Dừng nghe"
                                    else -> "Nói với AI"
                                }
                            )
                        }
                    }

                    FilledTonalButton(onClick = onToggleSubtitles) {
                        Icon(
                            if (state.showBilingualSubtitles) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.showBilingualSubtitles) "Ẩn phụ đề" else "Hiện phụ đề")
                    }

                    Button(
                        onClick = onEndCall,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kết thúc")
                    }
                }
            }
        }

        // Live Bilingual Subtitles
        if (state.showBilingualSubtitles) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.activeMessages, key = { it.id }) { msg ->
                    val isUser = msg.speaker == "USER"
                    val viSubtitle = state.subtitleTranslations[msg.id]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isUser) "Bạn" else "Gia sư AI (${state.selectedVoice.voiceName})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(text = msg.transcript, style = MaterialTheme.typography.bodyMedium)
                            if (!isUser && !viSubtitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = viSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (state.liveUserDraft.isNotBlank()) {
                    item(key = "live_user_draft") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Bạn (đang nói...)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(state.liveUserDraft, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (state.liveAiDraft.isNotBlank()) {
                    item(key = "live_ai_draft") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Gia sư AI (đang trả lời...)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                Text(state.liveAiDraft, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Optional text input turn bar (handy in fallback mode or quiet environments)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = fallbackInput,
                onValueChange = { fallbackInput = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Hoặc nhập câu trả lời bằng chữ...") }
            )
            IconButton(
                onClick = {
                    if (fallbackInput.isNotBlank()) {
                        onSendFallbackMessage(fallbackInput)
                        fallbackInput = ""
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi")
            }
        }
    }
}

@Composable
private fun VoiceWaveformVisualizer(
    micAmplitude: Float,
    aiAmplitude: Float,
    modifier: Modifier = Modifier
) {
    val activeEnergy = maxOf(micAmplitude, aiAmplitude)
    val smoothLevel by animateFloatAsState(
        targetValue = activeEnergy.coerceIn(0.06f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "waveform_level"
    )
    val barColor: Color = if (aiAmplitude > micAmplitude) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Canvas(modifier = modifier) {
        val barCount = 15
        val centerIdx = barCount / 2
        val gap = 8.dp.toPx()
        val totalGap = gap * (barCount - 1)
        val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(4f)
        val maxHeight = size.height

        for (i in 0 until barCount) {
            val distanceFactor = 1f - (abs(i - centerIdx).toFloat() / (centerIdx + 1).toFloat())
            val waveScale = (0.25f + 0.75f * distanceFactor) * smoothLevel
            val barHeight = (maxHeight * waveScale).coerceIn(6.dp.toPx(), maxHeight)
            val left = i * (barWidth + gap)
            val top = (maxHeight - barHeight) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
