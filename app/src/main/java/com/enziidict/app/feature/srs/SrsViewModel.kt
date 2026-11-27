package com.enziidict.app.feature.srs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.model.CollocationItem
import com.enziidict.app.core.model.ExampleItem
import com.enziidict.app.core.model.ReviewGrade
import com.enziidict.app.core.model.WordFolders
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.data.local.dao.SavedWordDao
import com.enziidict.app.data.local.dao.SavedWordWithEntry
import com.enziidict.app.data.local.dao.WordFolderDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class WordFolderUi(
    val id: Long,
    val name: String,
    val colorHex: String,
    val totalCount: Int,
    val dueCount: Int,
    val isDefault: Boolean,
    val isPreset: Boolean
)

data class FlashcardCardUi(
    val item: SavedWordWithEntry,
    val examples: List<ExampleItem>,
    val collocations: List<CollocationItem>,
    val synonyms: List<String>,
    val intervalPreviews: Map<ReviewGrade, String>
)

data class SrsUiState(
    val folders: List<WordFolderUi> = emptyList(),
    val totalCards: Int = 0,
    val dueCount: Int = 0,
    val selectedFolderId: Long? = null,
    val filteredWords: List<SavedWordWithEntry> = emptyList(),
    val activeReviewTitle: String? = null,
    val currentCard: FlashcardCardUi? = null,
    val isFlipped: Boolean = false,
    val sessionTotal: Int = 0,
    val reviewedCount: Int = 0,
    val sessionCompleted: Boolean = false,
    val statusMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SrsViewModel @Inject constructor(
    private val wordFolderDao: WordFolderDao,
    private val savedWordDao: SavedWordDao,
    private val dictionaryRepository: DictionaryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager,
    private val json: Json
) : ViewModel() {

    private val refreshTick = MutableStateFlow(System.currentTimeMillis())
    private val selectedFolderIdFlow = MutableStateFlow<Long?>(null)
    private val reviewQueueFlow = MutableStateFlow<List<SavedWordWithEntry>>(emptyList())
    private val activeReviewTitleFlow = MutableStateFlow<String?>(null)
    private val isFlippedFlow = MutableStateFlow(false)
    private val sessionTotalFlow = MutableStateFlow(0)
    private val reviewedCountFlow = MutableStateFlow(0)
    private val sessionCompletedFlow = MutableStateFlow(false)
    private val statusMessageFlow = MutableStateFlow<String?>(null)

    private val foldersFlow = refreshTick.flatMapLatest { now ->
        wordFolderDao.observeFoldersWithCounts(now + 60_000L)
    }.map { list ->
        list.map { item ->
            WordFolderUi(
                id = item.folder.id,
                name = item.folder.name,
                colorHex = item.folder.colorHex,
                totalCount = item.wordCount,
                dueCount = item.dueCount,
                isDefault = item.folder.name == WordFolders.DEFAULT,
                isPreset = item.folder.name in WordFolders.PRESETS
            )
        }
    }

    private val totalDueFlow = refreshTick.flatMapLatest { now ->
        savedWordDao.observeDueCount(now + 60_000L)
    }

    private val filteredWordsFlow = selectedFolderIdFlow.flatMapLatest { folderId ->
        if (folderId == null) {
            savedWordDao.observeAllSavedWords()
        } else {
            savedWordDao.observeWordsByFolderId(folderId)
        }
    }

    val uiState: StateFlow<SrsUiState> = combine(
        foldersFlow,
        savedWordDao.observeTotalCount(),
        totalDueFlow,
        selectedFolderIdFlow,
        filteredWordsFlow,
        reviewQueueFlow,
        activeReviewTitleFlow,
        isFlippedFlow,
        sessionTotalFlow,
        reviewedCountFlow,
        sessionCompletedFlow,
        statusMessageFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val folders = values[0] as List<WordFolderUi>
        val total = values[1] as Int
        val due = values[2] as Int
        val selectedFolderId = values[3] as Long?
        @Suppress("UNCHECKED_CAST")
        val filteredWords = values[4] as List<SavedWordWithEntry>
        @Suppress("UNCHECKED_CAST")
        val queue = values[5] as List<SavedWordWithEntry>
        val reviewTitle = values[6] as String?
        val isFlipped = values[7] as Boolean
        val sessionTotal = values[8] as Int
        val reviewedCount = values[9] as Int
        val sessionCompleted = values[10] as Boolean
        val statusMsg = values[11] as String?

        val head = queue.firstOrNull()
        val cardUi = head?.let { toFlashcardCardUi(it) }

        SrsUiState(
            folders = folders,
            totalCards = total,
            dueCount = due,
            selectedFolderId = selectedFolderId,
            filteredWords = filteredWords,
            activeReviewTitle = reviewTitle,
            currentCard = cardUi,
            isFlipped = isFlipped,
            sessionTotal = sessionTotal,
            reviewedCount = reviewedCount,
            sessionCompleted = sessionCompleted,
            statusMessage = statusMsg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SrsUiState())

    fun selectFolderFilter(folderId: Long?) {
        selectedFolderIdFlow.value = folderId
        statusMessageFlow.value = null
    }

    fun startReviewSession(folderId: Long? = selectedFolderIdFlow.value) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            refreshTick.value = now
            val folder = folderId?.let { wordFolderDao.findById(it) }
            val due = if (folder == null) {
                savedWordDao.getDueWords(now + 60_000L, limit = 30)
            } else {
                savedWordDao.getDueWordsByFolderId(folder.id, now + 60_000L, limit = 30)
            }

            if (due.isEmpty()) {
                statusMessageFlow.value = if (folder == null) {
                    "Không còn thẻ nào đến hạn ôn tập lúc này."
                } else {
                    "Thư mục \"${folder.name}\" chưa có thẻ đến hạn."
                }
                return@launch
            }

            activeReviewTitleFlow.value = folder?.name ?: "Tất cả đến hạn"
            reviewQueueFlow.value = due
            sessionTotalFlow.value = due.size
            reviewedCountFlow.value = 0
            sessionCompletedFlow.value = false
            isFlippedFlow.value = false
            statusMessageFlow.value = null
        }
    }

    fun exitReviewSession() {
        reviewQueueFlow.value = emptyList()
        activeReviewTitleFlow.value = null
        isFlippedFlow.value = false
        sessionCompletedFlow.value = false
        refreshTick.value = System.currentTimeMillis()
    }

    fun toggleFlip() {
        isFlippedFlow.value = !isFlippedFlow.value
    }

    fun speakWord(word: String, accent: TtsAccent = TtsAccent.US) {
        ttsManager.speak(word, accent)
    }

    fun gradeCurrentCard(grade: ReviewGrade) {
        val current = reviewQueueFlow.value.firstOrNull() ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val result = Sm2Scheduler.schedule(
                currentEf = current.savedWord.easeFactor,
                currentIntervalDays = current.savedWord.intervalDays,
                currentRepetitions = current.savedWord.repetitions,
                currentLapses = current.savedWord.lapses,
                grade = grade,
                nowMillis = now
            )

            savedWordDao.updateSavedWord(
                current.savedWord.copy(
                    easeFactor = result.easinessFactor,
                    intervalDays = result.intervalDays,
                    repetitions = result.repetitions,
                    lapses = result.lapses,
                    state = result.state.name,
                    nextReviewAt = result.dueAtMillis,
                    lastReviewedAt = now
                )
            )
            settingsDataStore.recordStudyActivity()

            val remaining = reviewQueueFlow.value.drop(1)
            reviewedCountFlow.value += 1
            reviewQueueFlow.value = remaining
            isFlippedFlow.value = false
            refreshTick.value = System.currentTimeMillis()

            if (remaining.isEmpty()) {
                sessionCompletedFlow.value = true
            }
        }
    }

    fun createCustomFolder(rawName: String, colorHex: String = "#0F766E") {
        val cleaned = rawName.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            val created = dictionaryRepository.createFolder(cleaned, colorHex = colorHex)
            if (created != null) {
                selectedFolderIdFlow.value = created.id
                refreshTick.value = System.currentTimeMillis()
                statusMessageFlow.value = "Đã tạo thư mục \"${created.name}\""
            }
        }
    }

    fun renameCustomFolder(folderId: Long, rawNewName: String) {
        val cleaned = rawNewName.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            val ok = dictionaryRepository.renameFolder(folderId, cleaned)
            refreshTick.value = System.currentTimeMillis()
            statusMessageFlow.value = if (ok) {
                "Đã đổi tên thư mục thành \"$cleaned\""
            } else {
                "Không thể đổi tên thư mục (tên trùng hoặc thư mục mặc định)."
            }
        }
    }

    fun deleteCustomFolder(folderId: Long, folderName: String) {
        viewModelScope.launch {
            val deleted = dictionaryRepository.deleteFolder(folderId)
            if (deleted) {
                if (selectedFolderIdFlow.value == folderId) {
                    selectedFolderIdFlow.value = null
                }
                refreshTick.value = System.currentTimeMillis()
                statusMessageFlow.value = "Đã xóa thư mục \"$folderName\""
            }
        }
    }

    fun deleteSavedWord(savedWordId: Long) {
        viewModelScope.launch {
            savedWordDao.deleteSavedWord(savedWordId)
            refreshTick.value = System.currentTimeMillis()
        }
    }

    private fun toFlashcardCardUi(item: SavedWordWithEntry): FlashcardCardUi {
        val previews = Sm2Scheduler.previewAllGrades(
            currentEf = item.savedWord.easeFactor,
            currentIntervalDays = item.savedWord.intervalDays,
            currentRepetitions = item.savedWord.repetitions,
            currentLapses = item.savedWord.lapses
        ).mapValues { (grade, result) -> Sm2Scheduler.formatIntervalLabel(grade, result) }

        return FlashcardCardUi(
            item = item,
            examples = decodeOrEmpty(item.entry.examplesJson),
            collocations = decodeOrEmpty(item.entry.collocationsJson),
            synonyms = decodeOrEmpty(item.entry.synonymsJson),
            intervalPreviews = previews
        )
    }

    private inline fun <reified T> decodeOrEmpty(raw: String): List<T> =
        runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
}
