package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AppPrimary,
    onPrimary = Color.White,
    secondary = AppSecondary,
    onSecondary = Color.Black,
    background = AppBackground,
    onBackground = AppTextPrimary,
    surface = AppSurface,
    onSurface = AppTextPrimary,
    onSurfaceVariant = AppTextSecondary,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = AppDarkPrimary,
    onPrimary = Color.Black,
    secondary = AppDarkSecondary,
    onSecondary = Color.White,
    background = AppDarkBackground,
    onBackground = AppDarkTextPrimary,
    surface = AppDarkSurface,
    onSurface = AppDarkTextPrimary,
    onSurfaceVariant = AppDarkTextSecondary,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Vypnuto pro zachování našeho motivu S POHODA Stormware
    currentDepo: String? = null,
    content: @Composable () -> Unit,
) {
    val isDepo2 = currentDepo == "Depo 2"
    val depoPrimaryLight = if (isDepo2) Color(0xFF7C4700) else Color(0xFF1F4E79)
    val depoPrimaryDark = if (isDepo2) Color(0xFFD97706) else Color(0xFF4A90E2)
    val depoBgLight = if (isDepo2) Color(0xFFF3E6D8) else Color(0xFFDCE6F2)
    val depoSurfaceLight = if (isDepo2) Color(0xFFFFFBF7) else Color(0xFFF7FAFC)
    val depoBgDark = if (isDepo2) Color(0xFF231B14) else Color(0xFF101C2C)

    val colorScheme = LightColorScheme.copy(
        primary = depoPrimaryLight,
        background = depoBgLight,
        surface = depoSurfaceLight,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF334155)
    )
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
