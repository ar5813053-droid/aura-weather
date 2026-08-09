package com.aura.weather.ui.weather

import androidx.compose.ui.graphics.Color
import com.aura.weather.ui.theme.AuraColors

/**
 * The set of weather conditions the orb / iconography system knows how to
 * render. This is intentionally decoupled from any network model — a future
 * data layer maps API condition codes onto this enum, nothing in the UI
 * layer needs to change.
 */
enum class WeatherVisualState {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    RAIN,
    STORM,
    SNOW,
    NIGHT
}

/**
 * Per-state visual tuning for the animated orb: a color ramp used inside the
 * liquid shader/gradient, and a couple of intensity knobs so calmer states
 * (clear sky) drift gently while energetic states (storm) move faster.
 */
data class OrbPalette(
    val colors: List<Color>,
    val flowSpeed: Float,   // relative animation speed multiplier
    val turbulence: Float   // relative blob distortion amount, 0..1
)

fun WeatherVisualState.orbPalette(): OrbPalette = when (this) {
    WeatherVisualState.CLEAR -> OrbPalette(AuraColors.OrbClear, flowSpeed = 0.6f, turbulence = 0.25f)
    WeatherVisualState.PARTLY_CLOUDY -> OrbPalette(AuraColors.OrbPartlyCloudy, flowSpeed = 0.75f, turbulence = 0.35f)
    WeatherVisualState.CLOUDY -> OrbPalette(AuraColors.OrbCloudy, flowSpeed = 0.5f, turbulence = 0.3f)
    WeatherVisualState.RAIN -> OrbPalette(AuraColors.OrbRain, flowSpeed = 1.0f, turbulence = 0.55f)
    WeatherVisualState.STORM -> OrbPalette(AuraColors.OrbStorm, flowSpeed = 1.4f, turbulence = 0.8f)
    WeatherVisualState.SNOW -> OrbPalette(AuraColors.OrbSnow, flowSpeed = 0.45f, turbulence = 0.2f)
    WeatherVisualState.NIGHT -> OrbPalette(AuraColors.OrbNight, flowSpeed = 0.4f, turbulence = 0.2f)
}

