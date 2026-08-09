package com.aura.weather.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.aura.weather.ui.components.AuraTopBar
import com.aura.weather.ui.components.BottomNavBar
import com.aura.weather.ui.components.GlassSurface
import com.aura.weather.ui.components.HourlyForecastCard
import com.aura.weather.ui.components.MetricsRow
import com.aura.weather.ui.components.NavTab
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
 *
 * Matches the "Aura Weather" reference design: a glowing liquid-glass hero
 * orb with a soft ambient pulse and gentle float, glass metric/forecast
 * cards, and a floating pill-shaped bottom navigation bar. A light staggered
 * entrance animation is applied to each section on first composition.
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
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 10.dp,
                bottom = 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                EntranceItem(delayMillis = 0) {
                    AuraTopBar(
                        city = uiState.city,
                        region = uiState.region,
                        pageCount = uiState.pageCount,
                        selectedPage = uiState.selectedPage
                    )
                }
            }

            item {
                EntranceItem(delayMillis = 60) {
                    HeroWeatherCard(uiState)
                }
            }

            item {
                EntranceItem(delayMillis = 120) {
                    MetricsRow(
                        humidity = uiState.humidity,
                        wind = uiState.wind,
                        pressure = uiState.pressure,
                        visibility = uiState.visibility
                    )
                }
            }

            item {
                EntranceItem(delayMillis = 180) {
                    HourlyForecastCard(hours = uiState.hourly)
                }
            }

            item {
                EntranceItem(delayMillis = 240) {
                    TenDayForecastCard(days = uiState.daily)
                }
            }
        }

        BottomNavBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .fillMaxWidth()
                .padding(horizontal = 44.dp)
        )
    }
}

/**
 * Fades + slides a section in on first composition, giving the screen a
 * gentle staggered entrance instead of popping in all at once.
 */
@Composable
private fun EntranceItem(
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(420, easing = LinearOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(420, easing = LinearOutSlowInEasing),
                initialOffsetY = { it / 6 }
            )
    ) {
        content()
    }
}

/**
 * The large liquid-glass hero card: soft ambient glow behind the orb, a
 * subtle continuous float, and the temperature/condition stack on top.
 */
@Composable
private fun HeroWeatherCard(uiState: HomeUiState) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroOrb")

    // Gentle vertical float, like the card is drifting.
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // Slow ambient glow pulse behind the orb.
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .graphicsLayer { translationY = floatOffset },
        contentAlignment = Alignment.Center
    ) {
        // Ambient violet/blue glow sitting behind the glass orb, echoing the
        // luminous rim seen in the reference image.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .scale(glowScale)
                .blur(90.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7C6FFF).copy(alpha = glowAlpha),
                            Color(0xFF4C3ED9).copy(alpha = glowAlpha * 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

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
                shape = RoundedCornerShape(50)
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
