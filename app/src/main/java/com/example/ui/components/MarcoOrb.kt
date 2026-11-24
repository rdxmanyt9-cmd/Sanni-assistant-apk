package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.AssistantStatus
import com.example.ui.theme.AmberConfirm
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanTealAccent
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GlassBorderEmerald
import com.example.ui.theme.GlassSurface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class ParticleSeed(
    val angleOffset: Float,
    val radiusFactor: Float,
    val speedFactor: Float,
    val sizeDp: Float
)

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = GlassBorderEmerald,
    backgroundColor: Color = GlassSurface,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun MarcoGlowingOrb(
    status: AssistantStatus,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 176.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "marco_orb")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathe"
    )

    val smoothAmp by animateFloatAsState(
        targetValue = when (status) {
            is AssistantStatus.Listening -> amplitude.coerceIn(0.18f, 1f)
            is AssistantStatus.Speaking -> 0.65f
            is AssistantStatus.Thinking -> 0.45f
            else -> 0.1f
        },
        animationSpec = spring(stiffness = 280f),
        label = "orb_amp"
    )

    val accentColor = when (status) {
        is AssistantStatus.Listening -> EmeraldBright
        is AssistantStatus.Speaking -> CyanTealAccent
        is AssistantStatus.Thinking -> EmeraldPrimary
        is AssistantStatus.AwaitingConfirmation -> AmberConfirm
        is AssistantStatus.Error -> CrimsonAlert
        AssistantStatus.Idle -> EmeraldPrimary
    }

    val particles = remember {
        List(14) { i ->
            ParticleSeed(
                angleOffset = i * (360f / 14f),
                radiusFactor = 0.38f + (i % 4) * 0.045f,
                speedFactor = if (i % 2 == 0) 1f else -0.75f,
                sizeDp = (2.2f + (i % 3) * 1.1f)
            )
        }
    }

    Box(
        modifier = modifier
            .size(orbSize)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Marco AI glowing orb, tap to start or stop voice listening"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.28f * breathe * (1f + smoothAmp * 0.14f)

            // Outer ambient nebula glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.32f + smoothAmp * 0.18f),
                        EmeraldDeep.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.5f
                ),
                center = center,
                radius = size.minDimension * 0.5f
            )

            // Rotating plasma arc rings
            val ring1Radius = baseRadius * 1.42f
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = 0.78f),
                        EmeraldBright.copy(alpha = 0.95f),
                        Color.Transparent
                    )
                ),
                startAngle = rotation,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = Offset(center.x - ring1Radius, center.y - ring1Radius),
                size = Size(ring1Radius * 2, ring1Radius * 2),
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
            )

            val ring2Radius = baseRadius * 1.24f
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        CyanTealAccent.copy(alpha = 0.65f),
                        accentColor.copy(alpha = 0.45f),
                        Color.Transparent
                    )
                ),
                startAngle = -rotation * 1.4f,
                sweepAngle = 165f,
                useCenter = false,
                topLeft = Offset(center.x - ring2Radius, center.y - ring2Radius),
                size = Size(ring2Radius * 2, ring2Radius * 2),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Floating orbital particles
            particles.forEach { p ->
                val rad = ((rotation * p.speedFactor + p.angleOffset) * PI / 180.0).toFloat()
                val orbitR = size.minDimension * p.radiusFactor * (1f + smoothAmp * 0.08f)
                val px = center.x + cos(rad) * orbitR
                val py = center.y + sin(rad) * orbitR
                drawCircle(
                    color = accentColor.copy(alpha = 0.55f),
                    radius = p.sizeDp.dp.toPx() * 0.5f,
                    center = Offset(px, py)
                )
            }

            // Core liquid-glass sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        EmeraldBright.copy(alpha = 0.95f),
                        accentColor.copy(alpha = 0.85f),
                        EmeraldDeep.copy(alpha = 0.9f),
                        Color(0xFF03140D)
                    ),
                    center = Offset(center.x - baseRadius * 0.22f, center.y - baseRadius * 0.22f),
                    radius = baseRadius * 1.15f
                ),
                center = center,
                radius = baseRadius
            )

            // Specular liquid glass highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.48f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - baseRadius * 0.34f, center.y - baseRadius * 0.34f),
                    radius = baseRadius * 0.46f
                ),
                center = Offset(center.x - baseRadius * 0.32f, center.y - baseRadius * 0.32f),
                radius = baseRadius * 0.42f
            )
        }
    }
}

@Composable
fun VoiceWaveformBar(
    status: AssistantStatus,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val barColor = when (status) {
        is AssistantStatus.Listening -> EmeraldBright
        is AssistantStatus.Speaking -> CyanTealAccent
        is AssistantStatus.Thinking -> EmeraldPrimary
        is AssistantStatus.AwaitingConfirmation -> AmberConfirm
        is AssistantStatus.Error -> CrimsonAlert
        AssistantStatus.Idle -> EmeraldDeep.copy(alpha = 0.7f)
    }

    val activeEnergy = when (status) {
        is AssistantStatus.Listening -> amplitude.coerceIn(0.25f, 1f)
        is AssistantStatus.Speaking -> 0.72f
        is AssistantStatus.Thinking -> 0.42f
        else -> 0.14f
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .semantics { contentDescription = "Voice waveform indicator" }
    ) {
        val barCount = 28
        val spacing = size.width / (barCount * 1.8f)
        val barWidth = (size.width - spacing * (barCount - 1)) / barCount
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val normPos = i.toFloat() / barCount
            val envelope = sin(normPos * PI).toFloat()
            val wave = ((sin(phase + i * 0.55f) + 1f) * 0.5f)
            val barH = (size.height * 0.16f) + (size.height * 0.8f * activeEnergy * envelope * (0.35f + 0.65f * wave))
            val x = i * (barWidth + spacing)
            drawRoundRect(
                color = barColor.copy(alpha = 0.45f + 0.55f * envelope),
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
