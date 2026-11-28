package com.enziidict.app.feature.focuslock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.database.FocusLockStatus
import com.enziidict.app.ui.components.BentoCard
import com.enziidict.app.ui.components.DuiMascotView
import com.enziidict.app.ui.components.MascotMood
import com.enziidict.app.ui.components.MascotState
import com.enziidict.app.ui.components.rememberEnziiHaptics
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusLockScreen(
    onBack: () -> Unit,
    viewModel: FocusLockViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberEnziiHaptics()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val statusMood = when (state.evaluation.status) {
        FocusLockStatus.UNLOCKED_BY_GOAL -> MascotMood.HAPPY
        FocusLockStatus.LOCKING -> MascotMood.FOCUSED
        FocusLockStatus.EMERGENCY_UNLOCKED -> MascotMood.INQUISITIVE
        FocusLockStatus.DISABLED -> MascotMood.SLEEPY
    }
    val statusTitle = when (state.evaluation.status) {
        FocusLockStatus.UNLOCKED_BY_GOAL -> "Đã mở khóa tự động"
        FocusLockStatus.LOCKING -> "Đang khóa (${state.lockedCount} ứng dụng)"
        FocusLockStatus.EMERGENCY_UNLOCKED -> {
            val mins = state.evaluation.emergencyRemainingSeconds / 60L
            val secs = state.evaluation.emergencyRemainingSeconds % 60L
            String.format(Locale.US, "Đang mở khẩn cấp (%02d:%02d)", mins, secs)
        }
        FocusLockStatus.DISABLED -> "Chế độ khóa đang tắt"
    }
    val statusSubtitle = when (state.evaluation.status) {
        FocusLockStatus.UNLOCKED_BY_GOAL -> "Bạn đã đạt mục tiêu hôm nay! Toàn bộ ứng dụng đã mở khóa."
        FocusLockStatus.LOCKING -> "Hoàn thành ${state.evaluation.requiredStudyMinutes}p hoặc ${state.evaluation.requiredKnowledgeCount} kiến thức để mở."
        FocusLockStatus.EMERGENCY_UNLOCKED -> "Còn ${state.evaluation.emergencyRemainingUsesToday}/${state.evaluation.maxEmergencyPerDay} lượt khẩn cấp hôm nay."
        FocusLockStatus.DISABLED -> "Bật công tắc bên phải để rèn thói quen học trước khi giải trí."
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Focus App Lock", fontWeight = FontWeight.ExtraBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Live Status + Master Switch Card
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = when (state.evaluation.status) {
                    FocusLockStatus.UNLOCKED_BY_GOAL -> Color(0xFFD5F2DC)
                    FocusLockStatus.LOCKING -> MaterialTheme.colorScheme.primaryContainer
                    FocusLockStatus.EMERGENCY_UNLOCKED -> MaterialTheme.colorScheme.tertiaryContainer
                    FocusLockStatus.DISABLED -> MaterialTheme.colorScheme.surface
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DuiMascotView(
                                state = MascotState(mood = statusMood),
                                size = 60.dp
                            )
                            Column {
                                Text(
                                    text = statusTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = statusSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = state.config.isEnabled,
                            onCheckedChange = {
                                haptics.confirm()
                                viewModel.setEnabled(it)
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Text(
                        text = "Tiến độ hôm nay: ${state.evaluation.studiedMinutesToday} phút • " +
                            "${state.evaluation.knowledgeCountToday} kiến thức (thẻ SRS / Shadowing / từ)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 2. Android Special Permissions Check
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Quyền hệ thống cần thiết",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    PermissionStatusRow(
                        title = "Quyền thống kê sử dụng (Usage Access)",
                        subtitle = "Nhận biết khi bạn mở các ứng dụng trong danh sách khóa",
                        granted = state.hasUsagePermission,
                        buttonLabel = "Cấp quyền Usage",
                        onOpenSettings = { FocusLockPermissions.openUsageStatsSettings(context) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    PermissionStatusRow(
                        title = "Quyền hiển thị trên ứng dụng khác",
                        subtitle = "Hiển thị màn hình nhắc học chú Dúi đè lên app bị khóa",
                        granted = state.hasOverlayPermission,
                        buttonLabel = "Cấp quyền Overlay",
                        onOpenSettings = { FocusLockPermissions.openOverlaySettings(context) }
                    )
                }
            }

            // 3. Daily Unlock Goals
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Điều kiện mở khóa trong ngày",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    StepperSettingRow(
                        label = "Thời gian học mỗi ngày (0 = không bắt buộc)",
                        valueText = "${state.config.requiredStudyMinutes} phút",
                        onMinus = {
                            viewModel.updateRequiredMinutes((state.config.requiredStudyMinutes - 5).coerceAtLeast(0))
                        },
                        onPlus = {
                            viewModel.updateRequiredMinutes((state.config.requiredStudyMinutes + 5).coerceAtMost(240))
                        }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 10, 15, 20, 30, 45).forEach { preset ->
                            FilterChip(
                                selected = state.config.requiredStudyMinutes == preset,
                                onClick = { viewModel.updateRequiredMinutes(preset) },
                                label = { Text(if (preset == 0) "Tắt (0p)" else "${preset}p") }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    StepperSettingRow(
                        label = "Số kiến thức hoàn thành (0 = không bắt buộc)",
                        valueText = "${state.config.requiredKnowledgeCount} lượt",
                        onMinus = {
                            viewModel.updateRequiredKnowledge((state.config.requiredKnowledgeCount - 5).coerceAtLeast(0))
                        },
                        onPlus = {
                            viewModel.updateRequiredKnowledge((state.config.requiredKnowledgeCount + 5).coerceAtMost(200))
                        }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 5, 10, 15, 20).forEach { preset ->
                            FilterChip(
                                selected = state.config.requiredKnowledgeCount == preset,
                                onClick = { viewModel.updateRequiredKnowledge(preset) },
                                label = { Text(if (preset == 0) "Tắt (0)" else "$preset") }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Text(
                        text = "Quy tắc kết hợp 2 điều kiện",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.config.requireBoth,
                            onClick = { viewModel.setRequireBoth(false) },
                            label = { Text("Đạt 1 trong 2") }
                        )
                        FilterChip(
                            selected = state.config.requireBoth,
                            onClick = { viewModel.setRequireBoth(true) },
                            label = { Text("Phải đạt CẢ HAI") }
                        )
                    }
                }
            }

            // 4. Emergency Unlock Limits
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Mở khẩn cấp có giới hạn",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Hôm nay đã dùng ${state.evaluation.emergencyUsedToday}/${state.evaluation.maxEmergencyPerDay} lượt (tự đặt lại lúc 00:00).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("Thời lượng mỗi lần mở khẩn cấp", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { mins ->
                            FilterChip(
                                selected = state.config.emergencyUnlockDurationMinutes == mins,
                                onClick = { viewModel.updateEmergencyDuration(mins) },
                                label = { Text("$mins phút") }
                            )
                        }
                    }

                    Text("Số lần tối đa mỗi ngày", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 2, 3).forEach { maxCount ->
                            FilterChip(
                                selected = state.config.maxEmergencyUnlocksPerDay == maxCount,
                                onClick = { viewModel.updateMaxEmergencyPerDay(maxCount) },
                                label = { Text(if (maxCount == 0) "Cấm (0)" else "$maxCount lần") }
                            )
                        }
                    }
                }
            }

            // 5. Select Installed Apps to Lock
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Chọn ứng dụng cần khóa",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${state.lockedCount} đã chọn",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("Tìm ứng dụng theo tên hoặc package...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )

                    if (state.isLoadingApps) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang quét ứng dụng đã cài...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (state.apps.isEmpty()) {
                        Text(
                            text = "Không tìm thấy ứng dụng phù hợp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.apps.forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (app.isLocked) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Switch(
                                    checked = app.isLocked,
                                    onCheckedChange = {
                                        haptics.lightTap()
                                        viewModel.toggleLockedApp(app)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (granted) Color(0xFF16A34A) else Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (granted) {
            Text(
                text = "Đã cấp",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16A34A)
            )
        } else {
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(buttonLabel, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StepperSettingRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus) {
                Icon(Icons.Default.Remove, contentDescription = "Giảm")
            }
            IconButton(onClick = onPlus) {
                Icon(Icons.Default.Add, contentDescription = "Tăng")
            }
        }
    }
}
