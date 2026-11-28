package com.enziidict.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enziidict.app.R
import com.enziidict.app.core.model.SpeakingLevel
import kotlinx.coroutines.delay

enum class MascotMood(
    @DrawableRes val drawableRes: Int,
    val contentDescriptionVi: String
) {
    INQUISITIVE(R.drawable.mascot_dui_inquisitive, "Chú Dúi tò mò tra từ"),
    FOCUSED(R.drawable.mascot_dui_focused, "Chú Dúi tập trung nghe nói"),
    HAPPY(R.drawable.mascot_dui_happy, "Chú Dúi ăn mừng thành tích"),
    SLEEPY(R.drawable.mascot_dui_sleepy, "Chú Dúi nhắc lịch học"),
    SMART(R.drawable.mascot_dui_smart, "Chú Dúi học giả nâng cao")
}

enum class MascotLevel(
    val labelVi: String,
    val cefrBadge: String,
    val accentColor: Color
) {
    BASIC("Cơ bản", "A1–A2", Color(0xFF0D9488)),
    INTERMEDIATE("Trung cấp", "B1–B2", Color(0xFFF59E0B)),
    ADVANCED("Nâng cao", "C1–C2", Color(0xFF7C3AED));

    companion object {
        fun fromSpeakingLevel(level: SpeakingLevel): MascotLevel = when (level) {
            SpeakingLevel.BEGINNER -> BASIC
            SpeakingLevel.INTERMEDIATE -> INTERMEDIATE
            SpeakingLevel.ADVANCED -> ADVANCED
        }

        fun fromMasteredCount(masteredCount: Int): MascotLevel = when {
            masteredCount >= 30 -> ADVANCED
            masteredCount >= 10 -> INTERMEDIATE
            else -> BASIC
        }
    }
}

data class MascotState(
    val mood: MascotMood = MascotMood.INQUISITIVE,
    val level: MascotLevel? = null
)

@Composable
fun DuiMascotView(
    state: MascotState,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    messageBubble: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    onDismissBubble: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mascot_press_scale"
    )

    val borderColor = state.level?.accentColor ?: when (state.mood) {
        MascotMood.HAPPY -> Color(0xFFF59E0B)
        MascotMood.FOCUSED, MascotMood.INQUISITIVE -> Color(0xFF0D9488)
        MascotMood.SLEEPY -> Color(0xFF64748B)
        MascotMood.SMART -> Color(0xFF7C3AED)
    }

    val avatarBox: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .let { base ->
                    if (onClick != null) {
                        base.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                    } else {
                        base
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(elevation = 6.dp, shape = CircleShape, clip = false),
                shape = CircleShape,
                color = Color(0xFFFDF8EE),
                border = BorderStroke(2.dp, borderColor.copy(alpha = 0.85f))
            ) {
                AnimatedContent(
                    targetState = state.mood,
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(
                            initialScale = 0.86f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )).togetherWith(fadeOut(tween(150)))
                    },
                    label = "mascot_mood_transition"
                ) { mood ->
                    Image(
                        painter = painterResource(id = mood.drawableRes),
                        contentDescription = mood.contentDescriptionVi,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }

            state.level?.let { lvl ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 1.dp),
                    shape = RoundedCornerShape(50),
                    color = lvl.accentColor,
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = lvl.cefrBadge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    if (messageBubble.isNullOrBlank()) {
        Box(modifier = modifier) {
            avatarBox()
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            avatarBox()
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, borderColor.copy(alpha = 0.35f)),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = messageBubble,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (onDismissBubble != null) {
                            IconButton(
                                onClick = onDismissBubble,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Đóng gợi ý",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (!actionLabel.isNullOrBlank() && onActionClick != null) {
                        TextButton(
                            onClick = onActionClick,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = borderColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EnziiSplashOverlay(
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splash_logo_scale"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1_150L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F766E),
                        Color(0xFF0D9488),
                        Color(0xFF134E4A)
                    )
                )
            )
            .clickable(onClick = onFinished)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 14.dp,
                border = BorderStroke(2.dp, Color(0xFFF59E0B).copy(alpha = 0.8f))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_enzii_banner),
                    contentDescription = "Logo Enzii Dict",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = "Enzii Dict",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.16f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "Chú Dúi đã sẵn sàng — cùng học tiếng Anh nhé!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFEF3C7),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
