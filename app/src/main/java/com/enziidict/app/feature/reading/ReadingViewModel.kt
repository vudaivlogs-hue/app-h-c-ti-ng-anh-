package com.enziidict.app.feature.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.audio.TtsManager
import com.enziidict.app.core.database.DictionaryRepository
import com.enziidict.app.core.database.LearningProgressRepository
import com.enziidict.app.core.model.BilingualParagraph
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.model.WordFolders
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.data.local.dao.ReadingDao
import com.enziidict.app.data.local.dao.WordFolderDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class BilingualArticleUi(
    val id: Long,
    val titleEn: String,
    val titleVi: String,
    val cefrLevel: String,
    val category: String,
    val paragraphs: List<BilingualParagraph>,
    val readProgress: Float
)

data class WordLookupSheetState(
    val tappedWord: String,
    val isLoading: Boolean = false,
    val matches: List<DictionaryEntry> = emptyList(),
    val selectedFolder: String = WordFolders.DEFAULT,
    val saveStatusMessage: String? = null
)

data class ReadingUiState(
    val selectedLevel: String? = null,
    val articles: List<BilingualArticleUi> = emptyList(),
    val activeArticle: BilingualArticleUi? = null,
    val showVietnameseGlobal: Boolean = true,
    val revealedParagraphIndices: Set<Int> = emptySet(),
    val availableFolders: List<String> = WordFolders.PRESETS,
    val lookupSheet: WordLookupSheetState? = null
)

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val readingDao: ReadingDao,
    private val dictionaryRepository: DictionaryRepository,
    wordFolderDao: WordFolderDao,
    private val progressRepository: LearningProgressRepository,
    private val json: Json,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val completedArticlesThisSession = mutableSetOf<Long>()

    private val selectedLevelFlow = MutableStateFlow<String?>(null)
    private val activeArticleIdFlow = MutableStateFlow<Long?>(null)
    private val showVietnameseGlobalFlow = MutableStateFlow(true)
    private val revealedParagraphsFlow = MutableStateFlow<Set<Int>>(emptySet())
    private val lookupSheetFlow = MutableStateFlow<WordLookupSheetState?>(null)

    private val articlesFlow = readingDao.observeAllArticles()
    private val foldersFlow = wordFolderDao.observeAllFolders().map { dbFolders ->
        val ordered = LinkedHashSet<String>()
        ordered.addAll(dbFolders.map { it.name })
        if (ordered.isEmpty()) ordered.addAll(WordFolders.PRESETS)
        ordered.toList()
    }

    val uiState: StateFlow<ReadingUiState> = combine(
        articlesFlow,
        selectedLevelFlow,
        activeArticleIdFlow,
        showVietnameseGlobalFlow,
        revealedParagraphsFlow,
        foldersFlow,
        lookupSheetFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val rawArticles = values[0] as List<com.enziidict.app.data.local.entity.BilingualArticle>
        val selectedLevel = values[1] as String?
        val activeArticleId = values[2] as Long?
        val showGlobal = values[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val revealed = values[4] as Set<Int>
        @Suppress("UNCHECKED_CAST")
        val folders = values[5] as List<String>
        val sheet = values[6] as WordLookupSheetState?

        val allMapped = rawArticles.map { row ->
            val paragraphs = runCatching {
                json.decodeFromString<List<BilingualParagraph>>(row.paragraphsJson)
            }.getOrDefault(emptyList())
            BilingualArticleUi(
                id = row.id,
                titleEn = row.titleEn,
                titleVi = row.titleVi,
                cefrLevel = row.cefrLevel,
                category = row.category,
                paragraphs = paragraphs,
                readProgress = row.readProgress
            )
        }

        val filtered = if (selectedLevel == null) {
            allMapped
        } else {
            allMapped.filter { it.cefrLevel.equals(selectedLevel, ignoreCase = true) }
        }

        ReadingUiState(
            selectedLevel = selectedLevel,
            articles = filtered,
            activeArticle = activeArticleId?.let { id -> allMapped.firstOrNull { it.id == id } },
            showVietnameseGlobal = showGlobal,
            revealedParagraphIndices = revealed,
            availableFolders = folders,
            lookupSheet = sheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingUiState())

    fun selectLevelFilter(level: String?) {
        selectedLevelFlow.value = level
    }

    fun openArticle(articleId: Long) {
        activeArticleIdFlow.value = articleId
        revealedParagraphsFlow.value = emptySet()
    }

    fun closeArticle() {
        activeArticleIdFlow.value = null
        lookupSheetFlow.value = null
    }

    fun toggleGlobalVietnamese() {
        showVietnameseGlobalFlow.value = !showVietnameseGlobalFlow.value
    }

    fun toggleParagraphVietnamese(index: Int) {
        val current = revealedParagraphsFlow.value
        revealedParagraphsFlow.value = if (index in current) current - index else current + index
    }

    fun markArticleProgress(articleId: Long, progress: Float) {
        viewModelScope.launch {
            readingDao.updateProgress(
                id = articleId,
                progress = progress.coerceIn(0f, 1f),
                updatedAt = System.currentTimeMillis()
            )
            if (progress >= 1f && completedArticlesThisSession.add(articleId)) {
                progressRepository.recordReadingCompleted()
            }
        }
    }

    fun onWordTapped(rawWord: String) {
        val cleaned = rawWord.trim()
        if (cleaned.isEmpty()) return

        val previousFolder = lookupSheetFlow.value?.selectedFolder ?: WordFolders.DEFAULT
        lookupSheetFlow.value = WordLookupSheetState(
            tappedWord = cleaned,
            isLoading = true,
            selectedFolder = previousFolder
        )

        viewModelScope.launch {
            val matches = dictionaryRepository.quickLookup(cleaned)
            lookupSheetFlow.value = WordLookupSheetState(
                tappedWord = cleaned,
                isLoading = false,
                matches = matches,
                selectedFolder = previousFolder
            )
        }
    }

    fun selectSaveFolder(folderName: String) {
        val current = lookupSheetFlow.value ?: return
        lookupSheetFlow.value = current.copy(selectedFolder = folderName, saveStatusMessage = null)
    }

    fun saveEntryToSelectedFolder(entry: DictionaryEntry) {
        val current = lookupSheetFlow.value ?: return
        val folder = current.selectedFolder
        viewModelScope.launch {
            val inserted = dictionaryRepository.saveToFolder(entry.id, folder)
            if (inserted) {
                progressRepository.recordWordSaved()
            }
            lookupSheetFlow.value = current.copy(
                saveStatusMessage = if (inserted) {
                    "Đã lưu \"${entry.word}\" vào thư mục \"$folder\""
                } else {
                    "\"${entry.word}\" đã có sẵn trong thư mục \"$folder\""
                }
            )
        }
    }

    fun dismissLookupSheet() {
        lookupSheetFlow.value = null
    }

    fun speakText(text: String, accent: TtsAccent = TtsAccent.US, rate: Float = 0.95f) {
        ttsManager.speak(text, accent, speechRate = rate)
    }
}
