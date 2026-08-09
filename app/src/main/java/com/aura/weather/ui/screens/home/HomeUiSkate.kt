package com.aura.weather.ui.screens.home

import com.aura.weather.ui.components.DailyForecast
import com.aura.weather.ui.components.HourlyPoint
import com.aura.weather.ui.weather.WeatherVisualState

/**
 * Phase 3 UI state. No networking yet -- this is a plain, screen-shaped
 * model populated with sample data so the layout can be built and verified
 * against the reference design. A later phase will replace
 * [sampleHomeUiState] with a ViewModel that maps real API responses onto
 * this same shape (and onto [WeatherVisualState] for the orb/icons).
 */
data class HomeUiState(
    val city: String,
    val region: String,
    val currentTempC: Int,
    val condition: String,
    val feelsLikeC: Int,
    val highC: Int,
    val lowC: Int,
    val visualState: WeatherVisualState,
    val humidity: String,
    val wind: String,
    val pressure: String,
    val visibility: String,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyForecast>,
    val pageCount: Int = 5,
    val selectedPage: Int = 0
)

fun sampleHomeUiState(): HomeUiState = HomeUiState(
    city = "San Francisco",
    region = "California, USA",
    currentTempC = 23,
    condition = "Partly Cloudy",
    feelsLikeC = 25,
    highC = 27,
    lowC = 18,
    visualState = WeatherVisualState.PARTLY_CLOUDY,
    humidity = "65%",
    wind = "12 km/h",
    pressure = "1013 hPa",
    visibility = "10 km",
    hourly = listOf(
        HourlyPoint("Now", 23, WeatherVisualState.PARTLY_CLOUDY, isNow = true),
        HourlyPoint("11AM", 24, WeatherVisualState.CLEAR),
        HourlyPoint("12PM", 25, WeatherVisualState.CLEAR),
        HourlyPoint("1PM", 26, WeatherVisualState.PARTLY_CLOUDY),
        HourlyPoint("2PM", 26, WeatherVisualState.PARTLY_CLOUDY),
        HourlyPoint("3PM", 25, WeatherVisualState.PARTLY_CLOUDY)
    ),
    daily = listOf(
        DailyForecast("Today", WeatherVisualState.PARTLY_CLOUDY, lowC = 18, highC = 27, rangeMinC = 15, rangeMaxC = 30),
        DailyForecast("Sat", WeatherVisualState.RAIN, lowC = 17, highC = 24, rangeMinC = 15, rangeMaxC = 30),
        DailyForecast("Sun", WeatherVisualState.CLEAR, lowC = 16, highC = 25, rangeMinC = 15, rangeMaxC = 30),
        DailyForecast("Mon", WeatherVisualState.CLOUDY, lowC = 17, highC = 26, rangeMinC = 15, rangeMaxC = 30),
        DailyForecast("Tue", WeatherVisualState.PARTLY_CLOUDY, lowC = 18, highC = 27, rangeMinC = 15, rangeMaxC = 30)
    )
)

