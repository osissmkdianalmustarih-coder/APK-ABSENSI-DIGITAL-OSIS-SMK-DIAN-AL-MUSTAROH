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

private val DarkColorScheme = darkColorScheme(
    primary = SkyBlueAccent,
    onPrimary = Color.White,
    primaryContainer = BrightBlueContainer,
    onPrimaryContainer = RoyalBlueLight,
    secondary = AccentGold,
    onSecondary = TextPrimaryLight,
    secondaryContainer = Color(0xFF422006),
    onSecondaryContainer = AccentGoldContainer,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SubtleDark,
    onSurfaceVariant = TextMutedDark,
    outline = BorderDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = DangerContainerLight
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = RoyalBlueLight,
    onPrimaryContainer = DeepNavy,
    secondary = AccentGold,
    onSecondary = TextPrimaryLight,
    secondaryContainer = AccentGoldContainer,
    onSecondaryContainer = AccentGoldText,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    surface = CardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SubtleLight,
    onSurfaceVariant = TextMutedLight,
    outline = BorderLight,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AbsensiOsisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
