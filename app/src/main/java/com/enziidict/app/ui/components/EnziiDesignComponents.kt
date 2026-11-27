package com.enziidict.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enziidict.app.ui.theme.CefrAdvancedBg
import com.enziidict.app.ui.theme.CefrAdvancedFg
import com.enziidict.app.ui.theme.CefrBeginnerBg
import com.enziidict.app.ui.theme.CefrBeginnerFg
import com.enziidict.app.ui.theme.CefrIntermediateBg
import com.enziidict.app.ui.theme.CefrIntermediateFg

class EnziiHaptics(private val view: View) {
    fun lightTap() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(code)
    }
}

@Composable
fun rememberEnziiHaptics(): EnziiHaptics {
    val view = LocalView.current
    return remember(view) { EnziiHaptics(view) }
}

@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    cornerRadius: Dp = 22.dp,
    border: BorderStroke? = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    ),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bento_press_scale"
    )

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.scale(scale),
            shape = RoundedCornerShape(cornerRadius),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            interactionSource = interactionSource,
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(cornerRadius),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            content = content
        )
    }
}

@Composable
fun PosPill(pos: String, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.primaryContainer
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            text = pos.lowercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

@Composable
fun CefrPill(level: String, modifier: Modifier = Modifier) {
    val normalized = level.trim().uppercase()
    val (bg, fg) = when {
        normalized.startsWith("A") -> CefrBeginnerBg to CefrBeginnerFg
        normalized.startsWith("B") -> CefrIntermediateBg to CefrIntermediateFg
        else -> CefrAdvancedBg to CefrAdvancedFg
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            text = normalized,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
fun IpaAudioPill(
    accentLabel: String,
    ipa: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = accentLabel,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = accentLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (ipa.isNotBlank()) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = ipa,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun EnziiEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun EnziiLoadingCard(
    message: String,
    modifier: Modifier = Modifier
) {
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
