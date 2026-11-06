package com.dialer.truedialer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TrueBlue,
    secondary = EmeraldGreen,
    tertiary = AmberOrange,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = SurfaceLight,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = TrueBlue,
    secondary = EmeraldGreen,
    tertiary = AmberOrange,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = SurfaceLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight
)

@Composable
fun TrueEnquiryDialerTheme(
    darkTheme: Boolean = when (com.dialer.truedialer.AppConfig.THEME_MODE) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
