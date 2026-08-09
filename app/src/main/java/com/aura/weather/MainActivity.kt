package com.aura.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aura.weather.ui.screens.home.HomeScreen
import com.aura.weather.ui.theme.AuraWeatherTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AuraWeatherTheme {
                HomeScreen()
            }
        }
    }
}