package com.lume.app.ui.theme

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
    primary = DarkLumeColors.accent,
    onPrimary = Color.Black,
    secondary = DarkLumeColors.accent,
    background = DarkLumeColors.background,
    surface = DarkLumeColors.surface,
    onBackground = DarkLumeColors.textPrimary,
    onSurface = DarkLumeColors.textPrimary,
    error = DarkLumeColors.danger,
    outline = DarkLumeColors.divider,
)

private val LightScheme = lightColorScheme(
    primary = LightLumeColors.accent,
    onPrimary = Color.Black,
    secondary = LightLumeColors.accentMuted,
    background = LightLumeColors.background,
    surface = LightLumeColors.surface,
    surfaceVariant = LightLumeColors.card,
    onBackground = LightLumeColors.textPrimary,
    onSurface = LightLumeColors.textPrimary,
    onSurfaceVariant = LightLumeColors.textSecondary,
    error = LightLumeColors.danger,
    outline = LightLumeColors.divider,
)

private val LumeTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun LumeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkLumeColors else LightLumeColors
    CompositionLocalProvider(LocalLumeColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = LumeTypography,
            content = content,
        )
    }
}
