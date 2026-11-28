package com.enziidict.app.data.local

import android.content.Context
import androidx.room.Room
import com.enziidict.app.data.local.dao.DictionaryDao
import com.enziidict.app.data.local.dao.FocusLockDao
import com.enziidict.app.data.local.dao.ReadingDao
import com.enziidict.app.data.local.dao.SavedWordDao
import com.enziidict.app.data.local.dao.SpeakingDao
import com.enziidict.app.data.local.dao.StudyProgressDao
import com.enziidict.app.data.local.dao.WordFolderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideEnziiDatabase(@ApplicationContext context: Context): EnziiDatabase {
        return Room.databaseBuilder(
            context,
            EnziiDatabase::class.java,
            "dictionary.db"
        )
            .createFromAsset("dictionary.db")
            .addMigrations(
                EnziiDatabaseMigrations.MIGRATION_1_2,
                EnziiDatabaseMigrations.MIGRATION_2_3,
                EnziiDatabaseMigrations.MIGRATION_3_4
            )
            .build()
    }

    @Provides
    fun provideDictionaryDao(db: EnziiDatabase): DictionaryDao = db.dictionaryDao()

    @Provides
    fun provideWordFolderDao(db: EnziiDatabase): WordFolderDao = db.wordFolderDao()

    @Provides
    fun provideSavedWordDao(db: EnziiDatabase): SavedWordDao = db.savedWordDao()

    @Provides
    fun provideSpeakingDao(db: EnziiDatabase): SpeakingDao = db.speakingDao()

    @Provides
    fun provideReadingDao(db: EnziiDatabase): ReadingDao = db.readingDao()

    @Provides
    fun provideStudyProgressDao(db: EnziiDatabase): StudyProgressDao = db.studyProgressDao()

    @Provides
    fun provideFocusLockDao(db: EnziiDatabase): FocusLockDao = db.focusLockDao()
}
