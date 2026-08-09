package com.aura.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.weather.ui.theme.AuraColors
import com.aura.weather.ui.theme.AuraType
import com.aura.weather.ui.weather.WeatherVisualState

data class HourlyPoint(
    val label: String,
    val tempC: Int,
    val condition: WeatherVisualState,
    val isNow: Boolean = false
)

@Composable
fun HourlyForecastCard(
    hours: List<HourlyPoint>,
    modifier: Modifier = Modifier,
    onViewAll: () -> Unit = {}
) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 18.dp, horizontal = 18.dp)) {
            SectionHeader(title = "HOURLY FORECAST", onViewAll = onViewAll)

            LazyRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(hours) { hour ->
                    HourColumn(hour)
                }
            }

            HourlyTrendLine(
                values = hours.map { it.tempC },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(top = 10.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun HourColumn(hour: HourlyPoint) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .then(
                if (hour.isNow) Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AuraColors.GlassFillStrong)
                else Modifier
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = hour.label, color = AuraColors.TextSecondary, style = AuraType.Subtext.copy(fontSize = 12.sp))
        WeatherConditionIcon(state = hour.condition, size = 24.dp, modifier = Modifier.padding(vertical = 8.dp))
        Text(text = "${hour.tempC}°", color = AuraColors.TextPrimary, style = AuraType.Condition.copy(fontSize = 15.sp))
    }
}

@Composable
private fun HourlyTrendLine(values: List<Int>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val min = values.min()
    val max = values.max().coerceAtLeast(min + 1)

    Canvas(modifier = modifier) {
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val points = values.mapIndexed { index, value ->
            val normalized = (value - min).toFloat() / (max - min).toFloat()
            Offset(
                x = stepX * index,
                y = size.height - (normalized * size.height * 0.8f) - size.height * 0.1f
            )
        }

        val path = Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }

        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.35f),
            style = Stroke(width = 2.dp.toPx())
        )
        points.forEach { point ->
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = point)
        }
    }
}

@Composable
fun SectionHeader(title: String, onViewAll: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = AuraColors.TextSecondary, style = AuraType.SectionLabel)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onViewAll)
        ) {
            Text(text = "View All", color = AuraColors.TextSecondary, style = AuraType.Subtext)
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = AuraColors.TextSecondary,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}
