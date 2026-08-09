package com.aura.weather.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.weather.ui.components.BottomNavBar
import com.aura.weather.ui.components.GlassSurface
import com.aura.weather.ui.components.HourlyForecastCard
import com.aura.weather.ui.components.MetricsRow
import com.aura.weather.ui.components.NavTab
import com.aura.weather.ui.components.AuraTopBar
import com.aura.weather.ui.components.TenDayForecastCard
import com.aura.weather.ui.components.WeatherConditionIcon
import com.aura.weather.ui.components.WeatherOrb
import com.aura.weather.ui.theme.AuraColors
import com.aura.weather.ui.theme.AuraType

/**
 * Phase 3 home screen. Pure UI + sample state -- no networking, no API
 * keys. Every visual block (top bar, hero orb, metrics, hourly, 10-day,
 * bottom nav) is its own composable in ui/components so this file stays a
 * layout/composition root rather than a monolith.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val uiState = remember { sampleHomeUiState() }
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AuraColors.BackgroundTop, AuraColors.BackgroundBottom)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AuraTopBar(
                    city = uiState.city,
                    region = uiState.region,
                    pageCount = uiState.pageCount,
                    selectedPage = uiState.selectedPage
                )
            }

            item {
                HeroWeatherCard(uiState)
            }

            item {
                MetricsRow(
                    humidity = uiState.humidity,
                    wind = uiState.wind,
                    pressure = uiState.pressure,
                    visibility = uiState.visibility
                )
            }

            item {
                HourlyForecastCard(hours = uiState.hourly)
            }

            item {
                TenDayForecastCard(days = uiState.daily)
            }

            // Space for the floating bottom nav bar.
            item { Box(modifier = Modifier.padding(top = 80.dp)) }
        }

        BottomNavBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .fillMaxWidth()
                .padding(horizontal = 60.dp)
        )
    }
}

@Composable
private fun HeroWeatherCard(uiState: HomeUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f),
        contentAlignment = Alignment.Center
    ) {
        WeatherOrb(
            state = uiState.visualState,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            WeatherConditionIcon(state = uiState.visualState, size = 56.dp)

            Box(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "${uiState.currentTempC}°",
                    color = AuraColors.TextPrimary,
                    style = AuraType.HeroTemperature,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = uiState.condition,
                color = AuraColors.TextPrimary,
                style = AuraType.Condition,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Feels like ${uiState.feelsLikeC}°",
                color = AuraColors.TextSecondary,
                style = AuraType.Subtext,
                modifier = Modifier.padding(top = 2.dp)
            )

            GlassSurface(
                modifier = Modifier.padding(top = 12.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
            ) {
                Text(
                    text = "H: ${uiState.highC}°   L: ${uiState.lowC}°",
                    color = AuraColors.TextPrimary,
                    style = AuraType.Subtext,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}
