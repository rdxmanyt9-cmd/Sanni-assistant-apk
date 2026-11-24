package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val MarcoDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldDark,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldBright,
    secondary = CyanTealAccent,
    onSecondary = OnEmeraldDark,
    secondaryContainer = DeepObsidian,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberConfirm,
    onTertiary = AmoledBlack,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberConfirm,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = DeepObsidian,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorderEmerald,
    outlineVariant = GlassBorderSubtle,
    error = CrimsonAlert,
    onError = AmoledBlack,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonAlert
)

val MarcoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MarcoAiTheme(
    pureAmoledBlack: Boolean = true,
    content: @Composable () -> Unit
) {
    val scheme = if (pureAmoledBlack) {
        MarcoDarkColorScheme
    } else {
        MarcoDarkColorScheme.copy(background = DeepObsidian)
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = MarcoTypography,
        shapes = MarcoShapes,
        content = content
    )
}
