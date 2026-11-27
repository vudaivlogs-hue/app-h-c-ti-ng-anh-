package com.enziidict.app.feature.translate

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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.network.GrammarAnalysisResult

private val SAMPLE_EN_SENTENCES = listOf(
    "Had I known about the traffic, I would have taken the train.",
    "Let's call it a day and pick this up tomorrow morning."
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslateScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: TranslateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Dịch thuật Hybrid & Ngữ pháp AI",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Direction switcher
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.enToVi) "Tiếng Anh" else "Tiếng Việt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = viewModel::swapDirection) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Đảo chiều dịch")
                }
                Text(
                    text = if (state.enToVi) "Tiếng Việt" else "Tiếng Anh",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Input box
        OutlinedTextField(
            value = state.inputText,
            onValueChange = viewModel::onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            placeholder = {
                Text(
                    if (state.enToVi) {
                        "Nhập câu hoặc đoạn tiếng Anh..."
                    } else {
                        "Nhập câu tiếng Việt cần dịch sang tiếng Anh..."
                    }
                )
            },
            trailingIcon = {
                if (state.inputText.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearInput) {
                        Icon(Icons.Default.Clear, contentDescription = "Xóa")
                    }
                }
            }
        )

        if (state.enToVi && state.inputText.isBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SAMPLE_EN_SENTENCES.forEachIndexed { idx, sample ->
                    SuggestionChip(
                        onClick = { viewModel.onInputChange(sample) },
                        label = { Text("Câu mẫu ${idx + 1}") }
                    )
                }
            }
        }

        // Action buttons: Tier 1 (Offline ML Kit) & Tier 2 (Gemini Deep Analysis)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = viewModel::translateOffline,
                enabled = !state.isOfflineTranslating,
                modifier = Modifier.weight(1f)
            ) {
                if (state.isOfflineTranslating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Translate, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Dịch Offline")
            }

            Button(
                onClick = viewModel::analyzeGrammarWithAi,
                enabled = !state.isAnalyzingAi,
                modifier = Modifier.weight(1.35f)
            ) {
                if (state.isAnalyzingAi) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Phân tích ngữ pháp & sắc thái từ", maxLines = 1)
            }
        }

        // Tier 1 Offline Error
        state.offlineError?.let { err ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    OutlinedButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Quản lý gói offline")
                    }
                }
            }
        }

        // Tier 1 Offline Translation Result
        if (state.offlineTranslation.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(onClick = {}, label = { Text("Tầng 1 • ML Kit Offline") })
                        if (!state.enToVi) {
                            IconButton(onClick = { viewModel.speakEnglish(state.offlineTranslation) }) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Nghe tiếng Anh")
                            }
                        }
                    }
                    Text(
                        text = state.offlineTranslation,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Tier 2 AI Error (Missing API Key or Network/API failure)
        when (val err = state.aiError) {
            AiAnalysisError.MissingApiKey -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Chưa cấu hình Gemini API Key",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = "Tính năng Phân tích ngữ pháp & sắc thái từ cần Gemini API Key cá nhân (lưu mã hóa trên máy). Bạn vẫn có thể dùng Dịch Offline miễn phí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mở Cài đặt nhập API Key")
                        }
                    }
                }
            }
            is AiAnalysisError.Message -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = err.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(onClick = viewModel::analyzeGrammarWithAi) {
                            Text("Thử lại")
                        }
                    }
                }
            }
            null -> Unit
        }

        // Tier 2 AI Deep Analysis Result
        state.aiAnalysis?.let { analysis ->
            AiGrammarAnalysisSection(
                analysis = analysis,
                modelName = state.selectedModel,
                onSpeakEnglish = viewModel::speakEnglish
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AiGrammarAnalysisSection(
    analysis: GrammarAnalysisResult,
    modelName: String,
    onSpeakEnglish: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AssistChip(
                onClick = {},
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                label = { Text("Tầng 2 • AI Deep Analysis ($modelName)") }
            )

            Text(
                text = "Bản dịch tự nhiên",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = analysis.naturalTranslation,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (analysis.sentenceStructure.isNotBlank()) {
                HorizontalDivider()
                Text(
                    text = "Cấu trúc câu & Thì ngữ pháp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(analysis.sentenceStructure, style = MaterialTheme.typography.bodyMedium)
            }

            if (analysis.grammarPoints.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Điểm ngữ pháp nổi bật",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                analysis.grammarPoints.forEach { gp ->
                    Text("• ${gp.pattern}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "  ${gp.explanationVi}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (analysis.phrasesAndIdioms.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Cụm từ, Idioms & Sắc thái từ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                analysis.phrasesAndIdioms.forEach { ph ->
                    Text(
                        "• ${ph.phrase} — ${ph.meaningVi}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (ph.nuanceVi.isNotBlank()) {
                        Text(
                            "  Sắc thái: ${ph.nuanceVi}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (analysis.naturalAlternatives.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Gợi ý diễn đạt tự nhiên hơn",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                analysis.naturalAlternatives.forEach { alt ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "• ${alt.expression}",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "  ${alt.contextVi}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onSpeakEnglish(alt.expression) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Nghe mẫu")
                        }
                    }
                }
            }
        }
    }
}
