package ru.max.superresolution.monitor

import androidx.compose.material3.MaterialTheme
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

private val AppColorScheme = lightColorScheme(
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

@Composable
fun AppTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AppColorScheme,
    content = content,
  )
}
