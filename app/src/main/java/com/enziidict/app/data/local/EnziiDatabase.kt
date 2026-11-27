package com.enziidict.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.enziidict.app.data.local.dao.DictionaryDao
import com.enziidict.app.data.local.dao.ReadingDao
import com.enziidict.app.data.local.dao.SavedWordDao
import com.enziidict.app.data.local.dao.SpeakingDao
import com.enziidict.app.data.local.dao.WordFolderDao
import com.enziidict.app.data.local.entity.BilingualArticle
import com.enziidict.app.data.local.entity.DictionaryEntry
import com.enziidict.app.data.local.entity.DictionaryFts
import com.enziidict.app.data.local.entity.SavedWord
import com.enziidict.app.data.local.entity.SpeakingMessage
import com.enziidict.app.data.local.entity.SpeakingSession
import com.enziidict.app.data.local.entity.WordFolder

@Database(
    entities = [
        DictionaryEntry::class,
        DictionaryFts::class,
        WordFolder::class,
        SavedWord::class,
        SpeakingSession::class,
        SpeakingMessage::class,
        BilingualArticle::class
    ],
    version = 2,
    exportSchema = true
)
abstract class EnziiDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun wordFolderDao(): WordFolderDao
    abstract fun savedWordDao(): SavedWordDao
    abstract fun speakingDao(): SpeakingDao
    abstract fun readingDao(): ReadingDao
}
