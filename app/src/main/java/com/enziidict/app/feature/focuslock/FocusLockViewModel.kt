package com.enziidict.app.feature.focuslock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enziidict.app.core.database.FocusLockEvaluation
import com.enziidict.app.core.database.FocusLockRepository
import com.enziidict.app.core.database.FocusLockStatus
import com.enziidict.app.data.local.entity.FocusLockConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SelectableAppUi(
    val packageName: String,
    val label: String,
    val isLocked: Boolean
)

data class FocusLockUiState(
    val config: FocusLockConfig = FocusLockConfig.default(),
    val evaluation: FocusLockEvaluation = FocusLockEvaluation(
        status = FocusLockStatus.DISABLED,
        studiedMinutesToday = 0,
        requiredStudyMinutes = 20,
        knowledgeCountToday = 0,
        requiredKnowledgeCount = 10,
        requireBoth = false,
        goalReached = false,
        isEmergencyActive = false,
        emergencyRemainingSeconds = 0L,
        emergencyUsedToday = 0,
        maxEmergencyPerDay = 2,
        lockedPackages = emptySet()
    ),
    val hasUsagePermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val searchQuery: String = "",
    val apps: List<SelectableAppUi> = emptyList(),
    val isLoadingApps: Boolean = true,
    val lockedCount: Int = 0
)

@HiltViewModel
class FocusLockViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val focusLockRepository: FocusLockRepository
) : ViewModel() {

    private val installedAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isLoadingAppsFlow = MutableStateFlow(true)
    private val searchQueryFlow = MutableStateFlow("")
    private val permissionsFlow = MutableStateFlow(
        FocusLockPermissions.hasUsageStatsPermission(appContext) to
            FocusLockPermissions.hasOverlayPermission(appContext)
    )

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val uiState: StateFlow<FocusLockUiState> = combine(
        focusLockRepository.observeConfig(),
        focusLockRepository.observeLockedApps(),
        focusLockRepository.observeEvaluation(),
        installedAppsFlow,
        isLoadingAppsFlow,
        searchQueryFlow,
        permissionsFlow,
        tickerFlow
    ) { values ->
        val config = values[0] as FocusLockConfig
        @Suppress("UNCHECKED_CAST")
        val lockedEntities = values[1] as List<com.enziidict.app.data.local.entity.LockedAppEntity>
        val baseEval = values[2] as FocusLockEvaluation
        @Suppress("UNCHECKED_CAST")
        val installed = values[3] as List<InstalledAppInfo>
        val loadingApps = values[4] as Boolean
        val query = values[5] as String
        @Suppress("UNCHECKED_CAST")
        val perms = values[6] as Pair<Boolean, Boolean>
        val nowMillis = values[7] as Long

        val lockedSet = lockedEntities.map { it.packageName }.toSet()
        val liveRemainingSec = ((config.emergencyUnlockedUntilMillis - nowMillis) / 1000L).coerceAtLeast(0L)
        val isEmergencyLive = config.emergencyUnlockedUntilMillis > nowMillis
        val liveStatus = when {
            !config.isEnabled -> FocusLockStatus.DISABLED
            baseEval.goalReached -> FocusLockStatus.UNLOCKED_BY_GOAL
            isEmergencyLive -> FocusLockStatus.EMERGENCY_UNLOCKED
            else -> FocusLockStatus.LOCKING
        }
        val eval = baseEval.copy(
            status = liveStatus,
            isEmergencyActive = isEmergencyLive,
            emergencyRemainingSeconds = liveRemainingSec
        )

        val cleanedQuery = query.trim().lowercase()
        val selectableApps = installed
            .filter { app ->
                cleanedQuery.isEmpty() ||
                    app.label.lowercase().contains(cleanedQuery) ||
                    app.packageName.lowercase().contains(cleanedQuery)
            }
            .map { app ->
                SelectableAppUi(
                    packageName = app.packageName,
                    label = app.label,
                    isLocked = app.packageName in lockedSet
                )
            }
            .sortedWith(compareByDescending<SelectableAppUi> { it.isLocked }.thenBy { it.label.lowercase() })

        FocusLockUiState(
            config = config,
            evaluation = eval,
            hasUsagePermission = perms.first,
            hasOverlayPermission = perms.second,
            searchQuery = query,
            apps = selectableApps,
            isLoadingApps = loadingApps,
            lockedCount = lockedSet.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusLockUiState())

    init {
        viewModelScope.launch {
            focusLockRepository.resetEmergencyIfNewDay()
            refreshPermissions()
            isLoadingAppsFlow.value = true
            val loaded = withContext(Dispatchers.IO) {
                FocusLockPermissions.loadInstalledLaunchableApps(appContext)
            }
            installedAppsFlow.value = loaded
            isLoadingAppsFlow.value = false
        }
    }

    fun refreshPermissions() {
        permissionsFlow.value =
            FocusLockPermissions.hasUsageStatsPermission(appContext) to
            FocusLockPermissions.hasOverlayPermission(appContext)
        syncServiceWithCurrentState()
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            focusLockRepository.setEnabled(enabled)
            syncServiceWithCurrentState(enabledOverride = enabled)
        }
    }

    fun updateRequiredMinutes(minutes: Int) {
        viewModelScope.launch {
            val current = uiState.value.config
            focusLockRepository.updateGoals(
                requiredStudyMinutes = minutes,
                requiredKnowledgeCount = current.requiredKnowledgeCount,
                requireBoth = current.requireBoth
            )
        }
    }

    fun updateRequiredKnowledge(count: Int) {
        viewModelScope.launch {
            val current = uiState.value.config
            focusLockRepository.updateGoals(
                requiredStudyMinutes = current.requiredStudyMinutes,
                requiredKnowledgeCount = count,
                requireBoth = current.requireBoth
            )
        }
    }

    fun setRequireBoth(requireBoth: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.config
            focusLockRepository.updateGoals(
                requiredStudyMinutes = current.requiredStudyMinutes,
                requiredKnowledgeCount = current.requiredKnowledgeCount,
                requireBoth = requireBoth
            )
        }
    }

    fun updateEmergencyDuration(durationMinutes: Int) {
        viewModelScope.launch {
            val current = uiState.value.config
            focusLockRepository.updateEmergencySettings(
                durationMinutes = durationMinutes,
                maxPerDay = current.maxEmergencyUnlocksPerDay
            )
        }
    }

    fun updateMaxEmergencyPerDay(maxPerDay: Int) {
        viewModelScope.launch {
            val current = uiState.value.config
            focusLockRepository.updateEmergencySettings(
                durationMinutes = current.emergencyUnlockDurationMinutes,
                maxPerDay = maxPerDay
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun toggleLockedApp(app: SelectableAppUi) {
        viewModelScope.launch {
            focusLockRepository.toggleLockedApp(
                packageName = app.packageName,
                appLabel = app.label,
                locked = !app.isLocked
            )
            syncServiceWithCurrentState()
        }
    }

    private fun syncServiceWithCurrentState(enabledOverride: Boolean? = null) {
        viewModelScope.launch {
            val eval = focusLockRepository.getCurrentEvaluation()
            val enabled = enabledOverride ?: (eval.status != FocusLockStatus.DISABLED)
            AppBlockerService.syncServiceState(
                context = appContext,
                shouldRun = enabled && eval.lockedPackages.isNotEmpty()
            )
        }
    }
}
