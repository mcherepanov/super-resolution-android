package ru.max.superresolution.monitor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SrBlue = Color(0xFF1565C0)
val SrBlueDeep = Color(0xFF0D47A1)
val SrOrange = Color(0xFFE65100)
val SrSurfaceBg = Color(0xFFF5F5F5)
val SrOnSurface = Color(0xFF212121)
val SrOnSurfaceVariant = Color(0xFF616161)
val SrOutlineVariant = Color(0xFFE0E0E0)
val SrPrimaryContainer = Color(0xFFE3F2FD)
val SrSecondaryContainer = Color(0xFFFFF3E0)
val SrErrorRed = Color(0xFFC62828)
val SrDoneGreen = Color(0xFF2E7D32)

private val LightColorScheme = lightColorScheme(
  primary = SrBlue,
  onPrimary = Color.White,
  primaryContainer = SrPrimaryContainer,
  onPrimaryContainer = SrBlueDeep,
  secondary = SrOrange,
  onSecondary = Color.White,
  secondaryContainer = SrSecondaryContainer,
  onSecondaryContainer = Color(0xFF5D3200),
  surface = SrSurfaceBg,
  onSurface = SrOnSurface,
  onSurfaceVariant = SrOnSurfaceVariant,
  surfaceVariant = Color.White,
  outline = Color(0xFF9E9E9E),
  outlineVariant = SrOutlineVariant,
  error = SrErrorRed,
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF90CAF9),
  onPrimary = Color(0xFF0D47A1),
  primaryContainer = Color(0xFF1565C0),
  onPrimaryContainer = Color(0xFFE3F2FD),
  secondary = Color(0xFFFFB74D),
  onSecondary = Color(0xFF3E2723),
  secondaryContainer = Color(0xFF5D3200),
  onSecondaryContainer = Color(0xFFFFF3E0),
  surface = Color(0xFF121212),
  onSurface = Color(0xFFE0E0E0),
  onSurfaceVariant = Color(0xFFBDBDBD),
  surfaceVariant = Color(0xFF1E1E1E),
  outline = Color(0xFF757575),
  outlineVariant = Color(0xFF424242),
  error = Color(0xFFEF9A9A),
)

@Composable
fun AppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
    content = content,
  )
}
