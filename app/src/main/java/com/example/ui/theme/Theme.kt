package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TripShieldLightColorScheme = lightColorScheme(
    primary = TripPrimary,
    onPrimary = TripOnPrimary,
    primaryContainer = TripPrimaryContainer,
    onPrimaryContainer = TripOnPrimaryContainer,
    inversePrimary = TripInversePrimary,
    secondary = TripSecondary,
    onSecondary = TripOnSecondary,
    secondaryContainer = TripSecondaryContainer,
    onSecondaryContainer = TripOnSecondaryContainer,
    tertiary = TripTertiary,
    onTertiary = TripOnTertiary,
    tertiaryContainer = TripTertiaryContainer,
    onTertiaryContainer = TripOnTertiaryContainer,
    background = TripSurface,
    onBackground = TripOnSurface,
    surface = TripSurface,
    onSurface = TripOnSurface,
    surfaceVariant = TripSurfaceContainerHighest,
    onSurfaceVariant = TripOnSurfaceVariant,
    surfaceTint = TripPrimary,
    inverseSurface = TripInverseSurface,
    inverseOnSurface = TripInverseOnSurface,
    error = TripError,
    onError = TripOnError,
    errorContainer = TripErrorContainer,
    onErrorContainer = TripOnErrorContainer,
    outline = TripOutline,
    outlineVariant = TripOutlineVariant,
    surfaceBright = TripSurfaceBright,
    surfaceDim = TripSurfaceDim,
    surfaceContainerLowest = TripSurfaceContainerLowest,
    surfaceContainerLow = TripSurfaceContainerLow,
    surfaceContainer = TripSurfaceContainer,
    surfaceContainerHigh = TripSurfaceContainerHigh,
    surfaceContainerHighest = TripSurfaceContainerHighest
)

val TripShieldShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TripShieldTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TripShieldLightColorScheme,
        typography = TripShieldTypography,
        shapes = TripShieldShapes,
        content = content
    )
}
