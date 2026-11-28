package com.enziidict.app.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enziidict.app.feature.speaking.SpeakingSessionRecapUi
import com.enziidict.app.feature.srs.SrsSessionRecapUi
import com.enziidict.app.ui.components.DuiMascotView
import com.enziidict.app.ui.components.MascotMood
import com.enziidict.app.ui.components.MascotState
import com.enziidict.app.ui.theme.StreakAmber
import com.enziidict.app.ui.theme.StreakCoral

@Composable
fun FlashcardRecapCard(
    recap: SrsSessionRecapUi?,
    reviewedCountFallback: Int,
    onOpenLearningSummary: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reviewed = recap?.reviewedCount ?: reviewedCountFallback
    val accuracy = recap?.accuracyPercent ?: 100
    val xp = recap?.xpEarned ?: (reviewed * 10)
    val streakAfter = recap?.streakAfter ?: 1
    val streakIncreased = recap?.streakIncreased ?: false
    val freezeUsed = recap?.freezeUsedCount ?: 0
    val goalCompleted = recap?.goalJustCompleted ?: false

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DuiMascotView(
                state = MascotState(mood = MascotMood.HAPPY),
                size = 86.dp
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Hoàn thành phiên ôn tập!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Lịch ôn SM-2 đã được cập nhật cho tất cả thẻ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecapStatPill(
                    label = "Số thẻ",
                    value = "$reviewed",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                RecapStatPill(
                    label = "Chính xác",
                    value = "$accuracy%",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                RecapStatPill(
                    label = "Điểm XP",
                    value = "+$xp",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }

            StreakFeedbackBanner(
                streakAfter = streakAfter,
                streakIncreased = streakIncreased,
                freezeUsedCount = freezeUsed,
                goalJustCompleted = goalCompleted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenLearningSummary,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tổng kết")
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Quay lại Sổ tay")
                }
            }
        }
    }
}

@Composable
fun SpeakingRecapDialog(
    recap: SpeakingSessionRecapUi,
    onViewAiReview: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            DuiMascotView(
                state = MascotState(mood = MascotMood.HAPPY),
                size = 76.dp
            )
        },
        title = {
            Text(
                text = "Hoàn thành buổi nói AI!",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RecapStatPill(
                        label = "Thời lượng",
                        value = if (recap.durationSec >= 60) "${recap.speakingMinutes} phút" else "${recap.durationSec}s",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    RecapStatPill(
                        label = "Lượt nói",
                        value = "${recap.userTurns}",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    RecapStatPill(
                        label = "Điểm XP",
                        value = "+${recap.xpEarned}",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }

                StreakFeedbackBanner(
                    streakAfter = recap.streakAfter,
                    streakIncreased = recap.streakIncreased,
                    freezeUsedCount = recap.freezeUsedCount,
                    goalJustCompleted = recap.goalJustCompleted
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onViewAiReview) {
                Text("Xem nhận xét AI")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
private fun RecapStatPill(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun StreakFeedbackBanner(
    streakAfter: Int,
    streakIncreased: Boolean,
    freezeUsedCount: Int,
    goalJustCompleted: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = StreakCoral,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = when {
                        streakIncreased -> "Chuỗi Streak tăng lên $streakAfter ngày!"
                        streakAfter > 0 -> "Đang giữ vững chuỗi $streakAfter ngày liên tiếp"
                        else -> "Đã ghi nhận hoạt động hôm nay"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (freezeUsedCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AcUnit,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Đã dùng $freezeUsedCount Streak Freeze để bảo vệ chuỗi của bạn!",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF0369A1)
                    )
                }
            }
            if (goalJustCompleted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = StreakAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Tuyệt vời! Bạn vừa đạt mục tiêu XP hằng ngày.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
