package com.aura.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aura.weather.ui.theme.AuraColors

enum class NavTab { HOME, MAP, ALERTS, SETTINGS }

@Composable
fun BottomNavBar(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPill(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavIcon(Icons.Filled.Home, NavTab.HOME, selected, onSelect)
            NavIcon(Icons.Filled.Map, NavTab.MAP, selected, onSelect)
            NavIcon(Icons.Filled.Notifications, NavTab.ALERTS, selected, onSelect)
            NavIcon(Icons.Filled.Settings, NavTab.SETTINGS, selected, onSelect)
        }
    }
}

@Composable
private fun NavIcon(
    icon: ImageVector,
    tab: NavTab,
    selected: NavTab,
    onSelect: (NavTab) -> Unit
) {
    val isSelected = tab == selected
    Icon(
        imageVector = icon,
        contentDescription = tab.name,
        tint = if (isSelected) AuraColors.TextPrimary else AuraColors.TextTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) AuraColors.GlassFillStrong else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onSelect(tab) }
            .padding(10.dp)
            .size(22.dp)
    )
}

