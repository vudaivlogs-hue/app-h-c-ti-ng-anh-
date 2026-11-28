package com.enziidict.app.feature.shadowing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.audio.DetailedPronunciationResult
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.WordMatchStatus
import com.enziidict.app.core.network.ShadowingAiFeedback
import com.enziidict.app.ui.components.BentoCard
import com.enziidict.app.ui.components.DuiMascotView
import com.enziidict.app.ui.components.MascotMood
import com.enziidict.app.ui.components.MascotState
import com.enziidict.app.ui.components.rememberEnziiHaptics
import java.util.Locale
import kotlin.math.abs

private enum class PendingMicAction {
    NONE,
    RECORD_MANUAL,
    AUTO_COUNTDOWN_RECORD,
    SPEECH_RECOGNIZE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShadowingScreen(
    initialItem: ShadowingItem? = null,
    onInitialItemConsumed: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: ShadowingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberEnziiHaptics()
    var pendingMicAction by remember { mutableStateOf(PendingMicAction.NONE) }
    var micDeniedError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialItem) {
        if (initialItem != null) {
            viewModel.loadExternalItem(initialItem)
            onInitialItemConsumed()
        }
    }

    // Strict lifecycle cleanup: release MediaRecorder, MediaPlayer, SpeechRecognizer & TTS
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.releaseOnExit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.releaseOnExit()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingMicAction
        pendingMicAction = PendingMicAction.NONE
        if (granted) {
            micDeniedError = null
            when (action) {
                PendingMicAction.AUTO_COUNTDOWN_RECORD -> viewModel.startCountdownThenRecord()
                PendingMicAction.RECORD_MANUAL -> viewModel.startRecordingNow()
                PendingMicAction.SPEECH_RECOGNIZE -> viewModel.toggleSpeechRecognitionCheck()
                PendingMicAction.NONE -> Unit
            }
        } else {
            micDeniedError = "Cần cấp quyền Micro (RECORD_AUDIO) để thu âm và nhận diện giọng nói."
        }
    }

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Luyện Shadowing", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.releaseOnExit()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    FilterChip(
                        selected = state.showVietnamese,
                        onClick = {
                            haptics.lightTap()
                            viewModel.toggleVietnamese()
                        },
                        leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(if (state.showVietnamese) "Hiện nghĩa" else "Ẩn nghĩa") },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Quick Topic Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Chủ đề luyện nhanh",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShadowingCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = state.selectedCategory == cat,
                            onClick = {
                                haptics.lightTap()
                                viewModel.selectCategory(cat)
                            },
                            label = { Text(cat.labelVi) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.categoryItems.forEachIndexed { idx, item ->
                        val isSelected = state.activeItem.id == item.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptics.lightTap()
                                viewModel.selectItem(item)
                            },
                            label = { Text("Câu ${idx + 1}") }
                        )
                    }
                }
            }

            // Progress Log Strip for this sentence
            state.practiceLog?.let { log ->
                PracticeProgressCard(
                    log = log,
                    onTapTrickyWord = { word ->
                        haptics.lightTap()
                        viewModel.speakChunk(word)
                    }
                )
            }

            // Mascot Companion Strip reacting to listening / recording / scoring
            val shadowingScore = state.wordDiffResult?.scorePercent ?: state.aiFeedback?.fluencyScore ?: 0
            val shadowingFocused = state.isPlayingModel ||
                state.isRecording ||
                state.isSpeechListening ||
                state.isEvaluatingAi ||
                state.isComparingSequence
            val shadowingMood = when {
                shadowingFocused -> MascotMood.FOCUSED
                shadowingScore >= 80 -> MascotMood.HAPPY
                else -> MascotMood.INQUISITIVE
            }
            val shadowingBubble = when {
                state.isRecording || state.isSpeechListening -> "Chú Dúi đang lắng nghe từng cụm từ của bạn..."
                state.isPlayingModel -> "Lắng nghe ngữ điệu mẫu thật kỹ nhé!"
                state.isEvaluatingAi -> "Chú Dúi đang nhờ AI chấm trọng âm & nối âm..."
                shadowingScore >= 80 -> "Phát âm đạt $shadowingScore%! Tiếp tục giữ phong độ nhé!"
                else -> "Nghe mẫu từng cụm rồi đọc nhại theo chú Dúi nào!"
            }
            DuiMascotView(
                state = MascotState(mood = shadowingMood),
                size = 64.dp,
                messageBubble = shadowingBubble,
                modifier = Modifier.fillMaxWidth()
            )

            // 2. Hero Sentence & Rhythm Chunks Card
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = state.activeItem.sourceLabel,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "Chạm cụm từ để nghe từng nhịp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = state.activeItem.sentenceEn,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.activeItem.chunks.forEachIndexed { idx, chunk ->
                            Surface(
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.speakChunk(chunk)
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = chunk,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (idx < state.activeItem.chunks.lastIndex) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "/",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.activeItem.ipa.isNotBlank()) {
                        Text(
                            text = state.activeItem.ipa,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(
                        visible = state.showVietnamese && state.activeItem.meaningVi.isNotBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            Text(
                                text = state.activeItem.meaningVi,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Model Audio Controls Card
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hearing, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nghe mẫu (Model Audio)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TtsAccent.entries.forEach { acc ->
                            FilterChip(
                                selected = state.accent == acc,
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.setAccent(acc)
                                },
                                label = { Text("Giọng ${acc.label}") }
                            )
                        }
                        ShadowingSpeed.entries.forEach { sp ->
                            FilterChip(
                                selected = state.speed == sp,
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.setSpeed(sp)
                                },
                                label = { Text(sp.label) }
                            )
                        }
                    }

                    Text(
                        text = "Tốc độ ${state.speed.label}: ${state.speed.hintVi}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tự động thu sau khi nghe (đếm 3-2-1)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.autoRecordAfterListen,
                            onCheckedChange = {
                                haptics.lightTap()
                                viewModel.toggleAutoRecordAfterListen()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptics.lightTap()
                                viewModel.toggleModelAudio(
                                    hasMicPermission = hasMicPermission(),
                                    onNeedMicPermission = {
                                        pendingMicAction = PendingMicAction.AUTO_COUNTDOWN_RECORD
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            },
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                if (state.isPlayingModel) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.isPlayingModel) "Tạm dừng" else "Nghe mẫu")
                        }

                        FilterChip(
                            selected = state.isLoopEnabled,
                            onClick = {
                                haptics.lightTap()
                                viewModel.toggleLoop()
                            },
                            leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Lặp lại") }
                        )
                    }
                }
            }

            // 4. Record & Playback Comparison Card
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = if (state.isRecording) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ghi âm & Nghe đối chiếu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.isRecording) {
                            Text(
                                text = formatRecordMillis(state.recordingDurationMs),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    state.countdownSec?.let { sec ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Text(
                                text = "Chuẩn bị nhại theo câu sau: $sec...",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    if (state.isRecording) {
                        RecordingWaveBar(
                            amplitude = state.recordingAmplitude,
                            barColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (state.isRecording) {
                                haptics.confirm()
                                viewModel.stopRecordingNow()
                            } else if (hasMicPermission()) {
                                haptics.lightTap()
                                viewModel.startRecordingNow()
                            } else {
                                pendingMicAction = PendingMicAction.RECORD_MANUAL
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = if (state.isRecording) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        }
                    ) {
                        Icon(
                            if (state.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isRecording) "Dừng ghi âm" else "Ghi âm giọng của bạn")
                    }

                    if (state.hasLatestTake || state.hasBestTake) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.toggleModelAudio(
                                        hasMicPermission = hasMicPermission(),
                                        onNeedMicPermission = {}
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Giọng mẫu", maxLines = 1)
                            }
                            FilledTonalButton(
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.playUserAudio(useBestTake = !state.hasLatestTake)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (state.isPlayingUserAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (state.isPlayingUserAudio) "Dừng" else "Giọng của bạn",
                                    maxLines = 1
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                haptics.lightTap()
                                viewModel.playSideBySideCompare()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (state.isComparingSequence) {
                                    "Dừng phát đối chiếu"
                                } else {
                                    "Nghe lần lượt: Giọng mẫu → Giọng của bạn"
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.confirm()
                                viewModel.saveBestTake()
                            },
                            enabled = state.hasLatestTake,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (state.hasBestTake) "Cập nhật bản hay" else "Lưu bản tốt nhất",
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.confirm()
                                viewModel.saveCurrentItemToSrs()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lưu Sổ tay", maxLines = 1)
                        }
                    }
                }
            }

            // 5. AI Pronunciation Coach & Word-by-Word Comparison Card
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Chấm điểm & Mẹo Nối âm AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (state.hasApiKey) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = if (state.hasApiKey) "AI + Offline" else "Thuần Offline",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (hasMicPermission()) {
                                    haptics.lightTap()
                                    viewModel.toggleSpeechRecognitionCheck()
                                } else {
                                    pendingMicAction = PendingMicAction.SPEECH_RECOGNIZE
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (state.isSpeechListening) "Dừng nghe..." else "Đọc chấm điểm (ASR)",
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                haptics.lightTap()
                                viewModel.analyzeWithGeminiAi()
                            },
                            enabled = !state.isEvaluatingAi && (state.hasLatestTake || state.hasBestTake || state.recognizedTranscript.isNotBlank()),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isEvaluatingAi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Phân tích AI", maxLines = 1)
                        }
                    }

                    if (state.isSpeechListening) {
                        RecordingWaveBar(
                            amplitude = state.speechRmsAmplitude,
                            barColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        )
                        if (state.partialSpeechText.isNotBlank()) {
                            Text(
                                text = "Đang nghe: \"${state.partialSpeechText}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    state.wordDiffResult?.let { diff ->
                        WordDiffHighlightSection(
                            result = diff,
                            recognizedTranscript = state.recognizedTranscript,
                            onTapWord = { word ->
                                haptics.lightTap()
                                viewModel.speakChunk(word)
                            }
                        )
                    }

                    state.aiFeedback?.let { feedback ->
                        AiPronunciationFeedbackSection(feedback = feedback)
                    }
                }
            }

            // Status / Error feedback
            val activeError = micDeniedError ?: state.errorMessage
            if (activeError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = activeError,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

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

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeProgressCard(
    log: ShadowingPracticeLog,
    onTapTrickyWord: (String) -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tiến bộ câu này (${log.attemptsCount} lần thử)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Gần nhất: ${log.lastScore} • Cao nhất: ${log.bestScore}/100",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (log.trickyWords.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Từ hay vấp:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    log.trickyWords.forEach { w ->
                        AssistChip(
                            onClick = { onTapTrickyWord(w) },
                            label = { Text(w) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordDiffHighlightSection(
    result: DetailedPronunciationResult,
    recognizedTranscript: String,
    onTapWord: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Đối chiếu từng từ (chạm từ để nghe lại)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "Khớp ${result.scorePercent}%",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Color legend
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("● Đúng", style = MaterialTheme.typography.labelSmall, color = Color(0xFF15803D))
            Text("● Đọc lệch", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB45309))
            Text("● Bỏ sót", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB91C1C))
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            result.wordMatches.forEach { match ->
                val (bg, fg, deco) = when (match.status) {
                    WordMatchStatus.CORRECT -> Triple(
                        Color(0xFFD8F5E3),
                        Color(0xFF0D6832),
                        TextDecoration.None
                    )
                    WordMatchStatus.MISPRONOUNCED -> Triple(
                        Color(0xFFFEF3C7),
                        Color(0xFF92400E),
                        TextDecoration.Underline
                    )
                    WordMatchStatus.MISSED -> Triple(
                        Color(0xFFFEE2E2),
                        Color(0xFF991B1B),
                        TextDecoration.LineThrough
                    )
                }

                Surface(
                    onClick = { onTapWord(match.normalizedWord) },
                    shape = RoundedCornerShape(10.dp),
                    color = bg
                ) {
                    Text(
                        text = match.displayWord,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = fg,
                        textDecoration = deco
                    )
                }
            }
        }

        if (recognizedTranscript.isNotBlank()) {
            Text(
                text = "Nhận diện được: \"$recognizedTranscript\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AiPronunciationFeedbackSection(feedback: ShadowingAiFeedback) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Phản hồi Trọng âm & Nối âm AI",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "${feedback.fluencyScore}/100",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        if (feedback.summaryVi.isNotBlank()) {
            Text(text = feedback.summaryVi, style = MaterialTheme.typography.bodyMedium)
        }

        if (feedback.stressNotes.isNotEmpty()) {
            Text(
                text = "Trọng âm (Stress) & từ dễ đọc lệch",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            feedback.stressNotes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (feedback.linkingAndRhythmTips.isNotEmpty()) {
            Text(
                text = "Mẹo nối âm (Linking), giảm âm & nhịp điệu",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            feedback.linkingAndRhythmTips.forEach { tip ->
                Text("• $tip", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RecordingWaveBar(
    amplitude: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val smooth by animateFloatAsState(
        targetValue = amplitude.coerceIn(0.08f, 1f),
        animationSpec = tween(durationMillis = 75),
        label = "shadowing_rec_wave"
    )

    Canvas(modifier = modifier) {
        val bars = 17
        val center = bars / 2
        val gap = 6.dp.toPx()
        val width = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(3f)
        val maxH = size.height

        for (i in 0 until bars) {
            val falloff = 1f - (abs(i - center).toFloat() / (center + 1).toFloat())
            val h = (maxH * (0.2f + 0.8f * falloff) * smooth).coerceIn(5.dp.toPx(), maxH)
            val left = i * (width + gap)
            val top = (maxH - h) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(width, h),
                cornerRadius = CornerRadius(width / 2f, width / 2f)
            )
        }
    }
}

private fun formatRecordMillis(ms: Long): String {
    val totalSec = (ms / 1000L).toInt()
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
