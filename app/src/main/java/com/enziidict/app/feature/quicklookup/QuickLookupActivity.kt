package com.enziidict.app.feature.quicklookup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.data.local.dao.WordFolderWithCount
import com.enziidict.app.data.local.entity.WordFolder
import com.enziidict.app.ui.components.SaveToFolderBottomSheet
import com.enziidict.app.ui.components.formatSavedFoldersButtonLabel
import com.enziidict.app.ui.theme.EnziiDictTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickLookupUiState(
    val query: String = "",
    val entries: List<DictionaryEntry> = emptyList(),
    val folders: List<WordFolderWithCount> = emptyList(),
    val savedFolders: List<WordFolder> = emptyList(),
    val showSaveFolderSheet: Boolean = false,
    val statusMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QuickLookupViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val baseState = MutableStateFlow(QuickLookupUiState())
    private val showSheetFlow = MutableStateFlow(false)

    private val savedFoldersFlow = baseState
        .map { it.entries.firstOrNull()?.id }
        .distinctUntilChanged()
        .flatMapLatest { entryId ->
            if (entryId == null) flowOf(emptyList()) else repository.observeSavedFoldersForEntry(entryId)
        }

    val uiState: StateFlow<QuickLookupUiState> = combine(
        baseState,
        repository.observeFoldersWithCounts(),
        savedFoldersFlow,
        showSheetFlow
    ) { state, folders, savedFolders, showSheet ->
        state.copy(
            folders = folders,
            savedFolders = savedFolders,
            showSaveFolderSheet = showSheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickLookupUiState())

    fun lookup(selectedText: String) {
        baseState.update { it.copy(query = selectedText, statusMessage = null) }
        viewModelScope.launch {
            val found = repository.quickLookup(selectedText)
            baseState.update { it.copy(entries = found) }
        }
    }

    fun speak(word: String, accent: TtsAccent) {
        ttsManager.speak(word, accent)
    }

    fun openSaveFolderSheet() {
        if (baseState.value.entries.isNotEmpty()) {
            showSheetFlow.value = true
        }
    }

    fun dismissSaveFolderSheet() {
        showSheetFlow.value = false
    }

    fun toggleFolder(folder: WordFolder) {
        val entry = baseState.value.entries.firstOrNull() ?: return
        val currentlySaved = uiState.value.savedFolders.any { it.id == folder.id }
        viewModelScope.launch {
            if (currentlySaved) {
                repository.removeFromFolder(entry.id, folder.id)
                baseState.update {
                    it.copy(statusMessage = "Đã bỏ \"${entry.word}\" khỏi \"${folder.name}\"")
                }
            } else {
                repository.saveToFolderById(entry.id, folder.id)
                settingsDataStore.recordStudyActivity()
                baseState.update {
                    it.copy(statusMessage = "Đã lưu \"${entry.word}\" vào \"${folder.name}\"")
                }
            }
        }
    }

    fun createFolderAndSave(folderName: String, colorHex: String) {
        val entry = baseState.value.entries.firstOrNull() ?: return
        viewModelScope.launch {
            val created = repository.createFolderAndSave(
                entryId = entry.id,
                folderName = folderName,
                colorHex = colorHex
            )
            if (created != null) {
                settingsDataStore.recordStudyActivity()
                showSheetFlow.value = false
                baseState.update {
                    it.copy(statusMessage = "Đã tạo \"${created.name}\" và lưu \"${entry.word}\"")
                }
            }
        }
    }

    fun removeFromAllFolders() {
        val entry = baseState.value.entries.firstOrNull() ?: return
        viewModelScope.launch {
            repository.removeEntryFromAllFolders(entry.id)
            showSheetFlow.value = false
            baseState.update {
                it.copy(statusMessage = "Đã bỏ lưu \"${entry.word}\" khỏi sổ tay")
            }
        }
    }
}

@AndroidEntryPoint
class QuickLookupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rawText = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()

        setContent {
            EnziiDictTheme {
                val viewModel: QuickLookupViewModel = hiltViewModel()
                LaunchedEffect(rawText) {
                    viewModel.lookup(rawText)
                }
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        tonalElevation = 6.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tra nhanh: ${state.query}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Đóng")
                                }
                            }

                            val first = state.entries.firstOrNull()
                            if (first == null) {
                                Text(
                                    text = "Chưa tìm thấy mục từ offline khớp với \"${state.query}\".",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = first.word,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = first.pos,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AssistChip(
                                        onClick = { viewModel.speak(first.word, TtsAccent.UK) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                                        label = { Text("UK ${first.ipaUk}") }
                                    )
                                    AssistChip(
                                        onClick = { viewModel.speak(first.word, TtsAccent.US) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                                        label = { Text("US ${first.ipaUs}") }
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(first.meaningVi, style = MaterialTheme.typography.titleMedium)
                                first.examples.firstOrNull()?.let { ex ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("• ${ex.en}", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                val isSaved = state.savedFolders.isNotEmpty()
                                val saveLabel = formatSavedFoldersButtonLabel(state.savedFolders)
                                if (isSaved) {
                                    Button(
                                        onClick = viewModel::openSaveFolderSheet,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(saveLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = viewModel::openSaveFolderSheet,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(saveLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                state.statusMessage?.let { msg ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                val first = state.entries.firstOrNull()
                if (state.showSaveFolderSheet && first != null) {
                    SaveToFolderBottomSheet(
                        word = first.word,
                        folders = state.folders,
                        savedFolderIds = state.savedFolders.map { it.id }.toSet(),
                        onDismiss = viewModel::dismissSaveFolderSheet,
                        onToggleFolder = viewModel::toggleFolder,
                        onCreateAndSave = viewModel::createFolderAndSave,
                        onRemoveAll = viewModel::removeFromAllFolders
                    )
                }
            }
        }
    }
}
