package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = CoralSecondary,
    onSecondary = Color.White,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = OnCoralContainer,
    tertiary = SlateBlueTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SlateBlueContainer,
    onTertiaryContainer = OnSlateBlueContainer,
    background = IvoryBackground,
    onBackground = CharcoalText,
    surface = WarmSurface,
    onSurface = CharcoalText,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    surfaceContainerLowest = WarmSurface,
    surfaceContainerLow = IvoryBackground,
    surfaceContainer = WarmSurfaceVariant,
    surfaceContainerHigh = WarmSurfaceContainer,
    outline = WarmOutline,
    outlineVariant = Color(0xFFE7E2D9),
    error = StatusDanger,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = DarkOnTealPrimary,
    primaryContainer = DarkTealContainer,
    onPrimaryContainer = DarkOnTealContainer,
    secondary = DarkCoralSecondary,
    onSecondary = DarkOnCoralSecondary,
    secondaryContainer = DarkCoralContainer,
    onSecondaryContainer = Color(0xFFFFDBD2),
    tertiary = Color(0xFF8CCDF0),
    onTertiary = Color(0xFF00344A),
    tertiaryContainer = Color(0xFF144B69),
    onTertiaryContainer = Color(0xFFD1E9F7),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextMuted,
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = DarkBackground,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF283431),
    error = Color(0xFFFF8A9B),
    onError = Color(0xFF5E001A)
)

val FonoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FonoVidaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FonoTypography,
        shapes = FonoShapes,
        content = content
    )
}
