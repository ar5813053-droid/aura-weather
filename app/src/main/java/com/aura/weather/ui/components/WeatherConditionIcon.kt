package com.aura.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.weather.ui.weather.WeatherVisualState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a small weather-condition glyph entirely with Canvas primitives —
 * no emoji characters, no bitmap/drawable assets, no placeholder shapes.
 * Used for the hourly strip and the 10-day list where a compact icon is
 * needed next to text.
 */
@Composable
fun WeatherConditionIcon(
    state: WeatherVisualState,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (state) {
            WeatherVisualState.CLEAR -> drawSun(w, h, Color(0xFFFFC24B))
            WeatherVisualState.NIGHT -> drawMoon(w, h, Color(0xFFE8ECFF))
            WeatherVisualState.PARTLY_CLOUDY -> {
                drawSun(w * 0.62f, h * 0.62f, Color(0xFFFFC24B), offsetX = w * 0.30f, offsetY = h * 0.06f)
                drawCloud(w, h, Color(0xFFF3F5FB), yBias = 0.30f)
            }
            WeatherVisualState.CLOUDY -> drawCloud(w, h, Color(0xFFD3D8EA), yBias = 0f, scale = 1.05f)
            WeatherVisualState.RAIN -> {
                drawCloud(w, h * 0.8f, Color(0xFFB9C1DE), yBias = -0.12f)
                drawRainDrops(w, h)
            }
            WeatherVisualState.STORM -> {
                drawCloud(w, h * 0.8f, Color(0xFF8B90B3), yBias = -0.12f)
                drawBolt(w, h)
            }
            WeatherVisualState.SNOW -> {
                drawCloud(w, h * 0.8f, Color(0xFFD8DEF2), yBias = -0.12f)
                drawSnowDots(w, h)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSun(
    w: Float,
    h: Float,
    color: Color,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    val cx = offsetX + w / 2f
    val cy = offsetY + h / 2f
    val r = minOf(w, h) * 0.26f
    // Rays
    val rayLen = r * 0.7f
    for (i in 0 until 8) {
        val angle = Math.toRadians((i * 45).toDouble())
        val startX = cx + cos(angle).toFloat() * (r + r * 0.35f)
        val startY = cy + sin(angle).toFloat() * (r + r * 0.35f)
        val endX = cx + cos(angle).toFloat() * (r + r * 0.35f + rayLen)
        val endY = cy + sin(angle).toFloat() * (r + r * 0.35f + rayLen)
        drawLine(
            color = color,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = r * 0.22f,
            cap = StrokeCap.Round
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0.85f)),
            center = Offset(cx, cy),
            radius = r
        ),
        radius = r,
        center = Offset(cx, cy)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoon(w: Float, h: Float, color: Color) {
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) * 0.32f
    drawCircle(color = color, radius = r, center = Offset(cx, cy))
    // Crescent bite
    drawCircle(
        color = Color(0xFF0C0E1C),
        radius = r * 0.85f,
        center = Offset(cx + r * 0.55f, cy - r * 0.25f)
    )
    // Small stars
    drawCircle(color = color.copy(alpha = 0.8f), radius = r * 0.09f, center = Offset(cx - r * 1.4f, cy - r * 0.9f))
    drawCircle(color = color.copy(alpha = 0.6f), radius = r * 0.06f, center = Offset(cx - r * 0.9f, cy + r * 1.1f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    w: Float,
    h: Float,
    color: Color,
    yBias: Float,
    scale: Float = 1f
) {
    val baseY = h * (0.62f + yBias)
    val cx = w / 2f
    val bodyR = w * 0.22f * scale
    drawCircle(color = color, radius = bodyR * 0.85f, center = Offset(cx - bodyR * 0.9f, baseY))
    drawCircle(color = color, radius = bodyR, center = Offset(cx, baseY - bodyR * 0.35f))
    drawCircle(color = color, radius = bodyR * 0.95f, center = Offset(cx + bodyR * 0.95f, baseY + bodyR * 0.05f))
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - bodyR * 1.7f, baseY - bodyR * 0.1f),
        size = androidx.compose.ui.geometry.Size(bodyR * 3.4f, bodyR * 1.1f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyR * 0.5f, bodyR * 0.5f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRainDrops(w: Float, h: Float) {
    val color = Color(0xFF6FA8FF)
    val ys = h * 0.86f
    val xs = listOf(w * 0.32f, w * 0.52f, w * 0.72f)
    xs.forEachIndexed { i, x ->
        drawLine(
            color = color,
            start = Offset(x, ys - h * 0.08f * (i % 2)),
            end = Offset(x - w * 0.05f, ys + h * 0.14f - h * 0.08f * (i % 2)),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnowDots(w: Float, h: Float) {
    val color = Color(0xFFEFF3FF)
    val ys = h * 0.88f
    listOf(w * 0.32f, w * 0.52f, w * 0.72f).forEach { x ->
        drawCircle(color = color, radius = w * 0.035f, center = Offset(x, ys))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBolt(w: Float, h: Float) {
    val color = Color(0xFFFFD24B)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.56f, h * 0.62f)
        lineTo(w * 0.44f, h * 0.86f)
        lineTo(w * 0.52f, h * 0.86f)
        lineTo(w * 0.42f, h * 1.02f)
        lineTo(w * 0.62f, h * 0.78f)
        lineTo(w * 0.53f, h * 0.78f)
        lineTo(w * 0.60f, h * 0.62f)
        close()
    }
    drawPath(path, color = color, style = Fill)
}
