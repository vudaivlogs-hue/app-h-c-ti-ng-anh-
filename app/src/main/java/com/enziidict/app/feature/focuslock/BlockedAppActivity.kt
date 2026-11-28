package com.enziidict.app.feature.focuslock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.MainActivity
import com.enziidict.app.core.database.EmergencyUnlockResult
import com.enziidict.app.core.database.FocusLockEvaluation
import com.enziidict.app.core.database.FocusLockRepository
import com.enziidict.app.core.database.FocusLockStatus
import com.enziidict.app.core.notification.EnziiNotificationHelper
import com.enziidict.app.ui.components.BentoCard
import com.enziidict.app.ui.components.DuiMascotView
import com.enziidict.app.ui.components.MascotMood
import com.enziidict.app.ui.components.MascotState
import com.enziidict.app.ui.theme.EnziiDictTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BlockedAppActivity : ComponentActivity() {

    @Inject
    lateinit var focusLockRepository: FocusLockRepository

    private var blockedAppLabelState by mutableStateOf("Ứng dụng giải trí")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readBlockedIntent(intent)

        setContent {
            val config by focusLockRepository.observeConfig()
                .collectAsStateWithLifecycle(initialValue = com.enziidict.app.data.local.entity.FocusLockConfig.default())
            val evaluation by focusLockRepository.observeEvaluation()
                .collectAsStateWithLifecycle(
                    initialValue = FocusLockEvaluation(
                        status = FocusLockStatus.LOCKING,
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
                    )
                )
            val scope = rememberCoroutineScope()
            var emergencyMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(evaluation.status) {
                if (evaluation.status == FocusLockStatus.UNLOCKED_BY_GOAL ||
                    evaluation.status == FocusLockStatus.DISABLED
                ) {
                    finish()
                }
            }

            BackHandler {
                goToDeviceHome()
            }

            EnziiDictTheme {
                BlockedAppScreenContent(
                    blockedAppLabel = blockedAppLabelState,
                    evaluation = evaluation,
                    emergencyDurationMinutes = config.emergencyUnlockDurationMinutes,
                    emergencyErrorMessage = emergencyMessage,
                    onStartStudying = {
                        val studyIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(EnziiNotificationHelper.EXTRA_DEST, EnziiNotificationHelper.DEST_SRS)
                        }
                        startActivity(studyIntent)
                        finish()
                    },
                    onConfirmEmergencyUnlock = {
                        scope.launch {
                            when (val res = focusLockRepository.triggerEmergencyUnlock()) {
                                is EmergencyUnlockResult.Granted -> finish()
                                EmergencyUnlockResult.LimitReached -> {
                                    emergencyMessage = "Bạn đã dùng hết lượt mở khẩn cấp hôm nay!"
                                }
                            }
                        }
                    },
                    onReturnHome = ::goToDeviceHome
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readBlockedIntent(intent)
    }

    private fun readBlockedIntent(intent: Intent?) {
        val label = intent?.getStringExtra(EXTRA_BLOCKED_LABEL)?.trim().orEmpty()
        val pkg = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.trim().orEmpty()
        blockedAppLabelState = label.ifEmpty { pkg.ifEmpty { "Ứng dụng đã khóa" } }
    }

    private fun goToDeviceHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(homeIntent) }
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "enzii_blocked_package"
        const val EXTRA_BLOCKED_LABEL = "enzii_blocked_label"
    }
}

@Composable
private fun BlockedAppScreenContent(
    blockedAppLabel: String,
    evaluation: FocusLockEvaluation,
    emergencyDurationMinutes: Int,
    emergencyErrorMessage: String?,
    onStartStudying: () -> Unit,
    onConfirmEmergencyUnlock: () -> Unit,
    onReturnHome: () -> Unit
) {
    var showEmergencyConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DuiMascotView(
                state = MascotState(mood = MascotMood.FOCUSED),
                size = 104.dp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "$blockedAppLabel đang tạm khóa",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Hoàn thành mục tiêu hôm nay để mở khóa!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (evaluation.requireBoth) {
                    "Điều kiện: đạt CẢ thời gian học VÀ số kiến thức mục tiêu."
                } else {
                    "Điều kiện: đạt 1 trong 2 mục tiêu thời gian hoặc kiến thức."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (evaluation.requiredStudyMinutes > 0) {
                        val minProgress = (evaluation.studiedMinutesToday.toFloat() /
                            evaluation.requiredStudyMinutes.toFloat()).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Thời gian học hôm nay",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${evaluation.studiedMinutesToday} / ${evaluation.requiredStudyMinutes} phút",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { minProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }

                    if (evaluation.requiredKnowledgeCount > 0) {
                        val knowProgress = (evaluation.knowledgeCountToday.toFloat() /
                            evaluation.requiredKnowledgeCount.toFloat()).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Kiến thức (thẻ / Shadowing / từ)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${evaluation.knowledgeCountToday} / ${evaluation.requiredKnowledgeCount}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { knowProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            emergencyErrorMessage?.let { err ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onStartStudying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.School, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Học ngay để mở khóa",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val canUseEmergency = evaluation.emergencyRemainingUsesToday > 0
            OutlinedButton(
                onClick = { showEmergencyConfirmDialog = true },
                enabled = canUseEmergency,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (canUseEmergency) {
                        "Mở khẩn cấp ${emergencyDurationMinutes}p (Còn ${evaluation.emergencyRemainingUsesToday}/${evaluation.maxEmergencyPerDay} lượt)"
                    } else {
                        "Đã hết lượt mở khẩn cấp hôm nay (0/${evaluation.maxEmergencyPerDay})"
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(onClick = onReturnHome) {
                Text("Quay về màn hình chính")
            }
        }
    }

    if (showEmergencyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyConfirmDialog = false },
            title = { Text("Xác nhận mở khẩn cấp?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Bạn sẽ dùng 1 lượt mở khẩn cấp để truy cập $blockedAppLabel trong $emergencyDurationMinutes phút.\n" +
                        "Sau khi dùng xong bạn còn ${evaluation.emergencyRemainingUsesToday - 1} lượt trong hôm nay."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyConfirmDialog = false
                        onConfirmEmergencyUnlock()
                    }
                ) {
                    Text("Mở $emergencyDurationMinutes phút")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyConfirmDialog = false }) {
                    Text("Quay lại học")
                }
            }
        )
    }
}
