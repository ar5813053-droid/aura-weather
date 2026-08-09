package com.aura.weather.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aura.weather.ui.weather.WeatherVisualState
import com.aura.weather.ui.weather.orbPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * AGSL shader used on API 33+ (RuntimeShader). Renders a soft moving color
 * field with a slowly travelling specular highlight -- the impression of
 * light passing through liquid. Deliberately few sin/cos terms so it stays
 * cheap per-frame at the orb's size.
 */
private const val LIQUID_SHADER = """
    uniform float2 uResolution;
    uniform float uTime;
    uniform float uTurbulence;
    uniform half4 uColorA;
    uniform half4 uColorB;
    uniform half4 uColorC;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        float2 p = uv - 0.5;

        float flow1 = sin((p.x * 3.0 + uTime * 0.6) + cos(p.y * 2.0 - uTime * 0.4)) * uTurbulence;
        float flow2 = cos((p.y * 3.0 - uTime * 0.5) + sin(p.x * 2.5 + uTime * 0.3)) * uTurbulence;
        float dist = length(p + float2(flow1, flow2) * 0.15);

        float mixA = smoothstep(0.0, 0.55, dist);
        float mixB = smoothstep(0.15, 0.75, dist);
        half4 base = mix(uColorA, uColorB, mixA);
        base = mix(base, uColorC, mixB * 0.6);

        float2 lightPos = float2(0.5 + 0.28 * cos(uTime * 0.35), 0.38 + 0.22 * sin(uTime * 0.5));
        float highlight = smoothstep(0.28, 0.0, length(uv - lightPos));
        base += half4(1.0, 1.0, 1.0, 1.0) * highlight * 0.35;

        return half4(base.rgb, 1.0);
    }
"""

/**
 * True only on API 33+ where [RuntimeShader] exists. Annotated with
 * [ChecksSdkIntAtLeast] so lint's NewApi checker recognizes `if (isRuntimeShaderSupported())`
 * as a valid version guard for the calls inside, the same way it recognizes an inline
 * `Build.VERSION.SDK_INT >=` check.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
private fun isRuntimeShaderSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Builds the AGSL liquid shader. Only ever called after [isRuntimeShaderSupported] is true. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createLiquidShader(): RuntimeShader = RuntimeShader(LIQUID_SHADER)

/**
 * Pushes this frame's uniforms into [shader] and paints [blobPath] with it. Only ever
 * called after [isRuntimeShaderSupported] is true.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawLiquidShaderOrb(
    shader: RuntimeShader,
    blobPath: Path,
    time: Float,
    turbulence: Float,
    colors: List<Color>
) {
    shader.setFloatUniform("uResolution", size.width, size.height)
    shader.setFloatUniform("uTime", time)
    shader.setFloatUniform("uTurbulence", turbulence)
    val c1 = colors.getOrElse(0) { Color.White }
    val c2 = colors.getOrElse(1) { Color.Gray }
    val c3 = colors.getOrElse(2) { Color.Black }
    shader.setFloatUniform("uColorA", c1.red, c1.green, c1.blue, 1f)
    shader.setFloatUniform("uColorB", c2.red, c2.green, c2.blue, 1f)
    shader.setFloatUniform("uColorC", c3.red, c3.green, c3.blue, 1f)
    drawPath(path = blobPath, brush = ShaderBrush(shader))
}

/**
 * The large hero weather visualization. Fills a rounded, continuously
 * deforming blob shape with either:
 *  - a live AGSL RuntimeShader driven as a Compose ShaderBrush (API 33+),
 *    giving genuine per-pixel fluid motion, or
 *  - an animated multi-stop radial gradient + moving highlight on a Canvas
 *    (API 24-32 fallback), still driven by real per-frame animation state,
 * with a shared glass rim (stroke + inner highlight) drawn on top either
 * way so both render paths read as the same design.
 *
 * Animation is lifecycle-aware: an infinite transition's time input is
 * frozen at 0 whenever the host lifecycle isn't STARTED/RESUMED, so the
 * shader/gradient stops doing per-frame work in the background.
 */
@Composable
fun WeatherOrb(
    state: WeatherVisualState,
    modifier: Modifier = Modifier
) {
    var isActive by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isActive = event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val palette = state.orbPalette()

    val infiniteTransition = rememberInfiniteTransition(label = "orbFlow")
    val rawTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (14000 / palette.flowSpeed).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    val rawPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (9000 / palette.flowSpeed).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blobPhase"
    )

    val effectiveTime = if (isActive) rawTime * 40f else 0f
    val effectivePhase = if (isActive) rawPhase else 0f

    val useShader = isRuntimeShaderSupported()
    val shader = remember(useShader) { if (useShader) createLiquidShader() else null }

    Canvas(modifier = modifier) {
        val blobPath = organicBlobPath(size.width, size.height, effectivePhase, palette.turbulence)

        if (useShader && shader != null) {
            drawLiquidShaderOrb(
                shader = shader,
                blobPath = blobPath,
                time = effectiveTime,
                turbulence = palette.turbulence,
                colors = palette.colors
            )
        } else {
            val angle = effectivePhase
            val cx = size.width / 2f + cos(angle) * size.width * 0.08f
            val cy = size.height / 2f + sin(angle * 1.3f) * size.height * 0.08f
            clipPath(blobPath) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = palette.colors + palette.colors.first(),
                        center = Offset(cx, cy),
                        radius = size.width * 0.85f
                    )
                )
                val hx = size.width * (0.5f + 0.26f * cos(effectivePhase * 0.7f))
                val hy = size.height * (0.35f + 0.2f * sin(effectivePhase * 0.9f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(hx, hy),
                        radius = size.width * 0.28f
                    ),
                    radius = size.width * 0.28f,
                    center = Offset(hx, hy)
                )
            }
        }

        drawPath(
            path = blobPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.20f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            style = Stroke(width = size.width * 0.012f)
        )
        drawPath(
            path = organicBlobPath(size.width * 0.86f, size.height * 0.5f, effectivePhase, palette.turbulence * 0.6f),
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
            ),
            alpha = 0.5f
        )
    }
}

/**
 * Builds a smooth, non-circular blob outline using quadratic beziers
 * between a ring of animated control points. Each point oscillates with
 * its own phase offset so the shape breathes organically instead of
 * simply rotating or scaling uniformly.
 */
private fun organicBlobPath(w: Float, h: Float, phase: Float, turbulence: Float): Path {
    val cx = w / 2f
    val cy = h / 2f
    val baseR = minOf(w, h) / 2f * 0.92f
    val pointCount = 8
    val points = (0 until pointCount).map { i ->
        val angle = (2 * PI * i / pointCount).toFloat()
        val wobble = sin(phase + i * 1.7f) * baseR * turbulence * 0.18f
        val r = baseR + wobble
        Offset(cx + cos(angle) * r, cy + sin(angle) * r * (h / w).coerceIn(0.7f, 1f))
    }

    return Path().apply {
        moveTo((points[0].x + points.last().x) / 2f, (points[0].y + points.last().y) / 2f)
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            val midX = (current.x + next.x) / 2f
            val midY = (current.y + next.y) / 2f
            quadraticTo(current.x, current.y, midX, midY)
        }
        close()
    }
}
