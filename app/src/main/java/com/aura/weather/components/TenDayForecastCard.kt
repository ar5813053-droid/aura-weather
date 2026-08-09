package com.aura.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.weather.ui.theme.AuraColors
import com.aura.weather.ui.theme.AuraType
import com.aura.weather.ui.weather.WeatherVisualState

data class DailyForecast(
    val dayLabel: String,
    val condition: WeatherVisualState,
    val lowC: Int,
    val highC: Int,
    val rangeMinC: Int,
    val rangeMaxC: Int
)

@Composable
fun TenDayForecastCard(
    days: List<DailyForecast>,
    modifier: Modifier = Modifier,
    onViewAll: () -> Unit = {},
    onAddDay: (DailyForecast) -> Unit = {}
) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 18.dp, horizontal = 18.dp)) {
            SectionHeader(title = "10-DAY FORECAST", onViewAll = onViewAll)

            Column(modifier = Modifier.padding(top = 8.dp)) {
                days.forEach { day ->
                    DayRow(day = day, onAdd = { onAddDay(day) })
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: DailyForecast, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.dayLabel,
            color = AuraColors.TextPrimary,
            style = AuraType.Subtext.copy(fontSize = 16.sp),
            modifier = Modifier.width(56.dp)
        )
        WeatherConditionIcon(state = day.condition, size = 24.dp)

        Text(
            text = "${day.lowC}°",
            color = AuraColors.AccentBlue,
            style = AuraType.Subtext.copy(fontSize = 15.sp),
            modifier = Modifier
                .padding(start = 12.dp)
                .width(28.dp)
        )

        RangeBar(
            rangeMin = day.rangeMinC,
            rangeMax = day.rangeMaxC,
            valueLow = day.lowC,
            valueHigh = day.highC,
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .padding(horizontal = 6.dp)
        )

        Text(
            text = "${day.highC}°",
            color = AuraColors.TextPrimary,
            style = AuraType.Subtext.copy(fontSize = 15.sp),
            modifier = Modifier.width(30.dp)
        )

        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(AuraColors.GlassFillStrong)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add ${day.dayLabel} to reminders",
                tint = AuraColors.TextPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun RangeBar(
    rangeMin: Int,
    rangeMax: Int,
    valueLow: Int,
    valueHigh: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val trackHeight = size.height * 0.28f
        val trackY = size.height / 2f
        val span = (rangeMax - rangeMin).coerceAtLeast(1)
        val startFraction = (valueLow - rangeMin).toFloat() / span
        val endFraction = (valueHigh - rangeMin).toFloat() / span

        // Full dim track
        drawRoundRect(
            color = AuraColors.GlassFill,
            topLeft = Offset(0f, trackY - trackHeight / 2f),
            size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        val startX = size.width * startFraction
        val endX = size.width * endFraction
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(AuraColors.TempLow, AuraColors.TempHigh),
                startX = startX,
                endX = endX
            ),
            topLeft = Offset(startX, trackY - trackHeight / 2f),
            size = androidx.compose.ui.geometry.Size((endX - startX).coerceAtLeast(4f), trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        drawCircle(color = Color.White, radius = trackHeight * 0.62f, center = Offset(endX, trackY))
    }
}
