package com.enziidict.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enziidict.app.data.local.entity.DailyStudyLog
import com.enziidict.app.data.local.entity.UserStreakState
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyProgressDao {

    @Query("SELECT * FROM daily_study_logs WHERE date_yyyy_mm_dd = :date LIMIT 1")
    fun observeLogForDate(date: String): Flow<DailyStudyLog?>

    @Query("SELECT * FROM daily_study_logs WHERE date_yyyy_mm_dd = :date LIMIT 1")
    suspend fun getLogForDate(date: String): DailyStudyLog?

    @Query("SELECT * FROM daily_study_logs WHERE date_yyyy_mm_dd >= :sinceDate ORDER BY date_yyyy_mm_dd ASC")
    fun observeLogsFrom(sinceDate: String): Flow<List<DailyStudyLog>>

    @Query("SELECT * FROM daily_study_logs WHERE date_yyyy_mm_dd >= :sinceDate ORDER BY date_yyyy_mm_dd ASC")
    suspend fun getLogsFrom(sinceDate: String): List<DailyStudyLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: DailyStudyLog)

    @Query("SELECT * FROM user_streak_state WHERE id = 1 LIMIT 1")
    fun observeStreakState(): Flow<UserStreakState?>

    @Query("SELECT * FROM user_streak_state WHERE id = 1 LIMIT 1")
    suspend fun getStreakState(): UserStreakState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreakState(state: UserStreakState)
}
