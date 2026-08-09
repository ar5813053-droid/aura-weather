package com.aura.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.aura.weather.ui.theme.AuraColors

/**
 * A reusable "liquid glass" surface: a translucent fill, a soft outer
 * shadow to lift it off the background, and a gradient border that is
 * brighter along the top-left edge than the bottom-right edge to fake a
 * single light source catching the rim of the glass. This border gradient
 * is what reads as "edge lighting" rather than a flat 1dp stroke.
 *
 * Deliberately does NOT use real-time blur of underlying content (Compose's
 * Modifier.blur only blurs this composable's own children on most devices,
 * it can't sample siblings below it without RenderEffect/backdrop support
 * that varies a lot across API levels). Instead the glass feel comes from
 * layered alpha + gradients, which is cheap, consistent across minSdk 24+,
 * and avoids "excessive blur" per the design brief.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    fillColor: Color = AuraColors.GlassFill,
    elevation: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = AuraColors.GlassShadow,
                spotColor = AuraColors.GlassShadow,
                clip = false
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        fillColor.copy(alpha = fillColor.alpha * 1.4f).coerceAlpha(),
                        fillColor
                    )
                )
            )
            .background(
                // Diagonal sheen: brightens the top-left third of the
                // surface, fades to nothing by the middle.
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.0f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(600f, 600f)
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        AuraColors.GlassStroke,
                        AuraColors.GlassStrokeDim,
                        AuraColors.GlassStrokeDim
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(400f, 800f)
                ),
                shape = shape
            )
    ) {
        content()
    }
}

private fun Color.coerceAlpha(): Color = copy(alpha = alpha.coerceIn(0f, 1f))

/** Convenience no-shape variant used for pill-shaped chips/badges. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        elevation = 6.dp,
        content = content
    )
}
