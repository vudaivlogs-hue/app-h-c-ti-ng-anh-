package com.enziidict.app.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.database.DailyQuestType
import com.enziidict.app.core.model.SpeakingTopic
import com.enziidict.app.ui.components.BentoCard
import com.enziidict.app.ui.components.CefrPill
import com.enziidict.app.ui.components.DuiMascotView
import com.enziidict.app.ui.components.EnziiLoadingCard
import com.enziidict.app.ui.components.IpaAudioPill
import com.enziidict.app.ui.components.MascotMood
import com.enziidict.app.ui.components.MascotState
import com.enziidict.app.ui.components.PosPill
import com.enziidict.app.ui.components.rememberEnziiHaptics
import com.enziidict.app.ui.theme.StreakAmber
import com.enziidict.app.ui.theme.StreakCoral

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenDictionary: () -> Unit,
    onOpenSrs: () -> Unit,
    onOpenSpeakingWithTopic: (SpeakingTopic) -> Unit,
    onOpenTranslate: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenShadowing: () -> Unit = {},
    onOpenLearningSummary: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberEnziiHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Greeting + Instant Search Pill
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Xin chào, học thôi nào!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Tra từ offline siêu tốc, ôn SRS và luyện nói AI mỗi ngày.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BentoCard(
            onClick = {
                haptics.lightTap()
                onOpenDictionary()
            },
            cornerRadius = 24.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Tra từ Anh – Việt, IPA, collocations...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.showMascotTip) {
            DuiMascotView(
                state = MascotState(mood = MascotMood.INQUISITIVE, level = state.mascotLevel),
                size = 58.dp,
                messageBubble = "Mình là chú Dúi! Thử tra 1 từ mới hoặc ôn nhanh vài thẻ SRS để thắp chuỗi Streak hôm nay nhé.",
                actionLabel = if (state.dueCardsCount > 0) "Ôn ${state.dueCardsCount} thẻ ngay" else "Tra từ ngay",
                onActionClick = {
                    viewModel.dismissMascotTip()
                    if (state.dueCardsCount > 0) onOpenSrs() else onOpenDictionary()
                },
                onDismissBubble = viewModel::dismissMascotTip,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // BENTO STREAK HERO CARD: Streak + 7-Day Calendar + Streak Freeze + Dui Mascot
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            onClick = {
                haptics.lightTap()
                onOpenLearningSummary()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DuiMascotView(
                            state = MascotState(
                                mood = if (state.studiedToday) MascotMood.HAPPY else MascotMood.SLEEPY,
                                level = state.mascotLevel
                            ),
                            size = 66.dp,
                            onClick = {
                                haptics.lightTap()
                                onOpenLearningSummary()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${state.streakDays} ngày Streak",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = if (state.studiedToday) {
                                    "Đã thắp chuỗi hôm nay • ${state.xpToday}/${state.dailyXpGoal} XP"
                                } else {
                                    "Chú Dúi đang đợi bạn • ${state.xpToday}/${state.dailyXpGoal} XP"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFE0F2FE)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.AcUnit,
                                contentDescription = "Streak Freeze",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${state.freezeCount} Freeze",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1)
                            )
                        }
                    }
                }

                // Weekly 7-day calendar strip (T2..CN)
                if (state.weekStrip.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        state.weekStrip.forEach { day ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                day.isCompleted -> Brush.linearGradient(listOf(StreakAmber, StreakCoral))
                                                day.isToday -> Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.surface,
                                                        MaterialTheme.colorScheme.surface
                                                    )
                                                )
                                                else -> Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                                                    )
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (day.isCompleted) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text(
                                            text = if (day.isToday) "•" else "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = StreakCoral
                                        )
                                    }
                                }
                                Text(
                                    text = day.labelVi,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                        alpha = if (day.isFuture) 0.5f else 0.9f
                                    )
                                )
                            }
                        }
                    }
                }

                val dailyGoalProgress = (state.xpToday.toFloat() / state.dailyXpGoal.coerceAtLeast(1).toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { dailyGoalProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = StreakCoral,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                )
            }
        }

        // BENTO DAILY QUESTS CARD (3 quests)
        val completedQuests = state.dailyQuests.count { it.isCompleted }
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            containerColor = MaterialTheme.colorScheme.surface
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
                        text = "Nhiệm vụ hằng ngày",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (completedQuests == state.dailyQuests.size) {
                            Color(0xFFD5F2DC)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = "$completedQuests / ${state.dailyQuests.size} hoàn thành",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (completedQuests == state.dailyQuests.size) {
                                Color(0xFF0A4A23)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }

                state.dailyQuests.forEach { quest ->
                    val questIcon = when (quest.type) {
                        DailyQuestType.SRS_REVIEW -> Icons.Default.Style
                        DailyQuestType.SHADOWING -> Icons.Default.Hearing
                        DailyQuestType.SPEAKING -> Icons.Default.Mic
                    }
                    Surface(
                        onClick = {
                            haptics.lightTap()
                            when (quest.type) {
                                DailyQuestType.SRS_REVIEW -> onOpenSrs()
                                DailyQuestType.SHADOWING -> onOpenShadowing()
                                DailyQuestType.SPEAKING -> onOpenSpeakingWithTopic(SpeakingTopic.DAILY)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (quest.isCompleted) Icons.Default.CheckCircle else questIcon,
                                        contentDescription = null,
                                        tint = if (quest.isCompleted) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = quest.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${quest.current.coerceAtMost(quest.target)}/${quest.target} • +${quest.rewardXp} XP",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (quest.isCompleted) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { quest.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = if (quest.isCompleted) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }
        }

        // BENTO ROW 1: SRS Due Today Card + Learning Summary Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SRS Due Today Bento Tile
            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                cornerRadius = 22.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = {
                    haptics.lightTap()
                    onOpenSrs()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Style,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SRS hôm nay",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "${state.dueCardsCount}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "thẻ đến hạn • Tổng ${state.totalSavedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            // Learning Summary Tile
            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                cornerRadius = 22.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                onClick = {
                    haptics.lightTap()
                    onOpenLearningSummary()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tổng kết",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "${state.xpToday} XP",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Biểu đồ tuần/tháng & Từ thành thạo →",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // BENTO HERO CARD: AI Speaking Shortcuts by Topic
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Nói với AI ngay",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Chọn chủ đề để mở phòng gọi thoại trực tiếp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
                            )
                        }
                    }
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        SpeakingTopic.DAILY,
                        SpeakingTopic.CAFE,
                        SpeakingTopic.INTERVIEW,
                        SpeakingTopic.TRAVEL
                    ).forEach { topic ->
                        Surface(
                            onClick = {
                                haptics.lightTap()
                                onOpenSpeakingWithTopic(topic)
                            },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = topic.titleVi,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // BENTO CARD: Word of the Day
        if (state.isLoadingWord) {
            EnziiLoadingCard(message = "Đang chuẩn bị từ vựng nổi bật hôm nay...")
        } else {
            state.wordOfTheDay?.let { word ->
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TỪ VỰNG NỔI BẬT HÔM NAY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.showNextFeaturedWord()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Đổi từ khác",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = word.word,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PosPill(word.pos)
                                    word.cefrLevel?.let { CefrPill(it) }
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    haptics.confirm()
                                    viewModel.saveWordOfTheDay(word)
                                }
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lưu SRS")
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IpaAudioPill(
                                accentLabel = "UK",
                                ipa = word.ipaUk,
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.speak(word.word, TtsAccent.UK)
                                }
                            )
                            IpaAudioPill(
                                accentLabel = "US",
                                ipa = word.ipaUs,
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.speak(word.word, TtsAccent.US)
                                }
                            )
                        }

                        Text(
                            text = word.meaningVi,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        word.examples.firstOrNull()?.let { ex ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "“${ex.en}”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (ex.vi.isNotBlank()) {
                                    Text(
                                        text = ex.vi,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = state.statusMessage != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut()
                        ) {
                            state.statusMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // BENTO ROW 2: Reading & Hybrid Translate Tiles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                cornerRadius = 22.dp,
                onClick = {
                    haptics.lightTap()
                    onOpenReading()
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Đọc song ngữ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.articlesCount} bài A2–C1 • Chạm từ tra nhanh",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                cornerRadius = 22.dp,
                onClick = {
                    haptics.lightTap()
                    onOpenTranslate()
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Dịch & Ngữ pháp",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ML Kit Offline + Gemini phân tích sâu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // BENTO SHADOWING SHORTCUT CARD
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            onClick = {
                haptics.lightTap()
                onOpenShadowing()
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Hearing,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Luyện Shadowing theo cụm từ (Chunks)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Nghe mẫu 0.75x–1.15x • Thu âm đối chiếu Giọng mẫu ↔ Giọng của bạn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
