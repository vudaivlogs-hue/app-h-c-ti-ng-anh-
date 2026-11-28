package com.enziidict.app.feature.srs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.model.ReviewGrade
import com.enziidict.app.data.local.dao.SavedWordWithEntry
import com.enziidict.app.feature.progress.FlashcardRecapCard
import com.enziidict.app.ui.components.CefrPill
import com.enziidict.app.ui.components.FOLDER_COLOR_PALETTE
import com.enziidict.app.ui.components.IpaAudioPill
import com.enziidict.app.ui.components.PosPill
import com.enziidict.app.ui.components.parseFolderColor
import com.enziidict.app.ui.components.rememberEnziiHaptics
import java.util.Locale

@Composable
fun FlashcardScreen(
    onOpenLearningSummary: () -> Unit = {},
    viewModel: SrsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberEnziiHaptics()
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<WordFolderUi?>(null) }

    if (state.activeReviewTitle != null) {
        FlashcardReviewMode(
            title = state.activeReviewTitle.orEmpty(),
            card = state.currentCard,
            isFlipped = state.isFlipped,
            reviewedCount = state.reviewedCount,
            sessionTotal = state.sessionTotal,
            sessionCompleted = state.sessionCompleted,
            sessionRecap = state.sessionRecap,
            onOpenLearningSummary = {
                viewModel.exitReviewSession()
                onOpenLearningSummary()
            },
            onBack = viewModel::exitReviewSession,
            onToggleFlip = {
                haptics.lightTap()
                viewModel.toggleFlip()
            },
            onSpeak = { w, a ->
                haptics.lightTap()
                viewModel.speakWord(w, a)
            },
            onGrade = { grade ->
                haptics.confirm()
                viewModel.gradeCurrentCard(grade)
            }
        )
    } else {
        NotebookHomeScreen(
            state = state,
            onSelectFolder = { id ->
                haptics.lightTap()
                viewModel.selectFolderFilter(id)
            },
            onStartReview = { folderId ->
                haptics.confirm()
                viewModel.startReviewSession(folderId)
            },
            onOpenCreateFolder = { showCreateFolderDialog = true },
            onRequestRenameFolder = { renamingFolder = it },
            onDeleteCustomFolder = { folder ->
                viewModel.deleteCustomFolder(folder.id, folder.name)
            },
            onSpeakWord = { word ->
                haptics.lightTap()
                viewModel.speakWord(word, TtsAccent.US)
            },
            onDeleteWord = viewModel::deleteSavedWord
        )
    }

    if (showCreateFolderDialog) {
        var folderInput by remember { mutableStateOf("") }
        var selectedColorHex by remember { mutableStateOf(FOLDER_COLOR_PALETTE[1]) }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Tạo thư mục từ vựng") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("VD: Động vật, IELTS Writing, Giao tiếp...") }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Màu:", style = MaterialTheme.typography.labelMedium)
                        FOLDER_COLOR_PALETTE.forEach { hex ->
                            val swatch = parseFolderColor(hex, MaterialTheme.colorScheme.primary)
                            val isSelected = hex == selectedColorHex
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selectedColorHex = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createCustomFolder(folderInput, selectedColorHex)
                        showCreateFolderDialog = false
                    },
                    enabled = folderInput.isNotBlank()
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    renamingFolder?.let { folder ->
        var renameInput by remember(folder.id) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { renamingFolder = null },
            title = { Text("Đổi tên thư mục") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameCustomFolder(folder.id, renameInput)
                        renamingFolder = null
                    },
                    enabled = renameInput.isNotBlank() && renameInput.trim() != folder.name
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingFolder = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun NotebookHomeScreen(
    state: SrsUiState,
    onSelectFolder: (Long?) -> Unit,
    onStartReview: (Long?) -> Unit,
    onOpenCreateFolder: () -> Unit,
    onRequestRenameFolder: (WordFolderUi) -> Unit,
    onDeleteCustomFolder: (WordFolderUi) -> Unit,
    onSpeakWord: (String) -> Unit,
    onDeleteWord: (Long) -> Unit
) {
    val selectedFolder = state.folders.firstOrNull { it.id == state.selectedFolderId }
    val activeTitle = selectedFolder?.name ?: "Tất cả thư mục"
    val activeDueCount = selectedFolder?.dueCount ?: state.dueCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Summary Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sổ tay & Flashcard SM-2",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Đến hạn hôm nay: ${state.dueCount} • Tổng thẻ: ${state.totalCards}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = { onStartReview(null) },
                    enabled = state.dueCount > 0,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Ôn tất cả")
                }
            }
        }

        state.statusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        // Folder Cards Header + Horizontal Topic Filter Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Thư mục chủ đề (${state.folders.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onOpenCreateFolder,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Thư mục mới")
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item(key = "folder_all") {
                FolderFilterCard(
                    title = "Tất cả",
                    wordCount = state.totalCards,
                    dueCount = state.dueCount,
                    accentColor = MaterialTheme.colorScheme.primary,
                    selected = state.selectedFolderId == null,
                    onClick = { onSelectFolder(null) }
                )
            }
            items(state.folders, key = { it.id }) { folder ->
                FolderFilterCard(
                    title = folder.name,
                    wordCount = folder.totalCount,
                    dueCount = folder.dueCount,
                    accentColor = parseFolderColor(folder.colorHex, MaterialTheme.colorScheme.primary),
                    selected = state.selectedFolderId == folder.id,
                    onClick = { onSelectFolder(folder.id) }
                )
            }
        }

        // Active Folder Action Bar (Filter header + Rename/Delete + Review this folder)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${state.filteredWords.size} từ • $activeDueCount đến hạn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedFolder != null && !selectedFolder.isDefault) {
                    IconButton(onClick = { onRequestRenameFolder(selectedFolder) }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Đổi tên thư mục",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { onDeleteCustomFolder(selectedFolder) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Xóa thư mục",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { onStartReview(state.selectedFolderId) },
                    enabled = activeDueCount > 0,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ôn ($activeDueCount)")
                }
            }
        }

        // Filtered Vocabulary List
        if (state.filteredWords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa có từ nào trong \"$activeTitle\".\nHãy tra từ ở Từ điển hoặc Đọc song ngữ rồi bấm \"Lưu vào sổ tay\".",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.filteredWords, key = { it.savedWord.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
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
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.entry.word,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    PosPill(item.entry.pos)
                                }
                                Text(
                                    text = item.entry.meaningVi,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        if (state.selectedFolderId == null) {
                                            append("${item.savedWord.folderName} • ")
                                        }
                                        append("EF ${String.format(Locale.US, "%.2f", item.savedWord.easeFactor)}")
                                        append(" • Chu kỳ ${item.savedWord.intervalDays} ngày")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { onSpeakWord(item.entry.word) }) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Phát âm")
                                }
                                IconButton(onClick = { onDeleteWord(item.savedWord.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Xóa khỏi sổ tay")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderFilterCard(
    title: String,
    wordCount: Int,
    dueCount: Int,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(142.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            accentColor.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                if (dueCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor
                    ) {
                        Text(
                            text = "$dueCount ôn",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$wordCount từ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlashcardReviewMode(
    title: String,
    card: FlashcardCardUi?,
    isFlipped: Boolean,
    reviewedCount: Int,
    sessionTotal: Int,
    sessionCompleted: Boolean,
    sessionRecap: SrsSessionRecapUi?,
    onOpenLearningSummary: () -> Unit,
    onBack: () -> Unit,
    onToggleFlip: () -> Unit,
    onSpeak: (String, TtsAccent) -> Unit,
    onGrade: (ReviewGrade) -> Unit
) {
    val density = LocalDensity.current.density
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "flashcard_flip_3d"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${reviewedCount.coerceAtMost(sessionTotal)} / $sessionTotal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        val progress = if (sessionTotal > 0) reviewedCount.toFloat() / sessionTotal.toFloat() else 0f
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )

        if (sessionCompleted || card == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                FlashcardRecapCard(
                    recap = sessionRecap,
                    reviewedCountFallback = reviewedCount,
                    onOpenLearningSummary = onOpenLearningSummary,
                    onBack = onBack
                )
            }
            return
        }

        // 3D Flipping Flashcard
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 14f * density
                }
                .clickable { onToggleFlip() },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (rotation <= 90f) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                }
            )
        ) {
            if (rotation <= 90f) {
                // Front face
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                listOfNotNull(
                                    card.item.savedWord.folderName,
                                    card.item.entry.cefrLevel
                                ).joinToString(" • ")
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = card.item.entry.word,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PosPill(card.item.entry.pos)
                        card.item.entry.cefrLevel?.let { CefrPill(it) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IpaAudioPill(
                            accentLabel = "UK",
                            ipa = card.item.entry.ipaUk,
                            onClick = { onSpeak(card.item.entry.word, TtsAccent.UK) }
                        )
                        IpaAudioPill(
                            accentLabel = "US",
                            ipa = card.item.entry.ipaUs,
                            onClick = { onSpeak(card.item.entry.word, TtsAccent.US) }
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Chạm vào thẻ để lật xem nghĩa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Back face (counter-rotated 180° so text reads left-to-right)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.item.entry.word,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${card.item.entry.pos} • ${card.item.entry.ipaUs}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onSpeak(card.item.entry.word, TtsAccent.US) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Phát âm")
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = card.item.entry.meaningVi,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (card.examples.isNotEmpty()) {
                        Text(
                            text = "Ví dụ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        card.examples.forEach { ex ->
                            Text("• ${ex.en}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (ex.vi.isNotBlank()) {
                                Text(
                                    "  ${ex.vi}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (card.collocations.isNotEmpty()) {
                        Text(
                            text = "Collocations",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        card.collocations.forEach { col ->
                            Text("• ${col.phrase} — ${col.meaningVi}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (card.synonyms.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            card.synonyms.forEach { syn ->
                                SuggestionChip(onClick = {}, label = { Text(syn) })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SM-2: EF ${String.format(Locale.US, "%.2f", card.item.savedWord.easeFactor)} • Lặp ${card.item.savedWord.repetitions} • Chu kỳ ${card.item.savedWord.intervalDays} ngày",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bottom controls
        if (!isFlipped) {
            FilledTonalButton(
                onClick = onToggleFlip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Lật thẻ xem đáp án")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReviewGrade.entries.forEach { grade ->
                    val previewText = card.intervalPreviews[grade].orEmpty()
                    val containerColor = when (grade) {
                        ReviewGrade.AGAIN -> MaterialTheme.colorScheme.errorContainer
                        ReviewGrade.HARD -> Color(0xFFFFE7C2)
                        ReviewGrade.GOOD -> MaterialTheme.colorScheme.primaryContainer
                        ReviewGrade.EASY -> Color(0xFFD5F2DC)
                    }
                    val contentColor = when (grade) {
                        ReviewGrade.AGAIN -> MaterialTheme.colorScheme.onErrorContainer
                        ReviewGrade.HARD -> Color(0xFF5D3A00)
                        ReviewGrade.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
                        ReviewGrade.EASY -> Color(0xFF0A4A23)
                    }

                    Button(
                        onClick = { onGrade(grade) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = containerColor,
                            contentColor = contentColor
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = grade.labelEn,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = previewText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
