package com.koma.kt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkScheme = darkColorScheme(
    primary = AppColors.accent,
    onPrimary = Color.Black,
    secondary = AppColors.accent,
    background = AppColors.background,
    surface = AppColors.surface,
    onBackground = AppColors.textPrimary,
    onSurface = AppColors.textPrimary,
    error = AppColors.danger,
    outline = AppColors.divider,
)

private val LightScheme = lightColorScheme(
    primary = AppColors.accent,
    onPrimary = Color.Black,
    secondary = AppColors.accentMuted,
    background = AppColors.lightBackground,
    surface = AppColors.lightSurface,
    surfaceVariant = AppColors.lightCard,
    onBackground = AppColors.lightText,
    onSurface = AppColors.lightText,
    onSurfaceVariant = AppColors.lightMuted,
    error = AppColors.danger,
    outline = AppColors.lightDivider,
)

private val KomaTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = AppColors.textSecondary),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun KomaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = KomaTypography,
        content = content,
    )
}
