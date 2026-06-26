package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = DarkPrimary,
  onPrimary = Color(0xFF001F0D),
  secondary = DarkSecondary,
  onSecondary = Color(0xFF001F0D),
  tertiary = DarkTertiary,
  onTertiary = Color(0xFF001F0D),
  background = DarkBackground,
  onBackground = TextPrimaryDark,
  surface = DarkSurface,
  onSurface = TextPrimaryDark,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = TextSecondaryDark,
  error = ErrorColor
)

private val LightColorScheme = lightColorScheme(
  primary = ForestGreenPrimary,
  onPrimary = Color.White,
  secondary = ForestGreenSecondary,
  onSecondary = Color.White,
  tertiary = ForestGreenTertiary,
  onTertiary = Color.White,
  background = Color(0xFFF9FBF9),
  onBackground = Color(0xFF191C19),
  surface = Color.White,
  onSurface = Color(0xFF191C19),
  surfaceVariant = Color(0xFFE1E5E0),
  onSurfaceVariant = Color(0xFF424942),
  error = Color(0xFFBA1A1A)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to Dark Theme for premium AgroScan feel
  dynamicColor: Boolean = false, // Enforce our curated corporate agricultural color palette
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
