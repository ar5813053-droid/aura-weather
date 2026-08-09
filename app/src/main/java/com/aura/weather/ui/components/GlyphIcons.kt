package com.aura.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small set of locally-drawn vector glyphs (pure Canvas Path/line
 * primitives -- no bitmaps, no emoji, no text-as-icon) that stand in for
 * Material icons which live only in the `material-icons-extended`
 * artifact. That dependency is intentionally NOT part of this project, so
 * these are real, first-class vector icons rendered at draw time instead.
 *
 * Each glyph takes an explicit [tint] because Canvas drawing doesn't pick
 * up LocalContentColor the way the Icon composable does.
 */

@Composable
fun DropletGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            cubicTo(w * 0.5f, h * 0.06f, w * 0.86f, h * 0.52f, w * 0.86f, h * 0.62f)
            cubicTo(w * 0.86f, h * 0.82f, w * 0.70f, h * 0.94f, w * 0.5f, h * 0.94f)
            cubicTo(w * 0.30f, h * 0.94f, w * 0.14f, h * 0.82f, w * 0.14f, h * 0.62f)
            cubicTo(w * 0.14f, h * 0.52f, w * 0.5f, h * 0.06f, w * 0.5f, h * 0.06f)
            close()
        }
        drawPath(path = path, color = tint, style = Fill)
    }
}

@Composable
fun WindGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val top = Path().apply {
            moveTo(w * 0.12f, h * 0.30f)
            lineTo(w * 0.62f, h * 0.30f)
            cubicTo(w * 0.80f, h * 0.30f, w * 0.80f, h * 0.10f, w * 0.62f, h * 0.10f)
            cubicTo(w * 0.50f, h * 0.10f, w * 0.48f, h * 0.20f, w * 0.52f, h * 0.24f)
        }
        val mid = Path().apply {
            moveTo(w * 0.12f, h * 0.52f)
            lineTo(w * 0.78f, h * 0.52f)
            cubicTo(w * 0.95f, h * 0.52f, w * 0.95f, h * 0.74f, w * 0.78f, h * 0.74f)
            cubicTo(w * 0.66f, h * 0.74f, w * 0.64f, h * 0.62f, w * 0.68f, h * 0.58f)
        }
        val bottom = Path().apply {
            moveTo(w * 0.12f, h * 0.86f)
            lineTo(w * 0.55f, h * 0.86f)
        }

        drawPath(top, color = tint, style = stroke)
        drawPath(mid, color = tint, style = stroke)
        drawPath(bottom, color = tint, style = stroke)
    }
}

@Composable
fun GaugeGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.62f
        val r = w * 0.38f

        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        )

        val needleAngle = Math.toRadians(-35.0)
        val needleLen = r * 0.75f
        drawLine(
            color = tint,
            start = Offset(cx, cy),
            end = Offset(
                cx + (kotlin.math.cos(needleAngle) * needleLen).toFloat(),
                cy + (kotlin.math.sin(needleAngle) * needleLen).toFloat()
            ),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
        drawCircle(color = tint, radius = w * 0.06f, center = Offset(cx, cy))
    }
}

@Composable
fun EyeGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.52f

        val path = Path().apply {
            moveTo(w * 0.06f, cy)
            cubicTo(w * 0.22f, h * 0.22f, w * 0.78f, h * 0.22f, w * 0.94f, cy)
            cubicTo(w * 0.78f, h * 0.82f, w * 0.22f, h * 0.82f, w * 0.06f, cy)
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color = tint, radius = w * 0.11f, center = Offset(cx, cy))
    }
}

@Composable
fun MapGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val panelW = w * 0.30f

        val outline = Path().apply {
            moveTo(w * 0.04f, h * 0.20f)
            lineTo(w * 0.04f + panelW, h * 0.08f)
            lineTo(w * 0.04f + panelW * 2f, h * 0.20f)
            lineTo(w * 0.04f + panelW * 3f, h * 0.08f)
            lineTo(w * 0.04f + panelW * 3f, h * 0.80f)
            lineTo(w * 0.04f + panelW * 2f, h * 0.92f)
            lineTo(w * 0.04f + panelW, h * 0.80f)
            lineTo(w * 0.04f, h * 0.92f)
            close()
        }
        drawPath(path = outline, color = tint, style = Stroke(width = w * 0.055f, join = StrokeJoin.Round, cap = StrokeCap.Round))

        val foldStroke = Stroke(width = w * 0.035f, cap = StrokeCap.Round)
        drawLine(tint.copy(alpha = 0.6f), Offset(w * 0.04f + panelW, h * 0.08f), Offset(w * 0.04f + panelW, h * 0.80f), foldStroke.width, StrokeCap.Round)
        drawLine(tint.copy(alpha = 0.6f), Offset(w * 0.04f + panelW * 2f, h * 0.20f), Offset(w * 0.04f + panelW * 2f, h * 0.92f), foldStroke.width, StrokeCap.Round)
    }
}

@Composable
fun ChevronRightGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.34f, h * 0.18f)
            lineTo(w * 0.72f, h * 0.5f)
            lineTo(w * 0.34f, h * 0.82f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
