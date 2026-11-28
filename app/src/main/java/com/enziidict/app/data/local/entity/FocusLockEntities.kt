package com.enziidict.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_lock_config")
data class FocusLockConfig(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "required_study_minutes")
    val requiredStudyMinutes: Int,
    @ColumnInfo(name = "required_knowledge_count")
    val requiredKnowledgeCount: Int,
    @ColumnInfo(name = "require_both")
    val requireBoth: Boolean,
    @ColumnInfo(name = "emergency_unlock_duration_minutes")
    val emergencyUnlockDurationMinutes: Int,
    @ColumnInfo(name = "max_emergency_unlocks_per_day")
    val maxEmergencyUnlocksPerDay: Int,
    @ColumnInfo(name = "emergency_unlocked_until_millis")
    val emergencyUnlockedUntilMillis: Long,
    @ColumnInfo(name = "emergency_used_count_today")
    val emergencyUsedCountToday: Int,
    @ColumnInfo(name = "emergency_usage_date")
    val emergencyUsageDate: String
) {
    companion object {
        const val SINGLETON_ID = 1

        fun default(): FocusLockConfig = FocusLockConfig(
            id = SINGLETON_ID,
            isEnabled = false,
            requiredStudyMinutes = 20,
            requiredKnowledgeCount = 10,
            requireBoth = false,
            emergencyUnlockDurationMinutes = 5,
            maxEmergencyUnlocksPerDay = 2,
            emergencyUnlockedUntilMillis = 0L,
            emergencyUsedCountToday = 0,
            emergencyUsageDate = ""
        )
    }
}

@Entity(tableName = "locked_apps")
data class LockedAppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "app_label")
    val appLabel: String,
    @ColumnInfo(name = "added_at")
    val addedAt: Long
)
