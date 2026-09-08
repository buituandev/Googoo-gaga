package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = AmberPrimaryDark,
    onPrimary = AmberOnPrimaryDark,
    primaryContainer = AmberPrimaryContainerDark,
    onPrimaryContainer = AmberOnPrimaryContainerDark,
    secondaryContainer = AmberSecondaryContainerDark,
    onSecondaryContainer = AmberOnSecondaryContainerDark,
    tertiaryContainer = AmberTertiaryContainerDark,
    onTertiaryContainer = AmberOnTertiaryContainerDark,
    surface = AmberSurfaceDark,
    surfaceContainerLow = AmberSurfaceContainerLowDark,
    surfaceContainer = AmberSurfaceContainerDark,
    surfaceContainerHigh = AmberSurfaceContainerHighDark,
    surfaceContainerHighest = AmberSurfaceContainerHighestDark,
    onSurface = AmberOnSurfaceDark,
    onSurfaceVariant = AmberOnSurfaceVariantDark,
    outline = AmberOutlineDark,
    outlineVariant = AmberOutlineVariantDark,
    inverseSurface = AmberInverseSurfaceDark,
    inverseOnSurface = AmberInverseOnSurfaceDark,
    inversePrimary = AmberInversePrimaryDark,
    error = AmberErrorDark,
    onError = AmberOnErrorDark,
    errorContainer = AmberErrorContainerDark,
    onErrorContainer = AmberOnErrorContainerDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = AmberPrimaryLight,
    onPrimary = AmberOnPrimaryLight,
    primaryContainer = AmberPrimaryContainerLight,
    onPrimaryContainer = AmberOnPrimaryContainerLight,
    secondaryContainer = AmberSecondaryContainerLight,
    onSecondaryContainer = AmberOnSecondaryContainerLight,
    tertiaryContainer = AmberTertiaryContainerLight,
    onTertiaryContainer = AmberOnTertiaryContainerLight,
    surface = AmberSurfaceLight,
    surfaceContainerLow = AmberSurfaceContainerLowLight,
    surfaceContainer = AmberSurfaceContainerLight,
    surfaceContainerHigh = AmberSurfaceContainerHighLight,
    surfaceContainerHighest = AmberSurfaceContainerHighestLight,
    onSurface = AmberOnSurfaceLight,
    onSurfaceVariant = AmberOnSurfaceVariantLight,
    outline = AmberOutlineLight,
    outlineVariant = AmberOutlineVariantLight,
    inverseSurface = AmberInverseSurfaceLight,
    inverseOnSurface = AmberInverseOnSurfaceLight,
    inversePrimary = AmberInversePrimaryLight,
    error = AmberErrorLight,
    onError = AmberOnErrorLight,
    errorContainer = AmberErrorContainerLight,
    onErrorContainer = AmberOnErrorContainerLight,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialExpressiveTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = Shapes,
    content = content
  )
}
