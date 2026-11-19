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
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = MintContainerLight,
    onPrimaryContainer = MintOnContainerLight,
    secondary = AmberCheckOut,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = AmberOnContainerLight,
    tertiary = EmeraldDeep,
    onTertiary = Color.White,
    background = SurfaceAlabaster,
    onBackground = InkPrimary,
    surface = SurfaceWhite,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = InkSecondary,
    outline = OutlineLight,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = CrimsonOnContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = MintGlow,
    onPrimary = EmeraldDeep,
    primaryContainer = MintContainerDark,
    onPrimaryContainer = MintContainerLight,
    secondary = AmberGlow,
    onSecondary = Color(0xFF2E1600),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberContainerLight,
    tertiary = EmeraldVibrant,
    onTertiary = EmeraldDeep,
    background = SurfaceObsidian,
    onBackground = InkOnDark,
    surface = SurfaceDarkCard,
    onSurface = InkOnDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = InkMutedOnDark,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = CrimsonContainerDark,
    onErrorContainer = CrimsonContainerLight
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AbsensiKuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
