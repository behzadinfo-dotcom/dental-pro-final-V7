package com.example.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adds a soft, theme-colored glow (blurred colored shadow) behind a
 * composable — the core building block for the "neon" look. Cheap to use
 * on cards, banners, buttons and selected chips.
 */
fun Modifier.neonGlow(
    color: Color,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 18.dp,
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = color,
    spotColor = color,
    clip = false
)

/** A crisp glowing border in the theme's accent color, for selected states. */
fun Modifier.neonBorder(
    color: Color,
    shape: Shape = RoundedCornerShape(16.dp),
    width: Dp = 1.5.dp,
): Modifier = this
    .neonGlow(color = color, shape = shape, elevation = 10.dp)
    .border(width = width, color = color, shape = shape)

/** Diagonal hero/banner gradient built from the active palette's glow pair. */
@Composable
fun NeonPalette.heroBrush(): Brush = Brush.linearGradient(
    colors = listOf(gradientStart, gradientEnd, background)
)

/** Radial accent glow, handy behind logos/icons for a punchier focal point. */
@Composable
fun NeonPalette.glowRadialBrush(): Brush = Brush.radialGradient(
    colors = listOf(glow.copy(alpha = 0.35f), Color.Transparent)
)
