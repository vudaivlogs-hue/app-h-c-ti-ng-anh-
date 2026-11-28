package com.enziidict.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enziidict.app.data.local.entity.FocusLockConfig
import com.enziidict.app.data.local.entity.LockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusLockDao {

    @Query("SELECT * FROM focus_lock_config WHERE id = 1 LIMIT 1")
    fun observeConfig(): Flow<FocusLockConfig?>

    @Query("SELECT * FROM focus_lock_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): FocusLockConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: FocusLockConfig)

    @Query("SELECT * FROM locked_apps ORDER BY app_label ASC")
    fun observeLockedApps(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps ORDER BY app_label ASC")
    suspend fun getLockedApps(): List<LockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLockedApp(app: LockedAppEntity)

    @Query("DELETE FROM locked_apps WHERE package_name = :packageName")
    suspend fun deleteLockedApp(packageName: String)
}
