package com.enziidict.app.core.database

import com.enziidict.app.data.local.dao.FocusLockDao
import com.enziidict.app.data.local.dao.StudyProgressDao
import com.enziidict.app.data.local.entity.DailyStudyLog
import com.enziidict.app.data.local.entity.FocusLockConfig
import com.enziidict.app.data.local.entity.LockedAppEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class FocusLockStatus {
    DISABLED,
    UNLOCKED_BY_GOAL,
    EMERGENCY_UNLOCKED,
    LOCKING
}

data class FocusLockEvaluation(
    val status: FocusLockStatus,
    val studiedMinutesToday: Int,
    val requiredStudyMinutes: Int,
    val knowledgeCountToday: Int,
    val requiredKnowledgeCount: Int,
    val requireBoth: Boolean,
    val goalReached: Boolean,
    val isEmergencyActive: Boolean,
    val emergencyRemainingSeconds: Long,
    val emergencyUsedToday: Int,
    val maxEmergencyPerDay: Int,
    val lockedPackages: Set<String>
) {
    val emergencyRemainingUsesToday: Int
        get() = (maxEmergencyPerDay - emergencyUsedToday).coerceAtLeast(0)
}

sealed interface EmergencyUnlockResult {
    data class Granted(val untilMillis: Long, val remainingToday: Int) : EmergencyUnlockResult
    data object LimitReached : EmergencyUnlockResult
}

object FocusLockPolicyEvaluator {

    fun computeStudiedMinutes(todayLog: DailyStudyLog?): Int {
        if (todayLog == null) return 0
        val estimatedExtraSec =
            todayLog.wordsReviewed * 25 + todayLog.shadowingCount * 45 + todayLog.wordsLearned * 20
        val total = todayLog.speakingMinutes + (estimatedExtraSec / 60)
        return if (total > 0) total else if (todayLog.xpEarned > 0) 1 else 0
    }

    fun computeKnowledgeCount(todayLog: DailyStudyLog?): Int {
        if (todayLog == null) return 0
        return (todayLog.wordsReviewed + todayLog.shadowingCount + todayLog.wordsLearned).coerceAtLeast(0)
    }

    fun evaluate(
        config: FocusLockConfig,
        lockedPackages: Set<String>,
        todayLog: DailyStudyLog?,
        nowMillis: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): FocusLockEvaluation {
        val studiedMinutes = computeStudiedMinutes(todayLog)
        val knowledgeCount = computeKnowledgeCount(todayLog)

        val todayKey = today.toString()
        val usedToday = if (config.emergencyUsageDate == todayKey) {
            config.emergencyUsedCountToday.coerceAtLeast(0)
        } else {
            0
        }

        val reqMinutes = config.requiredStudyMinutes.coerceAtLeast(0)
        val reqKnowledge = config.requiredKnowledgeCount.coerceAtLeast(0)

        val timeMet = reqMinutes <= 0 || studiedMinutes >= reqMinutes
        val knowledgeMet = reqKnowledge <= 0 || knowledgeCount >= reqKnowledge
        val goalReached = when {
            reqMinutes <= 0 && reqKnowledge <= 0 -> true
            reqMinutes > 0 && reqKnowledge > 0 ->
                if (config.requireBoth) (timeMet && knowledgeMet) else (timeMet || knowledgeMet)
            reqMinutes > 0 -> timeMet
            else -> knowledgeMet
        }

        val emergencyRemainingSec = ((config.emergencyUnlockedUntilMillis - nowMillis) / 1000L)
            .coerceAtLeast(0L)
        val isEmergencyActive = config.emergencyUnlockedUntilMillis > nowMillis

        val status = when {
            !config.isEnabled -> FocusLockStatus.DISABLED
            goalReached -> FocusLockStatus.UNLOCKED_BY_GOAL
            isEmergencyActive -> FocusLockStatus.EMERGENCY_UNLOCKED
            else -> FocusLockStatus.LOCKING
        }

        return FocusLockEvaluation(
            status = status,
            studiedMinutesToday = studiedMinutes,
            requiredStudyMinutes = reqMinutes,
            knowledgeCountToday = knowledgeCount,
            requiredKnowledgeCount = reqKnowledge,
            requireBoth = config.requireBoth,
            goalReached = goalReached,
            isEmergencyActive = isEmergencyActive,
            emergencyRemainingSeconds = emergencyRemainingSec,
            emergencyUsedToday = usedToday,
            maxEmergencyPerDay = config.maxEmergencyUnlocksPerDay.coerceAtLeast(0),
            lockedPackages = lockedPackages
        )
    }

    fun shouldBlockPackage(
        packageName: String,
        evaluation: FocusLockEvaluation,
        ownPackageName: String
    ): Boolean {
        if (packageName.isBlank() || packageName == ownPackageName) return false
        if (evaluation.status != FocusLockStatus.LOCKING) return false
        return packageName in evaluation.lockedPackages
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FocusLockRepository @Inject constructor(
    private val focusLockDao: FocusLockDao,
    private val studyProgressDao: StudyProgressDao
) {
    private val mutex = Mutex()

    fun observeConfig(): Flow<FocusLockConfig> =
        focusLockDao.observeConfig().map { it ?: FocusLockConfig.default() }

    fun observeLockedApps(): Flow<List<LockedAppEntity>> =
        focusLockDao.observeLockedApps()

    fun observeEvaluation(today: LocalDate = LocalDate.now()): Flow<FocusLockEvaluation> = combine(
        observeConfig(),
        observeLockedApps(),
        studyProgressDao.observeLogForDate(today.toString())
    ) { config, apps, todayLog ->
        FocusLockPolicyEvaluator.evaluate(
            config = config,
            lockedPackages = apps.map { it.packageName }.toSet(),
            todayLog = todayLog,
            nowMillis = System.currentTimeMillis(),
            today = today
        )
    }

    suspend fun getCurrentEvaluation(
        nowMillis: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): FocusLockEvaluation {
        val config = focusLockDao.getConfig() ?: FocusLockConfig.default()
        val apps = focusLockDao.getLockedApps().map { it.packageName }.toSet()
        val todayLog = studyProgressDao.getLogForDate(today.toString())
        return FocusLockPolicyEvaluator.evaluate(config, apps, todayLog, nowMillis, today)
    }

    suspend fun setEnabled(enabled: Boolean) = mutex.withLock {
        val current = focusLockDao.getConfig() ?: FocusLockConfig.default()
        focusLockDao.upsertConfig(current.copy(isEnabled = enabled))
    }

    suspend fun updateGoals(
        requiredStudyMinutes: Int,
        requiredKnowledgeCount: Int,
        requireBoth: Boolean
    ) = mutex.withLock {
        val current = focusLockDao.getConfig() ?: FocusLockConfig.default()
        focusLockDao.upsertConfig(
            current.copy(
                requiredStudyMinutes = requiredStudyMinutes.coerceIn(0, 240),
                requiredKnowledgeCount = requiredKnowledgeCount.coerceIn(0, 200),
                requireBoth = requireBoth
            )
        )
    }

    suspend fun updateEmergencySettings(
        durationMinutes: Int,
        maxPerDay: Int
    ) = mutex.withLock {
        val current = focusLockDao.getConfig() ?: FocusLockConfig.default()
        focusLockDao.upsertConfig(
            current.copy(
                emergencyUnlockDurationMinutes = durationMinutes.coerceIn(1, 30),
                maxEmergencyUnlocksPerDay = maxPerDay.coerceIn(0, 10)
            )
        )
    }

    suspend fun toggleLockedApp(packageName: String, appLabel: String, locked: Boolean) = mutex.withLock {
        if (locked) {
            focusLockDao.upsertLockedApp(
                LockedAppEntity(
                    packageName = packageName,
                    appLabel = appLabel.ifBlank { packageName },
                    addedAt = System.currentTimeMillis()
                )
            )
        } else {
            focusLockDao.deleteLockedApp(packageName)
        }
    }

    suspend fun resetEmergencyIfNewDay(today: LocalDate = LocalDate.now()) = mutex.withLock {
        val current = focusLockDao.getConfig() ?: return@withLock
        val todayKey = today.toString()
        if (current.emergencyUsageDate != todayKey &&
            (current.emergencyUsedCountToday != 0 || current.emergencyUnlockedUntilMillis != 0L)
        ) {
            focusLockDao.upsertConfig(
                current.copy(
                    emergencyUsedCountToday = 0,
                    emergencyUsageDate = todayKey,
                    emergencyUnlockedUntilMillis = 0L
                )
            )
        }
    }

    suspend fun triggerEmergencyUnlock(
        nowMillis: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): EmergencyUnlockResult = mutex.withLock {
        val current = focusLockDao.getConfig() ?: FocusLockConfig.default()
        val todayKey = today.toString()
        val usedToday = if (current.emergencyUsageDate == todayKey) {
            current.emergencyUsedCountToday.coerceAtLeast(0)
        } else {
            0
        }

        val maxPerDay = current.maxEmergencyUnlocksPerDay.coerceAtLeast(0)
        if (usedToday >= maxPerDay) {
            if (current.emergencyUsageDate != todayKey) {
                focusLockDao.upsertConfig(
                    current.copy(
                        emergencyUsedCountToday = 0,
                        emergencyUsageDate = todayKey
                    )
                )
            }
            return@withLock EmergencyUnlockResult.LimitReached
        }

        val durationMs = current.emergencyUnlockDurationMinutes.coerceAtLeast(1) * 60_000L
        val untilMillis = nowMillis + durationMs
        val nextUsed = usedToday + 1
        focusLockDao.upsertConfig(
            current.copy(
                emergencyUnlockedUntilMillis = untilMillis,
                emergencyUsedCountToday = nextUsed,
                emergencyUsageDate = todayKey
            )
        )
        EmergencyUnlockResult.Granted(
            untilMillis = untilMillis,
            remainingToday = (maxPerDay - nextUsed).coerceAtLeast(0)
        )
    }
}
