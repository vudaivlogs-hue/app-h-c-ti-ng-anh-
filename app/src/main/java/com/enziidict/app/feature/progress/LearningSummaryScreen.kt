package com.enziidict.app.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.data.local.dao.SavedWordWithEntry
import com.enziidict.app.ui.components.BentoCard
import com.enziidict.app.ui.components.DuiMascotView
import com.enziidict.app.ui.components.MascotLevel
import com.enziidict.app.ui.components.MascotMood
import com.enziidict.app.ui.components.MascotState
import com.enziidict.app.ui.components.PosPill
import com.enziidict.app.ui.components.rememberEnziiHaptics
import com.enziidict.app.ui.theme.StreakAmber
import com.enziidict.app.ui.theme.StreakCoral

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningSummaryScreen(
    onBack: () -> Unit,
    viewModel: LearningSummaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberEnziiHaptics()
    val mascotLevel = MascotLevel.fromMasteredCount(state.masteredCount)
    val mascotMood = when {
        state.activeDaysInPeriod == 0 -> MascotMood.SLEEPY
        mascotLevel == MascotLevel.ADVANCED -> MascotMood.SMART
        else -> MascotMood.HAPPY
    }
    val mascotSummaryBubble = when (mascotMood) {
        MascotMood.SLEEPY -> "Chú Dúi hơi buồn ngủ rồi... Làm 1 lượt ôn SRS hoặc đọc bài song ngữ để thắp lại chuỗi nhé!"
        MascotMood.SMART -> "Đẳng cấp học giả! Bạn đã thành thạo ${state.masteredCount} từ và tích lũy ${state.totalXpInPeriod} XP."
        else -> "Tuyệt vời! Bạn đã học ${state.activeDaysInPeriod} ngày với điểm mạnh ${state.topStrength.skillNameVi}."
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tổng kết học tập",
                        fontWeight = FontWeight.ExtraBold
                    )
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DuiMascotView(
                state = MascotState(mood = mascotMood, level = mascotLevel),
                size = 72.dp,
                messageBubble = mascotSummaryBubble,
                modifier = Modifier.fillMaxWidth()
            )

            // Period switcher (Week / Month)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = {
                            haptics.lightTap()
                            viewModel.selectPeriod(period)
                        },
                        label = { Text(period.labelVi) }
                    )
                }
            }

            // Row 1 KPI: Streak + Mastered Words
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryKpiTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = "Chuỗi Streak",
                    value = "${state.currentStreak} ngày",
                    subtitle = "Kỷ lục ${state.longestStreak} ngày • ❄️ ${state.freezeCount} Freeze",
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = StreakCoral,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
                SummaryKpiTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = "Đã thành thạo",
                    value = "${state.masteredCount} từ",
                    subtitle = "Tổng ${state.totalSavedCount} từ trong Sổ tay",
                    icon = Icons.Default.MilitaryTech,
                    iconTint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            }

            // Row 2 KPI: XP & Active Days + Top Strength
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryKpiTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = "Điểm XP (${if (state.period == SummaryPeriod.WEEK) "7 ngày" else "30 ngày"})",
                    value = "${state.totalXpInPeriod} XP",
                    subtitle = "${state.activeDaysInPeriod} ngày học • ${state.goalsCompletedInPeriod} ngày đạt mục tiêu",
                    icon = Icons.Default.Bolt,
                    iconTint = StreakAmber,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
                SummaryKpiTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = "Điểm mạnh nhất",
                    value = state.topStrength.skillNameVi,
                    subtitle = state.topStrength.detailVi,
                    icon = Icons.Default.EmojiEvents,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Weekly 7-day strip card
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
                        Text(
                            text = "Lịch tuần này",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFE0F2FE)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.AcUnit,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${state.freezeCount} Streak Freeze",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0369A1)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        state.weekStrip.forEach { day ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val bg = when {
                                    day.isCompleted -> Brush.linearGradient(listOf(StreakAmber, StreakCoral))
                                    day.isToday -> Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        )
                                    )
                                    else -> Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (day.isCompleted) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            text = day.date.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (day.isFuture) {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                                Text(
                                    text = day.labelVi,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (day.isToday) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Bar Chart Card (Words & Practice Minutes)
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Biểu đồ theo ngày",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.totalWordsInPeriod} từ • ${state.totalMinutesInPeriod} phút luyện",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryChartMetric.entries.forEach { metric ->
                            FilterChip(
                                selected = state.metric == metric,
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.selectMetric(metric)
                                },
                                label = { Text(metric.labelVi) }
                            )
                        }
                    }

                    DailyActivityBarChart(
                        bars = state.chartBars,
                        metric = state.metric
                    )
                }
            }

            // Notable vocabulary studied this week
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Từ vựng tiêu biểu tuần này",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.weeklyHighlightWords.isEmpty()) {
                        Text(
                            text = "Tuần này chưa có từ nào được lưu hoặc ôn lại. Hãy tra từ hoặc mở Sổ tay Flashcard để bắt đầu!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.weeklyHighlightWords.forEach { item ->
                            WeeklyHighlightWordRow(
                                item = item,
                                onSpeak = {
                                    haptics.lightTap()
                                    viewModel.speakWord(item.entry.word)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SummaryKpiTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        containerColor = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DailyActivityBarChart(
    bars: List<DailyChartBarUi>,
    metric: SummaryChartMetric
) {
    if (bars.isEmpty()) return

    val values = bars.map { bar ->
        if (metric == SummaryChartMetric.WORDS) bar.wordsCount else bar.practiceMinutes
    }
    val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val isMonthMode = bars.size > 7

    val chartModifier = if (isMonthMode) {
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = chartModifier
            .height(170.dp)
            .padding(top = 6.dp),
        horizontalArrangement = if (isMonthMode) Arrangement.spacedBy(8.dp) else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEachIndexed { idx, bar ->
            val value = values[idx]
            val ratio = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
            val barColor = when {
                value == 0 -> MaterialTheme.colorScheme.surfaceVariant
                bar.isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
            }

            Column(
                modifier = if (isMonthMode) Modifier.width(26.dp) else Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (value > 0) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (bar.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(104.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val barHeight = if (value == 0) 6.dp else (14.dp + (90.dp * ratio))
                    Box(
                        modifier = Modifier
                            .width(if (isMonthMode) 16.dp else 22.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(barColor)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isMonthMode && idx % 5 != 0 && !bar.isToday) "" else bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (bar.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WeeklyHighlightWordRow(
    item: SavedWordWithEntry,
    onSpeak: () -> Unit
) {
    val isMastered = item.savedWord.repetitions >= 3 && item.savedWord.intervalDays >= 7
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.entry.word,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    PosPill(item.entry.pos)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isMastered) Color(0xFFD5F2DC) else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isMastered) "Thành thạo" else "Đang ôn",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMastered) Color(0xFF0A4A23) else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if (item.entry.ipaUs.isNotBlank()) {
                    Text(
                        text = item.entry.ipaUs,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = item.entry.meaningVi,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onSpeak) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Phát âm")
            }
        }
    }
}
