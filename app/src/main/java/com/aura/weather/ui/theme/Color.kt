package com.aura.weather.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Central color palette for the Aura Weather "liquid glass" aesthetic.
 * Everything is a deep navy/near-black base with translucent glass layers
 * on top, plus a small set of accent colors used inside the animated orb
 * and the forecast range bars.
 */
object AuraColors {

    // Base app background (near-black navy, not pure black so glass has
    // something to catch light against).
    val BackgroundTop = Color(0xFF07070F)
    val BackgroundBottom = Color(0xFF0C0E1C)

    // Glass surface tints (kept low-alpha so the background always shows
    // through — this is what keeps it reading as "glass" rather than a
    // flat card).
    val GlassFill = Color(0x1FFFFFFF)      // ~12% white
    val GlassFillStrong = Color(0x2EFFFFFF) // ~18% white, used on the hero orb
    val GlassStroke = Color(0x4DFFFFFF)     // edge highlight
    val GlassStrokeDim = Color(0x1AFFFFFF)  // dimmer edge, bottom/sides
    val GlassShadow = Color(0x66000000)

    // Text
    val TextPrimary = Color(0xFFF5F6FA)
    val TextSecondary = Color(0xFFAFB4C8)
    val TextTertiary = Color(0xFF7D8299)

    // Accent / brand
    val AccentViolet = Color(0xFF6E5BFF)
    val AccentBlue = Color(0xFF4C7DFF)
    val AccentCyan = Color(0xFF52C7E8)

    // Orb condition palettes (low -> high energy color stops used to tint
    // the animated blob per weather state)
    val OrbClear = listOf(Color(0xFFFFC24B), Color(0xFFFF9A3D), Color(0xFF6E5BFF))
    val OrbPartlyCloudy = listOf(Color(0xFF9FB4FF), Color(0xFFFFC24B), Color(0xFF5A63C9))
    val OrbCloudy = listOf(Color(0xFF8B93B8), Color(0xFF5D6489), Color(0xFF3C4066))
    val OrbRain = listOf(Color(0xFF4C7DFF), Color(0xFF2E4FA3), Color(0xFF1C2A5E))
    val OrbStorm = listOf(Color(0xFF6E5BFF), Color(0xFF3A2F8C), Color(0xFF14123A))
    val OrbSnow = listOf(Color(0xFFE8F1FF), Color(0xFF9FC4FF), Color(0xFF4C6FA3))
    val OrbNight = listOf(Color(0xFF3C4B9A), Color(0xFF1E2452), Color(0xFF0C0E1C))

    // Temperature gradient used in the 10-day range bars
    val TempLow = Color(0xFF4C9EFF)
    val TempHigh = Color(0xFFFFA24B)
}
