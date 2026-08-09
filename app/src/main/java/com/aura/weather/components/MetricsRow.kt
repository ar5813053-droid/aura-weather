package com.aura.weather.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.weather.ui.theme.AuraColors
import com.aura.weather.ui.theme.AuraType

data class WeatherMetric(val icon: ImageVector, val label: String, val value: String)

@Composable
fun MetricsRow(
    humidity: String,
    wind: String,
    pressure: String,
    visibility: String,
    modifier: Modifier = Modifier
) {
    val metrics = listOf(
        WeatherMetric(Icons.Filled.WaterDrop, "Humidity", humidity),
        WeatherMetric(Icons.Filled.Air, "Wind", wind),
        WeatherMetric(Icons.Filled.Speed, "Pressure", pressure),
        WeatherMetric(Icons.Filled.Visibility, "Visibility", visibility)
    )

    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(vertical = 18.dp, horizontal = 8.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            metrics.forEach { metric ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = metric.icon,
                        contentDescription = metric.label,
                        tint = AuraColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(text = metric.label, color = AuraColors.TextSecondary, style = AuraType.Subtext)
                    Text(text = metric.value, color = AuraColors.TextPrimary, style = AuraType.Condition.copy(fontSize = 15.sp))
                }
            }
        }
    }
}
