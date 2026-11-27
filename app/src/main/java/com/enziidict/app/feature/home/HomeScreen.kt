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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Hearing
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.audio.TtsAccent
import com.enziidict.app.core.model.SpeakingTopic
import com.enziidict.app.ui.components.BentoCard
import com.enziidict.app.ui.components.CefrPill
import com.enziidict.app.ui.components.EnziiLoadingCard
import com.enziidict.app.ui.components.IpaAudioPill
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

        // BENTO ROW 1: Streak Card + SRS Due Today Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Streak Bento Tile
            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                cornerRadius = 22.dp,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(listOf(StreakAmber, StreakCoral))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Streak",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${state.streakDays} ngày",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = if (state.studiedToday) {
                            "Tuyệt vời! Đã giữ chuỗi hôm nay"
                        } else {
                            "Ôn 1 thẻ để thắp chuỗi hôm nay"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

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

                    Spacer(modifier = Modifier.height(12.dp))

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
