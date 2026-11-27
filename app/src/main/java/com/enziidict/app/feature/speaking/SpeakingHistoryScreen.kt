package com.enziidict.app.feature.speaking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enziidict.app.core.model.SuggestedVocab
import com.enziidict.app.data.local.entity.SpeakingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpeakingHistoryScreen(
    sessions: List<SpeakingSession>,
    detail: HistoryDetailUiState?,
    onBackFromHistory: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onBackFromDetail: () -> Unit,
    onReEvaluate: (Long) -> Unit,
    onSaveVocab: (SuggestedVocab) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onSpeakSample: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    if (detail != null) {
        SpeakingSessionDetailView(
            detail = detail,
            onBack = onBackFromDetail,
            onReEvaluate = { onReEvaluate(detail.session.id) },
            onSaveVocab = onSaveVocab,
            onSpeakSample = onSpeakSample,
            onOpenSettings = onOpenSettings
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackFromHistory) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }
            Column {
                Text(
                    text = "Lịch sử & Đánh giá buổi nói",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${sessions.size} phiên đã lưu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Chưa có buổi luyện nói nào.\nHãy bắt đầu một cuộc gọi AI để hệ thống tự động lưu lịch sử và phân tích.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sessions, key = { it.id }) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSession(session.id) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.topic,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${formatDateTime(session.startedAt)} • ${session.durationSec}s • ${session.voiceName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (!session.correctionSuggestionsJson.isNullOrBlank()) {
                                        "Đã có đánh giá AI"
                                    } else {
                                        "Chạm để xem transcript & phân tích AI"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDeleteSession(session.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa phiên")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakingSessionDetailView(
    detail: HistoryDetailUiState,
    onBack: () -> Unit,
    onReEvaluate: () -> Unit,
    onSaveVocab: (SuggestedVocab) -> Unit,
    onSpeakSample: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                }
                Column {
                    Text(
                        text = detail.session.topic,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${formatDateTime(detail.session.startedAt)} • ${detail.session.durationSec}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onReEvaluate,
                enabled = !detail.isEvaluating
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Phân tích")
            }
        }

        detail.actionMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Evaluation Card
        if (detail.isEvaluating) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Gemini đang phân tích transcript và gợi ý cách nói tự nhiên hơn...")
                }
            }
        } else if (detail.evaluationError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = detail.evaluationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReEvaluate) { Text("Thử phân tích lại") }
                        OutlinedButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cài đặt")
                        }
                    }
                }
            }
        }

        detail.analysis?.let { analysis ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Nhận xét tổng quan AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        AssistChip(onClick = {}, label = { Text("${analysis.scoreOutOf10}/10") })
                    }
                    Text(analysis.overallFeedbackVi, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (analysis.mistakes.isNotEmpty()) {
                Text(
                    text = "Gợi ý sửa câu tự nhiên hơn (${analysis.mistakes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                analysis.mistakes.forEach { mistake ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Bạn nói: ${mistake.original}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tự nhiên hơn: ${mistake.correction}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onSpeakSample(mistake.correction) }) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Nghe câu gợi ý")
                                }
                            }
                            Text(
                                text = mistake.explanationVi,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (analysis.suggestedVocabulary.isNotEmpty()) {
                Text(
                    text = "Từ vựng mới trong hội thoại",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                analysis.suggestedVocabulary.forEach { vocab ->
                    val alreadySaved = vocab.word.lowercase() in detail.savedWordsSet
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${vocab.word} (${vocab.pos})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = vocab.meaningVi,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onSpeakSample(vocab.word) }) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Phát âm")
                                }
                                FilledTonalButton(
                                    onClick = { onSaveVocab(vocab) },
                                    enabled = !alreadySaved
                                ) {
                                    Icon(
                                        if (alreadySaved) Icons.Default.Check else Icons.Default.BookmarkAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (alreadySaved) "Đã lưu" else "Lưu SRS")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full Transcript
        HorizontalDivider()
        Text(
            text = "Nội dung hội thoại (${detail.messages.size} lượt)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        detail.messages.forEach { msg ->
            val isUser = msg.speaker == "USER"
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isUser) "Bạn" else "Gia sư AI",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = msg.transcript, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!isUser) {
                        IconButton(onClick = { onSpeakSample(msg.transcript) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Nghe lại")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
