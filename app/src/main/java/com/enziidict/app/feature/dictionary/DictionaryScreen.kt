package com.enziidict.app.feature.dictionary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.network.OnlineWordExpansion
import com.enziidict.app.data.local.entity.WordFolder
import com.enziidict.app.feature.shadowing.ShadowingItem
import com.enziidict.app.ui.components.BentoCard
import com.enziidict.app.ui.components.CefrPill
import com.enziidict.app.ui.components.EnziiLoadingCard
import com.enziidict.app.ui.components.IpaAudioPill
import com.enziidict.app.ui.components.PosPill
import com.enziidict.app.ui.components.SaveToFolderBottomSheet
import com.enziidict.app.ui.components.formatSavedFoldersButtonLabel
import com.enziidict.app.ui.components.rememberEnziiHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DictionaryScreen(
    onOpenShadowing: (ShadowingItem) -> Unit = {},
    viewModel: DictionaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberEnziiHaptics()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopAudio()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopAudio()
        }
    }

    val trimmedQuery = state.query.trim()
    val hasExactOfflineMatch = trimmedQuery.isNotEmpty() &&
        state.entries.any { it.word.equals(trimmedQuery, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            placeholder = { Text("Tra từ Anh - Việt, cụm từ hoặc gõ tiếng Việt...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Xóa")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (trimmedQuery.isNotEmpty() && (state.entries.isEmpty() || !hasExactOfflineMatch)) {
                        viewModel.searchOnlineForCurrentQuery()
                    }
                }
            )
        )

        AnimatedVisibility(
            visible = state.statusMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut()
        ) {
            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading && state.entries.isEmpty() -> {
                EnziiLoadingCard(message = "Đang tìm kiếm trong từ điển offline...")
            }
            !state.isLoading && state.entries.isEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.isOnlineSearching) {
                        EnziiLoadingCard(message = "Đang tìm trực tuyến cho \"$trimmedQuery\"...")
                    } else {
                        OnlineSearchPromptCard(
                            query = trimmedQuery,
                            isSearching = false,
                            onSearchOnline = {
                                haptics.confirm()
                                viewModel.searchOnlineForCurrentQuery()
                            }
                        )
                    }

                    state.onlineErrorMessage?.let { err ->
                        OnlineErrorBanner(
                            message = err,
                            onRetry = {
                                haptics.lightTap()
                                viewModel.searchOnlineForCurrentQuery()
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.onQueryChange("") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Xóa từ khóa & quay lại danh sách phổ biến")
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (trimmedQuery.length >= 2 && !hasExactOfflineMatch) {
                        item(key = "online_prompt_banner") {
                            OnlineSearchPromptCard(
                                query = trimmedQuery,
                                isSearching = state.isOnlineSearching,
                                compact = true,
                                onSearchOnline = {
                                    haptics.confirm()
                                    viewModel.searchOnlineForCurrentQuery()
                                }
                            )
                        }
                    }

                    state.onlineErrorMessage?.let { err ->
                        if (state.selectedEntry == null || !hasExactOfflineMatch) {
                            item(key = "online_error_top") {
                                OnlineErrorBanner(
                                    message = err,
                                    onRetry = { viewModel.searchOnlineForCurrentQuery() }
                                )
                            }
                        }
                    }

                    state.selectedEntry?.let { detail ->
                        val wordKey = detail.word.lowercase()
                        val expansion = state.onlineExpansions[wordKey]
                        val isOnlineSource = (detail.id in state.onlineCachedEntryIds) ||
                            expansion != null ||
                            detail.frequencyRank == 25_000
                        val isExpanding = state.expandingWordKey == wordKey

                        item(key = "detail_${detail.id}") {
                            WordDetailCard(
                                entry = detail,
                                savedFolders = state.savedFoldersForSelected,
                                isOnlineSource = isOnlineSource,
                                onlineExpansion = expansion,
                                isExpandingOnline = isExpanding,
                                onlineError = if (hasExactOfflineMatch) state.onlineErrorMessage else null,
                                onSpeakUk = {
                                    haptics.lightTap()
                                    viewModel.speakWithPreferredAudio(
                                        word = detail.word,
                                        accent = TtsAccent.UK,
                                        audioUrl = expansion?.audioUrlUk
                                    )
                                },
                                onSpeakUs = {
                                    haptics.lightTap()
                                    viewModel.speakWithPreferredAudio(
                                        word = detail.word,
                                        accent = TtsAccent.US,
                                        audioUrl = expansion?.audioUrlUs
                                    )
                                },
                                onPlayOnlineAudio = { url, accent ->
                                    haptics.lightTap()
                                    viewModel.speakWithPreferredAudio(detail.word, accent, url)
                                },
                                onExpandOnline = {
                                    haptics.lightTap()
                                    viewModel.expandSelectedWordOnline()
                                },
                                onOpenSaveSheet = {
                                    haptics.lightTap()
                                    viewModel.openSaveFolderSheet()
                                },
                                onShadowingSentence = { sentence, meaning, keyword ->
                                    haptics.lightTap()
                                    onOpenShadowing(
                                        ShadowingItem(
                                            id = "dict_${detail.id}_${sentence.hashCode()}",
                                            sentenceEn = sentence,
                                            meaningVi = meaning,
                                            ipa = detail.ipaUs.ifBlank { detail.ipaUk },
                                            sourceLabel = "Từ điển • ${detail.word}",
                                            keywordForSrs = keyword
                                        )
                                    )
                                }
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Gợi ý (${state.entries.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(state.entries, key = { it.id }) { entry ->
                        val isSelected = state.selectedEntry?.id == entry.id
                        val entryCachedOnline = (entry.id in state.onlineCachedEntryIds) ||
                            entry.frequencyRank == 25_000
                        BentoCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            onClick = {
                                haptics.lightTap()
                                viewModel.selectEntry(entry)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = entry.word,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        PosPill(entry.pos)
                                        SourceBadgePill(isOnline = entryCachedOnline)
                                    }
                                    Text(
                                        text = entry.meaningVi,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                entry.cefrLevel?.let { level ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CefrPill(level)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val selected = state.selectedEntry
    if (state.showSaveFolderSheet && selected != null) {
        SaveToFolderBottomSheet(
            word = selected.word,
            folders = state.folders,
            savedFolderIds = state.savedFoldersForSelected.map { it.id }.toSet(),
            onDismiss = viewModel::dismissSaveFolderSheet,
            onToggleFolder = viewModel::toggleEntryInFolder,
            onCreateAndSave = viewModel::createFolderAndSaveSelected,
            onRemoveAll = viewModel::removeSelectedFromAllFolders
        )
    }
}

@Composable
private fun OnlineSearchPromptCard(
    query: String,
    isSearching: Boolean,
    compact: Boolean = false,
    onSearchOnline: () -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        onClick = if (isSearching) null else onSearchOnline
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tìm trực tuyến cho từ \"$query\"",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lấy IPA, audio bản xứ, nghĩa Anh–Việt từ Free Dictionary / AI và tự động lưu offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onSearchOnline,
                enabled = !isSearching,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đang tra cứu trực tuyến...")
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tìm trực tuyến ngay")
                }
            }
        }
    }
}

@Composable
private fun OnlineErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            FilledTonalButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Thử lại")
            }
        }
    }
}

@Composable
private fun SourceBadgePill(isOnline: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isOnline) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.Public else Icons.Default.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isOnline) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = if (isOnline) "Online" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isOnline) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDetailCard(
    entry: DictionaryEntry,
    savedFolders: List<WordFolder> = emptyList(),
    isOnlineSource: Boolean = false,
    onlineExpansion: OnlineWordExpansion? = null,
    isExpandingOnline: Boolean = false,
    onlineError: String? = null,
    onSpeakUk: () -> Unit,
    onSpeakUs: () -> Unit,
    onPlayOnlineAudio: (url: String, accent: TtsAccent) -> Unit = { _, _ -> },
    onExpandOnline: () -> Unit = {},
    onOpenSaveSheet: () -> Unit,
    onShadowingSentence: (sentence: String, meaningVi: String, keyword: String) -> Unit = { _, _, _ -> }
) {
    val isSaved = savedFolders.isNotEmpty()
    val saveLabel = formatSavedFoldersButtonLabel(savedFolders)

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = entry.word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PosPill(entry.pos)
                    entry.cefrLevel?.let { CefrPill(it) }
                    SourceBadgePill(isOnline = isOnlineSource)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSaved) {
                        Button(
                            onClick = onOpenSaveSheet,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(saveLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onOpenSaveSheet,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(saveLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = onExpandOnline,
                        enabled = !isExpandingOnline,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isExpandingOnline) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (onlineExpansion != null) "Cập nhật Online" else "Mở rộng Online",
                            maxLines = 1
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IpaAudioPill(
                    accentLabel = "UK",
                    ipa = entry.ipaUk.ifBlank { onlineExpansion?.ipaUk.orEmpty() },
                    onClick = onSpeakUk
                )
                IpaAudioPill(
                    accentLabel = "US",
                    ipa = entry.ipaUs.ifBlank { onlineExpansion?.ipaUs.orEmpty() },
                    onClick = onSpeakUs
                )
            }

            Text(
                text = entry.meaningVi,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            onlineError?.let { err ->
                OnlineErrorBanner(message = err, onRetry = onExpandOnline)
            }

            if (entry.examples.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Text("Ví dụ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                entry.examples.forEach { ex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("• ${ex.en}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (ex.vi.isNotBlank()) {
                                Text(
                                    "  ${ex.vi}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        SuggestionChip(
                            onClick = { onShadowingSentence(ex.en, ex.vi, entry.word) },
                            label = { Text("Shadow") }
                        )
                    }
                }
            }

            if (entry.collocations.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Text("Collocations", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                entry.collocations.forEach { col ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• ${col.phrase} — ${col.meaningVi}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        SuggestionChip(
                            onClick = {
                                val targetSentence = col.example.ifBlank { col.phrase }
                                onShadowingSentence(targetSentence, col.meaningVi, col.phrase)
                            },
                            label = { Text("Shadow") }
                        )
                    }
                }
            }

            if (entry.synonyms.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.synonyms.forEach { syn ->
                        SuggestionChip(onClick = {}, label = { Text(syn) })
                    }
                }
            }

            if (onlineExpansion != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mở rộng Online (Anh–Anh)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = onlineExpansion.sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!onlineExpansion.audioUrlUk.isNullOrBlank() || !onlineExpansion.audioUrlUs.isNullOrBlank()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            onlineExpansion.audioUrlUk?.let { url ->
                                AssistChip(
                                    onClick = { onPlayOnlineAudio(url, TtsAccent.UK) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Audio bản xứ UK") }
                                )
                            }
                            onlineExpansion.audioUrlUs?.let { url ->
                                AssistChip(
                                    onClick = { onPlayOnlineAudio(url, TtsAccent.US) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Audio bản xứ US") }
                                )
                            }
                        }
                    }

                    onlineExpansion.meaningGroups.forEach { group ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PosPill(group.partOfSpeech)
                                group.definitions.forEachIndexed { idx, def ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "${idx + 1}. ${def.definitionEn}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (def.definitionVi.isNotBlank()) {
                                            Text(
                                                text = "→ ${def.definitionVi}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (def.exampleEn.isNotBlank()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "VD: \"${def.exampleEn}\"",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (def.exampleVi.isNotBlank()) {
                                                        Text(
                                                            text = def.exampleVi,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                SuggestionChip(
                                                    onClick = {
                                                        onShadowingSentence(
                                                            def.exampleEn,
                                                            def.exampleVi.ifBlank { def.definitionVi },
                                                            entry.word
                                                        )
                                                    },
                                                    label = { Text("Shadow") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (onlineExpansion.antonyms.isNotEmpty()) {
                        Text(
                            text = "Trái nghĩa (Antonyms)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            onlineExpansion.antonyms.forEach { ant ->
                                SuggestionChip(onClick = {}, label = { Text(ant) })
                            }
                        }
                    }
                }
            }
        }
    }
}
