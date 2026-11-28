package com.enziidict.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_study_logs")
data class DailyStudyLog(
    @PrimaryKey
    @ColumnInfo(name = "date_yyyy_mm_dd")
    val dateYyyyMmDd: String,
    @ColumnInfo(name = "words_reviewed")
    val wordsReviewed: Int,
    @ColumnInfo(name = "words_learned")
    val wordsLearned: Int,
    @ColumnInfo(name = "speaking_minutes")
    val speakingMinutes: Int,
    @ColumnInfo(name = "shadowing_count")
    val shadowingCount: Int,
    @ColumnInfo(name = "xp_earned")
    val xpEarned: Int,
    @ColumnInfo(name = "goal_completed")
    val goalCompleted: Boolean
)

@Entity(tableName = "user_streak_state")
data class UserStreakState(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "current_streak")
    val currentStreak: Int,
    @ColumnInfo(name = "longest_streak")
    val longestStreak: Int,
    @ColumnInfo(name = "freeze_count")
    val freezeCount: Int,
    @ColumnInfo(name = "last_active_date")
    val lastActiveDate: String?,
    @ColumnInfo(name = "daily_xp_goal")
    val dailyXpGoal: Int
) {
    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_DAILY_XP_GOAL = 50
        const val INITIAL_FREEZE_COUNT = 1
        const val MAX_FREEZE_COUNT = 3

        fun default(): UserStreakState = UserStreakState(
            id = SINGLETON_ID,
            currentStreak = 0,
            longestStreak = 0,
            freezeCount = INITIAL_FREEZE_COUNT,
            lastActiveDate = null,
            dailyXpGoal = DEFAULT_DAILY_XP_GOAL
        )
    }
}
