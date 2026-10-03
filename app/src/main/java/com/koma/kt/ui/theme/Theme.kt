package com.koma.kt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkScheme = darkColorScheme(
    primary = DarkKomaColors.accent,
    onPrimary = Color.Black,
    secondary = DarkKomaColors.accent,
    background = DarkKomaColors.background,
    surface = DarkKomaColors.surface,
    onBackground = DarkKomaColors.textPrimary,
    onSurface = DarkKomaColors.textPrimary,
    error = DarkKomaColors.danger,
    outline = DarkKomaColors.divider,
)

private val LightScheme = lightColorScheme(
    primary = LightKomaColors.accent,
    onPrimary = Color.Black,
    secondary = LightKomaColors.accentMuted,
    background = LightKomaColors.background,
    surface = LightKomaColors.surface,
    surfaceVariant = LightKomaColors.card,
    onBackground = LightKomaColors.textPrimary,
    onSurface = LightKomaColors.textPrimary,
    onSurfaceVariant = LightKomaColors.textSecondary,
    error = LightKomaColors.danger,
    outline = LightKomaColors.divider,
)

private val KomaTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun KomaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkKomaColors else LightKomaColors
    CompositionLocalProvider(LocalKomaColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = KomaTypography,
            content = content,
        )
    }
}
