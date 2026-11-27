package com.enziidict.app.feature.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.OnlineAudioPlayer
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.network.OnlineDictionaryRepository
import com.enziidict.app.core.network.OnlineLookupOutcome
import com.enziidict.app.core.network.OnlineWordExpansion
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.data.local.dao.WordFolderWithCount
import com.enziidict.app.data.local.entity.WordFolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DictionaryUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val entries: List<DictionaryEntry> = emptyList(),
    val selectedEntry: DictionaryEntry? = null,
    val folders: List<WordFolderWithCount> = emptyList(),
    val savedFoldersForSelected: List<WordFolder> = emptyList(),
    val showSaveFolderSheet: Boolean = false,
    val isOnlineSearching: Boolean = false,
    val expandingWordKey: String? = null,
    val onlineExpansions: Map<String, OnlineWordExpansion> = emptyMap(),
    val onlineCachedEntryIds: Set<Long> = emptySet(),
    val onlineErrorMessage: String? = null,
    val statusMessage: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    private val onlineDictionaryRepository: OnlineDictionaryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager,
    private val onlineAudioPlayer: OnlineAudioPlayer
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val baseState = MutableStateFlow(DictionaryUiState())
    private val showSaveSheetFlow = MutableStateFlow(false)

    private val savedFoldersFlow = baseState
        .map { it.selectedEntry?.id }
        .distinctUntilChanged()
        .flatMapLatest { entryId ->
            if (entryId == null) flowOf(emptyList()) else repository.observeSavedFoldersForEntry(entryId)
        }

    val uiState: StateFlow<DictionaryUiState> = combine(
        baseState,
        repository.observeFoldersWithCounts(),
        savedFoldersFlow,
        showSaveSheetFlow
    ) { state, folders, savedFolders, showSheet ->
        state.copy(
            folders = folders,
            savedFoldersForSelected = savedFolders,
            showSaveFolderSheet = showSheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DictionaryUiState())

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(80L)
                .collectLatest { q ->
                    baseState.update { it.copy(isLoading = true) }
                    val found = repository.search(q)
                    baseState.update { state ->
                        state.copy(
                            isLoading = false,
                            entries = found,
                            selectedEntry = state.selectedEntry?.takeIf { sel -> found.any { it.id == sel.id } }
                                ?: found.firstOrNull()
                        )
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        baseState.update {
            it.copy(
                query = newQuery,
                statusMessage = null,
                onlineErrorMessage = null
            )
        }
        queryFlow.value = newQuery
    }

    fun selectEntry(entry: DictionaryEntry) {
        baseState.update {
            it.copy(
                selectedEntry = entry,
                statusMessage = null,
                onlineErrorMessage = null
            )
        }
        viewModelScope.launch {
            repository.recordSearch(entry.word, entry.id)
        }
    }

    fun searchOnlineForCurrentQuery() {
        val raw = baseState.value.query.trim()
        if (raw.isEmpty() || baseState.value.isOnlineSearching) return

        viewModelScope.launch {
            baseState.update {
                it.copy(
                    isOnlineSearching = true,
                    onlineErrorMessage = null,
                    statusMessage = null
                )
            }

            when (val outcome = onlineDictionaryRepository.lookupOnline(raw)) {
                is OnlineLookupOutcome.Success -> {
                    val refreshed = repository.search(raw)
                    val mergedList = if (refreshed.any { it.id == outcome.entry.id }) {
                        refreshed
                    } else {
                        listOf(outcome.entry) + refreshed
                    }
                    val key = outcome.entry.word.lowercase()
                    baseState.update { state ->
                        state.copy(
                            isOnlineSearching = false,
                            entries = mergedList,
                            selectedEntry = outcome.entry,
                            onlineExpansions = state.onlineExpansions + (key to outcome.expansion),
                            onlineCachedEntryIds = state.onlineCachedEntryIds + outcome.entry.id,
                            onlineErrorMessage = null,
                            statusMessage = if (outcome.newlyCached) {
                                "Đã tra Online & lưu \"${outcome.entry.word}\" để dùng offline"
                            } else {
                                "Đã tải mở rộng Online cho \"${outcome.entry.word}\""
                            }
                        )
                    }
                }
                is OnlineLookupOutcome.NotFound -> {
                    baseState.update {
                        it.copy(isOnlineSearching = false, onlineErrorMessage = outcome.messageVi)
                    }
                }
                is OnlineLookupOutcome.OfflineError -> {
                    baseState.update {
                        it.copy(isOnlineSearching = false, onlineErrorMessage = outcome.messageVi)
                    }
                }
                is OnlineLookupOutcome.ApiError -> {
                    baseState.update {
                        it.copy(isOnlineSearching = false, onlineErrorMessage = outcome.messageVi)
                    }
                }
            }
        }
    }

    fun expandSelectedWordOnline() {
        val selected = baseState.value.selectedEntry ?: return
        val wordKey = selected.word.lowercase()
        if (baseState.value.expandingWordKey == wordKey) return

        viewModelScope.launch {
            baseState.update {
                it.copy(
                    expandingWordKey = wordKey,
                    onlineErrorMessage = null
                )
            }

            when (val outcome = onlineDictionaryRepository.lookupOnline(selected.word)) {
                is OnlineLookupOutcome.Success -> {
                    val outcomeKey = outcome.entry.word.lowercase()
                    baseState.update { state ->
                        val updatedEntries = state.entries.map { existing ->
                            if (existing.id == outcome.entry.id) outcome.entry else existing
                        }
                        state.copy(
                            expandingWordKey = null,
                            entries = updatedEntries,
                            selectedEntry = outcome.entry,
                            onlineExpansions = state.onlineExpansions +
                                (wordKey to outcome.expansion) +
                                (outcomeKey to outcome.expansion),
                            onlineCachedEntryIds = state.onlineCachedEntryIds + outcome.entry.id,
                            onlineErrorMessage = null,
                            statusMessage = "Đã mở rộng Online cho \"${outcome.entry.word}\""
                        )
                    }
                }
                is OnlineLookupOutcome.NotFound -> {
                    baseState.update {
                        it.copy(expandingWordKey = null, onlineErrorMessage = outcome.messageVi)
                    }
                }
                is OnlineLookupOutcome.OfflineError -> {
                    baseState.update {
                        it.copy(expandingWordKey = null, onlineErrorMessage = outcome.messageVi)
                    }
                }
                is OnlineLookupOutcome.ApiError -> {
                    baseState.update {
                        it.copy(expandingWordKey = null, onlineErrorMessage = outcome.messageVi)
                    }
                }
            }
        }
    }

    fun speak(text: String, accent: TtsAccent) {
        onlineAudioPlayer.stop()
        ttsManager.speak(text, accent)
    }

    fun speakWithPreferredAudio(word: String, accent: TtsAccent, audioUrl: String?) {
        if (!audioUrl.isNullOrBlank()) {
            ttsManager.stop()
            onlineAudioPlayer.playUrl(audioUrl) {
                ttsManager.speak(word, accent)
            }
        } else {
            speak(word, accent)
        }
    }

    fun stopAudio() {
        onlineAudioPlayer.stop()
        ttsManager.stop()
    }

    fun openSaveFolderSheet() {
        if (baseState.value.selectedEntry != null) {
            showSaveSheetFlow.value = true
        }
    }

    fun dismissSaveFolderSheet() {
        showSaveSheetFlow.value = false
    }

    fun toggleEntryInFolder(folder: WordFolder) {
        val entry = baseState.value.selectedEntry ?: return
        val currentlySaved = uiState.value.savedFoldersForSelected.any { it.id == folder.id }
        viewModelScope.launch {
            if (currentlySaved) {
                repository.removeFromFolder(entry.id, folder.id)
                baseState.update {
                    it.copy(statusMessage = "Đã bỏ \"${entry.word}\" khỏi thư mục \"${folder.name}\"")
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

    fun createFolderAndSaveSelected(folderName: String, colorHex: String) {
        val entry = baseState.value.selectedEntry ?: return
        viewModelScope.launch {
            val created = repository.createFolderAndSave(
                entryId = entry.id,
                folderName = folderName,
                colorHex = colorHex
            )
            if (created != null) {
                settingsDataStore.recordStudyActivity()
                showSaveSheetFlow.value = false
                baseState.update {
                    it.copy(statusMessage = "Đã tạo thư mục \"${created.name}\" và lưu \"${entry.word}\"")
                }
            }
        }
    }

    fun removeSelectedFromAllFolders() {
        val entry = baseState.value.selectedEntry ?: return
        viewModelScope.launch {
            repository.removeEntryFromAllFolders(entry.id)
            showSaveSheetFlow.value = false
            baseState.update {
                it.copy(statusMessage = "Đã bỏ lưu \"${entry.word}\" khỏi sổ tay")
            }
        }
    }

    override fun onCleared() {
        onlineAudioPlayer.release()
        super.onCleared()
    }
}
