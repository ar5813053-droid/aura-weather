package com.aura.weather.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.weather.ui.theme.AuraColors
import com.aura.weather.ui.theme.AuraType

/**
 * Humidity / Wind / Pressure / Visibility strip. Icons are drawn locally
 * via GlyphIcons.kt (Canvas vector shapes) rather than
 * androidx.compose.material.icons.filled.{Air,Speed,Visibility,WaterDrop},
 * which only exist in the material-icons-extended artifact -- not a
 * dependency of this project.
 */
@Composable
fun MetricsRow(
    humidity: String,
    wind: String,
    pressure: String,
    visibility: String,
    modifier: Modifier = Modifier
) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(vertical = 18.dp, horizontal = 8.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricColumn(glyph = { tint -> DropletGlyph(tint = tint, size = 22.dp) }, label = "Humidity", value = humidity)
            MetricColumn(glyph = { tint -> WindGlyph(tint = tint, size = 22.dp) }, label = "Wind", value = wind)
            MetricColumn(glyph = { tint -> GaugeGlyph(tint = tint, size = 22.dp) }, label = "Pressure", value = pressure)
            MetricColumn(glyph = { tint -> EyeGlyph(tint = tint, size = 22.dp) }, label = "Visibility", value = visibility)
        }
    }
}

@Composable
private fun MetricColumn(
    glyph: @Composable (tint: androidx.compose.ui.graphics.Color) -> Unit,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        glyph(AuraColors.TextSecondary)
        Text(
            text = label,
            color = AuraColors.TextSecondary,
            style = AuraType.Subtext,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = value,
            color = AuraColors.TextPrimary,
            style = AuraType.Condition.copy(fontSize = 15.sp)
        )
    }
}