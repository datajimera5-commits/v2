package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VidoProColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = TextOffWhite,
    primaryContainer = ColorTokens.BlueContainer,
    onPrimaryContainer = TextOffWhite,
    secondary = FireOrange,
    onSecondary = TextOffWhite,
    secondaryContainer = ColorTokens.OrangeContainer,
    onSecondaryContainer = TextOffWhite,
    tertiary = CyanGlow,
    onTertiary = BackgroundObsidian,
    background = BackgroundObsidian,
    onBackground = TextOffWhite,
    surface = SurfaceDark,
    onSurface = TextOffWhite,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextMuted,
    outline = SurfaceCardBorder,
    outlineVariant = ColorTokens.BorderMuted,
    error = CrimsonError,
    onError = TextOffWhite
)

private object ColorTokens {
    val BlueContainer = androidx.compose.ui.graphics.Color(0xFF1E3A5F)
    val OrangeContainer = androidx.compose.ui.graphics.Color(0xFF431F10)
    val BorderMuted = androidx.compose.ui.graphics.Color(0xFF1F2B42)
}

val VidoProShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VidoProTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VidoProColorScheme,
        typography = VidoProTypography,
        shapes = VidoProShapes,
        content = content
    )
}
