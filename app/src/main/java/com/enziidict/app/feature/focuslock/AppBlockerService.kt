package com.enziidict.app.feature.focuslock

import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.enziidict.app.MainActivity
import com.enziidict.app.R
import com.enziidict.app.core.database.FocusLockPolicyEvaluator
import com.enziidict.app.core.database.FocusLockRepository
import com.enziidict.app.core.notification.EnziiNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class AppBlockerService : Service() {

    @Inject
    lateinit var focusLockRepository: FocusLockRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var lockedLabelsByPkg: Map<String, String> = emptyMap()
    private var lastObservedDate: LocalDate = LocalDate.now()
    private var lastForegroundPkg: String? = null
    private var lastBlockLaunchAtMillis: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (monitorJob?.isActive != true) {
            startMonitoringLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        monitorJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        EnziiNotificationHelper.ensureChannels(this)
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            3001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, EnziiNotificationHelper.CHANNEL_FOCUS_LOCK)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Enzii Focus Lock đang hoạt động")
            .setContentText("Hoàn thành mục tiêu học hôm nay để mở khóa các ứng dụng đã chọn.")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                EnziiNotificationHelper.NOTIF_ID_FOCUS_LOCK,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(EnziiNotificationHelper.NOTIF_ID_FOCUS_LOCK, notification)
        }
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        serviceScope.launch {
            focusLockRepository.observeLockedApps().collect { list ->
                lockedLabelsByPkg = list.associate { it.packageName to it.appLabel }
            }
        }

        monitorJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val today = LocalDate.now()
                if (today != lastObservedDate) {
                    lastObservedDate = today
                    focusLockRepository.resetEmergencyIfNewDay(today)
                }

                val eval = focusLockRepository.getCurrentEvaluation(nowMillis = now, today = today)
                val shouldStayAlive = eval.lockedPackages.isNotEmpty() &&
                    eval.status != com.enziidict.app.core.database.FocusLockStatus.DISABLED
                if (!shouldStayAlive) {
                    stopSelf()
                    break
                }

                if (FocusLockPermissions.hasUsageStatsPermission(this@AppBlockerService)) {
                    val fgPkg = queryCurrentForegroundPackage(now)
                    if (!fgPkg.isNullOrBlank()) {
                        lastForegroundPkg = fgPkg
                    }

                    val activePkg = lastForegroundPkg
                    if (activePkg != null &&
                        FocusLockPolicyEvaluator.shouldBlockPackage(activePkg, eval, packageName)
                    ) {
                        if (now - lastBlockLaunchAtMillis >= BLOCK_COOLDOWN_MS) {
                            lastBlockLaunchAtMillis = now
                            launchBlockedScreen(
                                blockedPackage = activePkg,
                                blockedLabel = lockedLabelsByPkg[activePkg] ?: activePkg
                            )
                        }
                    }
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun queryCurrentForegroundPackage(nowMillis: Long): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val events = runCatching { usm.queryEvents(nowMillis - 5_000L, nowMillis) }.getOrNull()
            ?: return null
        val event = UsageEvents.Event()
        var latestPkg: String? = null
        var latestTs = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (isForegroundEvent && event.timeStamp >= latestTs) {
                latestTs = event.timeStamp
                latestPkg = event.packageName
            }
        }
        return latestPkg
    }

    private fun launchBlockedScreen(blockedPackage: String, blockedLabel: String) {
        val intent = Intent(this, BlockedAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockedAppActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(BlockedAppActivity.EXTRA_BLOCKED_LABEL, blockedLabel)
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1_000L
        private const val BLOCK_COOLDOWN_MS = 1_500L

        fun syncServiceState(context: Context, shouldRun: Boolean) {
            val intent = Intent(context, AppBlockerService::class.java)
            if (shouldRun && FocusLockPermissions.hasUsageStatsPermission(context)) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}
