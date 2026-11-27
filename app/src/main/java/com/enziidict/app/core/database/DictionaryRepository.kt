package com.enziidict.app.core.database

import com.enziidict.app.core.model.CollocationItem
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.core.model.ExampleItem
import com.enziidict.app.core.model.SrsState
import com.enziidict.app.core.model.WordFolders
import com.enziidict.app.data.local.dao.DictionaryDao
import com.enziidict.app.data.local.dao.SavedWordDao
import com.enziidict.app.data.local.dao.WordFolderDao
import com.enziidict.app.data.local.dao.WordFolderWithCount
import com.enziidict.app.data.local.entity.DictionaryEntry as DictionaryEntryEntity
import com.enziidict.app.data.local.entity.SavedWord
import com.enziidict.app.data.local.entity.WordFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepository @Inject constructor(
    private val dictionaryDao: DictionaryDao,
    private val wordFolderDao: WordFolderDao,
    private val savedWordDao: SavedWordDao,
    private val json: Json
) {
    fun observeFoldersWithCounts(nowMillis: Long = System.currentTimeMillis() + 60_000L): Flow<List<WordFolderWithCount>> {
        return wordFolderDao.observeFoldersWithCounts(nowMillis)
    }

    fun observeSavedFoldersForEntry(entryId: Long): Flow<List<WordFolder>> {
        return savedWordDao.observeSavedFoldersForEntry(entryId)
    }

    suspend fun ensureFolder(
        folderName: String,
        iconName: String = "label",
        colorHex: String = "#0F766E"
    ): WordFolder {
        val cleaned = folderName.trim().ifEmpty { WordFolders.DEFAULT }
        wordFolderDao.findByName(cleaned)?.let { return it }

        val resolvedIcon = when (cleaned) {
            WordFolders.DEFAULT -> "folder"
            WordFolders.OXFORD_3000 -> "school"
            WordFolders.CONVERSATION -> "chat"
            else -> iconName
        }
        val resolvedColor = when (cleaned) {
            WordFolders.DEFAULT -> "#1D4ED8"
            WordFolders.OXFORD_3000 -> "#0F766E"
            WordFolders.CONVERSATION -> "#7C3AED"
            else -> colorHex
        }
        val now = System.currentTimeMillis()
        val insertedId = wordFolderDao.insertFolder(
            WordFolder(
                name = cleaned,
                iconName = resolvedIcon,
                colorHex = resolvedColor,
                createdAt = now
            )
        )
        return if (insertedId > 0L) {
            WordFolder(
                id = insertedId,
                name = cleaned,
                iconName = resolvedIcon,
                colorHex = resolvedColor,
                createdAt = now
            )
        } else {
            wordFolderDao.findByName(cleaned)
                ?: wordFolderDao.getAllFolders().first()
        }
    }

    suspend fun createFolder(
        name: String,
        iconName: String = "label",
        colorHex: String = "#0F766E"
    ): WordFolder? {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return null
        return ensureFolder(cleaned, iconName, colorHex)
    }

    suspend fun renameFolder(folderId: Long, newName: String): Boolean {
        val cleaned = newName.trim()
        if (cleaned.isEmpty()) return false
        val current = wordFolderDao.findById(folderId) ?: return false
        if (current.name == WordFolders.DEFAULT) return false
        val conflict = wordFolderDao.findByName(cleaned)
        if (conflict != null && conflict.id != folderId) return false
        wordFolderDao.renameFolder(folderId, cleaned)
        savedWordDao.updateFolderNameForFolderId(folderId, cleaned)
        return true
    }

    suspend fun deleteFolder(folderId: Long): Boolean {
        val current = wordFolderDao.findById(folderId) ?: return false
        if (current.name == WordFolders.DEFAULT) return false
        wordFolderDao.deleteFolder(folderId)
        return true
    }

    suspend fun search(rawQuery: String, limit: Int = 30): List<DictionaryEntry> {
        val cleaned = rawQuery.trim().lowercase()
        if (cleaned.isEmpty()) {
            return dictionaryDao.getTopPopularWords(limit).map { it.toDomain() }
        }

        val results = LinkedHashMap<Long, DictionaryEntryEntity>()

        // Tier 1: Fast B-Tree prefix scan on word_normalized
        val prefixUpper = nextPrefixUpperBound(cleaned)
        if (prefixUpper != null) {
            dictionaryDao.searchPrefix(cleaned, prefixUpper, limit).forEach {
                results[it.id] = it
            }
        }

        // Tier 2: Full-text search (supports Vietnamese -> English & collocations)
        if (results.size < limit) {
            val ftsToken = cleaned
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .joinToString(" ") { "$it*" }
            if (ftsToken.isNotEmpty()) {
                runCatching {
                    dictionaryDao.searchFullText(ftsToken, limit - results.size)
                }.getOrNull()?.forEach { entry ->
                    results.putIfAbsent(entry.id, entry)
                }
            }
        }

        // Tier 3: Direct Vietnamese substring lookup (handles all Vietnamese tone marks reliably)
        if (results.size < limit) {
            dictionaryDao.searchByMeaning(cleaned, limit - results.size).forEach { entry ->
                results.putIfAbsent(entry.id, entry)
            }
        }

        return results.values.map { it.toDomain() }
    }

    suspend fun quickLookup(rawText: String): List<DictionaryEntry> {
        val token = rawText
            .trim()
            .trim('.', ',', '!', '?', ';', ':', '"', '\'', '(', ')')
            .lowercase()
        if (token.isEmpty()) return emptyList()

        val exact = dictionaryDao.findExact(token)
        if (exact.isNotEmpty()) {
            return exact.map { it.toDomain() }
        }

        for (candidate in generateLemmaCandidates(token)) {
            val found = dictionaryDao.findExact(candidate)
            if (found.isNotEmpty()) {
                return found.map { it.toDomain() }
            }
        }

        return search(token, limit = 10)
    }

    suspend fun saveToDefaultWordbook(entryId: Long): Boolean {
        return saveToFolder(entryId, WordFolders.DEFAULT)
    }

    suspend fun saveToFolder(entryId: Long, folderName: String): Boolean {
        val folder = ensureFolder(folderName)
        return saveToFolderById(entryId, folder.id)
    }

    suspend fun saveToFolderById(entryId: Long, folderId: Long): Boolean {
        val folder = wordFolderDao.findById(folderId) ?: ensureFolder(WordFolders.DEFAULT)
        val existing = savedWordDao.findSavedWordByFolderId(folderId = folder.id, entryId = entryId)
        if (existing != null) return false
        val now = System.currentTimeMillis()
        savedWordDao.insertSavedWord(
            SavedWord(
                entryId = entryId,
                folderId = folder.id,
                folderName = folder.name,
                easeFactor = 2.5,
                intervalDays = 0,
                repetitions = 0,
                lapses = 0,
                state = SrsState.NEW.name,
                nextReviewAt = now,
                lastReviewedAt = null,
                createdAt = now
            )
        )
        return true
    }

    suspend fun createFolderAndSave(
        entryId: Long,
        folderName: String,
        iconName: String = "label",
        colorHex: String = "#0F766E"
    ): WordFolder? {
        val folder = createFolder(folderName, iconName, colorHex) ?: return null
        saveToFolderById(entryId, folder.id)
        return folder
    }

    suspend fun removeFromFolder(entryId: Long, folderId: Long) {
        savedWordDao.deleteSavedWordByEntryAndFolder(entryId = entryId, folderId = folderId)
    }

    suspend fun removeEntryFromAllFolders(entryId: Long) {
        savedWordDao.deleteAllForEntry(entryId)
    }

    suspend fun saveSuggestedWordToConversationFolder(
        word: String,
        pos: String,
        meaningVi: String
    ): Boolean {
        return savePhraseToFolder(
            word = word,
            pos = pos,
            meaningVi = meaningVi,
            folderName = WordFolders.CONVERSATION
        )
    }

    suspend fun savePhraseToFolder(
        word: String,
        pos: String,
        meaningVi: String,
        folderName: String
    ): Boolean {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return false

        val normalized = cleaned.lowercase()
        val existingEntry = dictionaryDao.findExact(normalized).firstOrNull()
        val entryId = if (existingEntry != null) {
            existingEntry.id
        } else {
            dictionaryDao.insertEntry(
                DictionaryEntryEntity(
                    word = cleaned,
                    wordNormalized = normalized,
                    ipaUk = "",
                    ipaUs = "",
                    pos = pos.trim().ifEmpty { "phrase" },
                    meaningVi = meaningVi.trim(),
                    examplesJson = "[]",
                    synonymsJson = "[]",
                    collocationsJson = "[]",
                    cefrLevel = null,
                    frequencyRank = 50_000
                )
            )
        }
        if (entryId <= 0L) return false
        return saveToFolder(entryId, folderName)
    }

    suspend fun recordSearch(query: String, entryId: Long?) {
        // Search history persistence can be wired to a dedicated DAO when needed.
    }

    /**
     * Caches or enriches a dictionary entry fetched from online sources.
     * Returns Pair(entry, newlyCached). Preserves existing Room ID & SavedWord links.
     */
    suspend fun cacheOnlineEntry(
        word: String,
        ipaUk: String,
        ipaUs: String,
        pos: String,
        meaningVi: String,
        examples: List<ExampleItem>,
        synonyms: List<String>,
        collocations: List<CollocationItem>,
        cefrLevel: String?
    ): Pair<DictionaryEntry, Boolean> {
        val cleanedWord = word.trim()
        val normalized = cleanedWord.lowercase()
        val existing = dictionaryDao.findExact(normalized).firstOrNull()

        if (existing != null) {
            val mergedExamples = (decodeOrEmpty<ExampleItem>(existing.examplesJson) + examples)
                .filter { it.en.isNotBlank() }
                .distinctBy { it.en.trim().lowercase() }
                .take(8)
            val mergedSynonyms = (decodeOrEmpty<String>(existing.synonymsJson) + synonyms)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .take(10)
            val mergedCollocations = (decodeOrEmpty<CollocationItem>(existing.collocationsJson) + collocations)
                .filter { it.phrase.isNotBlank() }
                .distinctBy { it.phrase.trim().lowercase() }
                .take(8)

            val updated = existing.copy(
                ipaUk = existing.ipaUk.ifBlank { ipaUk.trim() },
                ipaUs = existing.ipaUs.ifBlank { ipaUs.trim() },
                pos = existing.pos.ifBlank { pos.trim().ifEmpty { "noun" } },
                meaningVi = existing.meaningVi.ifBlank { meaningVi.trim() },
                examplesJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(ExampleItem.serializer()),
                    mergedExamples
                ),
                synonymsJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                    mergedSynonyms
                ),
                collocationsJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(CollocationItem.serializer()),
                    mergedCollocations
                ),
                cefrLevel = existing.cefrLevel ?: cefrLevel?.trim()?.ifEmpty { null }
            )
            dictionaryDao.updateEntry(updated)
            return updated.toDomain() to false
        }

        val entity = DictionaryEntryEntity(
            word = cleanedWord,
            wordNormalized = normalized,
            ipaUk = ipaUk.trim(),
            ipaUs = ipaUs.trim(),
            pos = pos.trim().ifEmpty { "noun" },
            meaningVi = meaningVi.trim().ifEmpty { cleanedWord },
            examplesJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ExampleItem.serializer()),
                examples.take(8)
            ),
            synonymsJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                synonyms.take(10)
            ),
            collocationsJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(CollocationItem.serializer()),
                collocations.take(8)
            ),
            cefrLevel = cefrLevel?.trim()?.ifEmpty { null },
            frequencyRank = 25_000
        )
        val insertedId = dictionaryDao.insertEntry(entity)
        val persisted = if (insertedId > 0L) {
            entity.copy(id = insertedId)
        } else {
            dictionaryDao.findExact(normalized).firstOrNull() ?: entity
        }
        return persisted.toDomain() to true
    }

    private fun DictionaryEntryEntity.toDomain(): DictionaryEntry = DictionaryEntry(
        id = id,
        word = word,
        ipaUk = ipaUk,
        ipaUs = ipaUs,
        pos = pos,
        meaningVi = meaningVi,
        examples = decodeOrEmpty(examplesJson),
        synonyms = decodeOrEmpty(synonymsJson),
        collocations = decodeOrEmpty(collocationsJson),
        cefrLevel = cefrLevel,
        frequencyRank = frequencyRank
    )

    private inline fun <reified T> decodeOrEmpty(raw: String): List<T> =
        runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())

    private fun nextPrefixUpperBound(prefix: String): String? {
        if (prefix.isEmpty()) return null
        val lastChar = prefix.last()
        return if (lastChar < Char.MAX_VALUE) {
            prefix.dropLast(1) + (lastChar + 1)
        } else {
            null
        }
    }

    private fun generateLemmaCandidates(word: String): List<String> {
        val candidates = mutableListOf<String>()
        if (word.endsWith("ies") && word.length > 4) candidates += word.dropLast(3) + "y"
        if (word.endsWith("es") && word.length > 3) candidates += word.dropLast(2)
        if (word.endsWith("s") && word.length > 2) candidates += word.dropLast(1)
        if (word.endsWith("ied") && word.length > 4) candidates += word.dropLast(3) + "y"
        if (word.endsWith("ed") && word.length > 3) {
            candidates += word.dropLast(2)
            candidates += word.dropLast(1)
        }
        if (word.endsWith("ing") && word.length > 4) {
            val stem = word.dropLast(3)
            candidates += stem
            candidates += stem + "e"
        }
        return candidates.distinct()
    }
}
