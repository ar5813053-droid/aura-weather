package com.aura.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aura.weather.ui.theme.AuraColors

enum class NavTab { HOME, MAP, ALERTS, SETTINGS }

/**
 * Home / Map / Alerts / Settings pill nav. Home, Notifications, and
 * Settings come from the default (core) Material icon set that ships
 * without any extra dependency. Map does NOT exist in that core set --
 * it's only in material-icons-extended -- so it's drawn locally via
 * MapGlyph (GlyphIcons.kt) instead of adding that dependency.
 */
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
            NavIconButton(tab = NavTab.HOME, selected = selected, onSelect = onSelect) { tint ->
                Icon(imageVector = Icons.Filled.Home, contentDescription = NavTab.HOME.name, tint = tint)
            }
            NavIconButton(tab = NavTab.MAP, selected = selected, onSelect = onSelect) { tint ->
                MapGlyph(tint = tint, size = 22.dp)
            }
            NavIconButton(tab = NavTab.ALERTS, selected = selected, onSelect = onSelect) { tint ->
                Icon(imageVector = Icons.Filled.Notifications, contentDescription = NavTab.ALERTS.name, tint = tint)
            }
            NavIconButton(tab = NavTab.SETTINGS, selected = selected, onSelect = onSelect) { tint ->
                Icon(imageVector = Icons.Filled.Settings, contentDescription = NavTab.SETTINGS.name, tint = tint)
            }
        }
    }
}

@Composable
private fun NavIconButton(
    tab: NavTab,
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    content: @Composable (tint: Color) -> Unit
) {
    val isSelected = tab == selected
    val tint = if (isSelected) AuraColors.TextPrimary else AuraColors.TextTertiary
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) AuraColors.GlassFillStrong else Color.Transparent)
            .clickable { onSelect(tab) }
            .padding(10.dp)
            .size(22.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        content(tint)
    }
}