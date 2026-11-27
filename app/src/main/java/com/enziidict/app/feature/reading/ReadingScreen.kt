package com.enziidict.app.feature.reading

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.feature.shadowing.ShadowingItem
import com.enziidict.app.ui.components.CefrPill
import com.enziidict.app.ui.components.IpaAudioPill
import com.enziidict.app.ui.components.PosPill
import com.enziidict.app.ui.components.rememberEnziiHaptics

private val WORD_REGEX = Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*")
private val CEFR_LEVELS = listOf("A2", "B1", "B2", "C1")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    onBackToHome: (() -> Unit)? = null,
    onOpenShadowing: (ShadowingItem) -> Unit = {},
    viewModel: ReadingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberEnziiHaptics()

    val activeArticle = state.activeArticle
    if (activeArticle == null) {
        ReadingListView(
            selectedLevel = state.selectedLevel,
            articles = state.articles,
            onBackToHome = onBackToHome,
            onSelectLevel = viewModel::selectLevelFilter,
            onOpenArticle = viewModel::openArticle
        )
    } else {
        BilingualReaderView(
            article = activeArticle,
            showVietnameseGlobal = state.showVietnameseGlobal,
            revealedParagraphIndices = state.revealedParagraphIndices,
            onBack = viewModel::closeArticle,
            onToggleGlobalVietnamese = viewModel::toggleGlobalVietnamese,
            onToggleParagraphVietnamese = viewModel::toggleParagraphVietnamese,
            onWordTapped = { w ->
                haptics.lightTap()
                viewModel.onWordTapped(w)
            },
            onSpeakParagraph = {
                haptics.lightTap()
                viewModel.speakText(it, TtsAccent.US, rate = 0.92f)
            },
            onShadowingParagraph = { paraIndex, en, vi ->
                haptics.lightTap()
                onOpenShadowing(
                    ShadowingItem(
                        id = "reading_${activeArticle.id}_$paraIndex",
                        sentenceEn = en,
                        meaningVi = vi,
                        sourceLabel = "Đọc ${activeArticle.cefrLevel} • Đoạn ${paraIndex + 1}",
                        keywordForSrs = en.split(" ").take(4).joinToString(" ")
                    )
                )
            },
            onMarkCompleted = {
                haptics.confirm()
                viewModel.markArticleProgress(activeArticle.id, 1f)
            }
        )
    }

    val sheetState = state.lookupSheet
    if (sheetState != null) {
        val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissLookupSheet,
            sheetState = modalSheetState
        ) {
            WordLookupBottomSheetContent(
                state = sheetState,
                folders = state.availableFolders,
                onSelectFolder = viewModel::selectSaveFolder,
                onSpeak = { word, accent ->
                    haptics.lightTap()
                    viewModel.speakText(word, accent, rate = 1.0f)
                },
                onSaveEntry = { entry ->
                    haptics.confirm()
                    viewModel.saveEntryToSelectedFolder(entry)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingListView(
    selectedLevel: String?,
    articles: List<BilingualArticleUi>,
    onBackToHome: (() -> Unit)? = null,
    onSelectLevel: (String?) -> Unit,
    onOpenArticle: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBackToHome != null) {
                IconButton(onClick = onBackToHome) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                }
            }
            Text(
                text = "Đọc Song ngữ (A2 – C1)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "Chọn bài đọc để luyện đọc hiểu và chạm vào bất kỳ từ nào để tra nghĩa + lưu vào SRS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedLevel == null,
                onClick = { onSelectLevel(null) },
                label = { Text("Tất cả") }
            )
            CEFR_LEVELS.forEach { level ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { onSelectLevel(if (selectedLevel == level) null else level) },
                    label = { Text(level) }
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(articles, key = { it.id }) { article ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenArticle(article.id) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = article.category,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            CefrPill(article.cefrLevel)
                        }
                        Text(
                            text = article.titleEn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = article.titleVi,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${article.paragraphs.size} đoạn",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(article.readProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { article.readProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BilingualReaderView(
    article: BilingualArticleUi,
    showVietnameseGlobal: Boolean,
    revealedParagraphIndices: Set<Int>,
    onBack: () -> Unit,
    onToggleGlobalVietnamese: () -> Unit,
    onToggleParagraphVietnamese: (Int) -> Unit,
    onWordTapped: (String) -> Unit,
    onSpeakParagraph: (String) -> Unit,
    onShadowingParagraph: (index: Int, en: String, vi: String) -> Unit = { _, _, _ -> },
    onMarkCompleted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                }
                AssistChip(onClick = {}, label = { Text(article.cefrLevel) })
            }
            FilterChip(
                selected = showVietnameseGlobal,
                onClick = onToggleGlobalVietnamese,
                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                label = { Text(if (showVietnameseGlobal) "Đang hiện dịch" else "Đang ẩn dịch") }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
        ) {
            Text(
                text = "Mẹo: Chạm trực tiếp vào bất kỳ từ tiếng Anh nào để tra nghĩa tức thì, nghe phát âm UK/US và lưu vào Sổ tay SRS.",
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = article.titleEn,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (showVietnameseGlobal) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.titleVi,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(article.paragraphs) { index, para ->
                val showParaVi = showVietnameseGlobal || (index in revealedParagraphIndices)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TappableEnglishParagraph(
                            text = para.en,
                            onWordTapped = onWordTapped
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onSpeakParagraph(para.en) }) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nghe đoạn ${index + 1}")
                                }
                                TextButton(onClick = { onShadowingParagraph(index, para.en, para.vi) }) {
                                    Text("Shadowing")
                                }
                            }
                            if (!showVietnameseGlobal) {
                                TextButton(onClick = { onToggleParagraphVietnamese(index) }) {
                                    Text(if (showParaVi) "Ẩn dịch" else "Xem dịch")
                                }
                            }
                        }

                        if (showParaVi) {
                            HorizontalDivider()
                            Text(
                                text = para.vi,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onMarkCompleted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (article.readProgress >= 1f) "Đã hoàn thành bài đọc" else "Đánh dấu đã đọc xong")
                }
            }
        }
    }
}

@Composable
private fun TappableEnglishParagraph(
    text: String,
    onWordTapped: (String) -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.onSurface
    val pressedColor = MaterialTheme.colorScheme.primary

    val annotated = remember(text, highlightColor, pressedColor) {
        buildAnnotatedString {
            var cursor = 0
            for (match in WORD_REGEX.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1
                if (start > cursor) {
                    append(text.substring(cursor, start))
                }
                val word = match.value
                pushLink(
                    LinkAnnotation.Clickable(
                        tag = word,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = highlightColor,
                                textDecoration = TextDecoration.None
                            ),
                            pressedStyle = SpanStyle(
                                color = pressedColor,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { onWordTapped(word) }
                    )
                )
                append(word)
                pop()
                cursor = end
            }
            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordLookupBottomSheetContent(
    state: WordLookupSheetState,
    folders: List<String>,
    onSelectFolder: (String) -> Unit,
    onSpeak: (String, TtsAccent) -> Unit,
    onSaveEntry: (DictionaryEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Tra từ tại chỗ: \"${state.tappedWord}\"",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        if (state.isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        val entry = state.matches.firstOrNull()
        if (entry == null) {
            Text(
                text = "Chưa tìm thấy mục từ \"${state.tappedWord}\" trong bộ dữ liệu offline hiện tại.",
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onSpeak(state.tappedWord, TtsAccent.UK) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                    label = { Text("Nghe UK") }
                )
                AssistChip(
                    onClick = { onSpeak(state.tappedWord, TtsAccent.US) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                    label = { Text("Nghe US") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.word,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PosPill(entry.pos)
                    entry.cefrLevel?.let { CefrPill(it) }
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IpaAudioPill(
                accentLabel = "UK",
                ipa = entry.ipaUk,
                onClick = { onSpeak(entry.word, TtsAccent.UK) }
            )
            IpaAudioPill(
                accentLabel = "US",
                ipa = entry.ipaUs,
                onClick = { onSpeak(entry.word, TtsAccent.US) }
            )
        }

        Text(
            text = entry.meaningVi,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (entry.examples.isNotEmpty()) {
            HorizontalDivider()
            entry.examples.take(2).forEach { ex ->
                Text("• ${ex.en}", style = MaterialTheme.typography.bodyMedium)
                if (ex.vi.isNotBlank()) {
                    Text(
                        "  ${ex.vi}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (entry.synonyms.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.synonyms.take(4).forEach { syn ->
                    SuggestionChip(onClick = {}, label = { Text(syn) })
                }
            }
        }

        HorizontalDivider()
        Text("Chọn thư mục lưu SRS:", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            folders.forEach { folder ->
                FilterChip(
                    selected = state.selectedFolder == folder,
                    onClick = { onSelectFolder(folder) },
                    label = { Text(folder) }
                )
            }
        }

        Button(
            onClick = { onSaveEntry(entry) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Lưu vào \"${state.selectedFolder}\"")
        }

        state.saveStatusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
