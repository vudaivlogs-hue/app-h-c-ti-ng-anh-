package com.enziidict.app.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enziidict.app.data.local.EnziiDatabase
import com.enziidict.app.data.local.dao.DictionaryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DictionaryUpgradeState {
    data object Idle : DictionaryUpgradeState
    data class Upgrading(val message: String) : DictionaryUpgradeState
    data class Completed(val totalEntries: Int) : DictionaryUpgradeState
}

@Singleton
class DictionaryPackUpgrader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: EnziiDatabase,
    private val dictionaryDao: DictionaryDao
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DictionaryUpgradeState>(DictionaryUpgradeState.Idle)
    val state: StateFlow<DictionaryUpgradeState> = _state.asStateFlow()

    suspend fun upgradeIfNeeded() {
        val existingCount = dictionaryDao.countEntries()
        if (existingCount >= MIN_LARGE_PACK_ENTRIES) {
            _state.value = DictionaryUpgradeState.Completed(existingCount)
            return
        }

        val tmpFile = File(context.cacheDir, "enzii_seed_pack_tmp.db")
        withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("dictionary.db").use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onSuccess {
                upgradeFromFile(tmpFile, deleteSeedAfter = true)
            }.onFailure {
                tmpFile.delete()
                _state.value = DictionaryUpgradeState.Completed(existingCount)
            }
        }
    }

    internal suspend fun upgradeFromFile(
        seedPackFile: File,
        deleteSeedAfter: Boolean = false
    ): Int = mutex.withLock {
        val beforeCount = dictionaryDao.countEntries()
        if (!seedPackFile.exists() || beforeCount >= MIN_LARGE_PACK_ENTRIES) {
            _state.value = DictionaryUpgradeState.Completed(beforeCount)
            return beforeCount
        }

        _state.value = DictionaryUpgradeState.Upgrading(
            "Đang nâng cấp kho 100.000+ từ điển offline (giữ nguyên toàn bộ sổ tay)..."
        )

        withContext(Dispatchers.IO) {
            val writableDb = database.openHelper.writableDatabase
            try {
                // Verify seed pack actually has more entries before attaching
                val seedCount = SQLiteDatabase.openDatabase(
                    seedPackFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                ).use { raw ->
                    raw.rawQuery("SELECT COUNT(*) FROM dictionary_entries", null).use { c ->
                        if (c.moveToFirst()) c.getInt(0) else 0
                    }
                }

                if (seedCount <= beforeCount) {
                    _state.value = DictionaryUpgradeState.Completed(beforeCount)
                    return@withContext beforeCount
                }

                mergeSeedEntries(writableDb, seedPackFile.absolutePath)
            } finally {
                if (deleteSeedAfter) {
                    seedPackFile.delete()
                }
            }
        }

        val afterCount = dictionaryDao.countEntries()
        _state.value = DictionaryUpgradeState.Completed(afterCount)
        afterCount
    }

    private fun mergeSeedEntries(writableDb: SupportSQLiteDatabase, seedPath: String) {
        writableDb.execSQL("ATTACH DATABASE ? AS seed_db", arrayOf(seedPath))
        try {
            writableDb.beginTransaction()
            try {
                // Temporarily drop AFTER_INSERT FTS trigger so inserting 100K+ rows runs in seconds
                writableDb.execSQL("DROP TRIGGER IF EXISTS `room_fts_content_sync_dictionary_fts_AFTER_INSERT`")

                writableDb.execSQL(
                    """
                    INSERT INTO `dictionary_entries` (
                        `word`, `word_normalized`, `ipa_uk`, `ipa_us`, `pos`, `meaning_vi`,
                        `examples_json`, `synonyms_json`, `collocations_json`, `cefr_level`, `frequency_rank`
                    )
                    SELECT
                        s.`word`, s.`word_normalized`, s.`ipa_uk`, s.`ipa_us`, s.`pos`, s.`meaning_vi`,
                        s.`examples_json`, s.`synonyms_json`, s.`collocations_json`, s.`cefr_level`, s.`frequency_rank`
                    FROM `seed_db`.`dictionary_entries` s
                    WHERE NOT EXISTS (
                        SELECT 1 FROM `dictionary_entries` d
                        WHERE d.`word_normalized` = s.`word_normalized` AND d.`pos` = s.`pos`
                    )
                    """.trimIndent()
                )

                writableDb.execSQL("INSERT INTO `dictionary_fts`(`dictionary_fts`) VALUES('rebuild')")
                writableDb.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_dictionary_fts_AFTER_INSERT`
                    AFTER INSERT ON `dictionary_entries` BEGIN
                        INSERT INTO `dictionary_fts`(`docid`, `word`, `meaning_vi`, `collocations_json`, `synonyms_json`)
                        VALUES (NEW.`rowid`, NEW.`word`, NEW.`meaning_vi`, NEW.`collocations_json`, NEW.`synonyms_json`);
                    END
                    """.trimIndent()
                )
                writableDb.setTransactionSuccessful()
            } finally {
                writableDb.endTransaction()
            }
        } finally {
            runCatching { writableDb.execSQL("DETACH DATABASE seed_db") }
        }
    }

    companion object {
        const val MIN_LARGE_PACK_ENTRIES = 50_000
    }
}
