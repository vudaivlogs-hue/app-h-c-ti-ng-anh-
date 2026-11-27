package com.enziidict.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = OnTealPrimaryLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = IndigoSecondaryLight,
    onSecondary = OnIndigoSecondaryLight,
    secondaryContainer = IndigoContainerLight,
    onSecondaryContainer = OnIndigoContainerLight,
    tertiary = CoralTertiaryLight,
    onTertiary = OnCoralTertiaryLight,
    tertiaryContainer = CoralContainerLight,
    onTertiaryContainer = OnCoralContainerLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = OnTealPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = IndigoSecondaryDark,
    onSecondary = OnIndigoSecondaryDark,
    secondaryContainer = IndigoContainerDark,
    onSecondaryContainer = OnIndigoContainerDark,
    tertiary = CoralTertiaryDark,
    onTertiary = OnCoralTertiaryDark,
    tertiaryContainer = CoralContainerDark,
    onTertiaryContainer = OnCoralContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

val EnziiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun EnziiDictTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EnziiTypography,
        shapes = EnziiShapes,
        content = content
    )
}
