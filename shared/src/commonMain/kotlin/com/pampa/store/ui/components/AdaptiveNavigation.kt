package com.pampa.store.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pampa.store.ui.theme.AccentOrange
import com.pampa.store.ui.theme.GlassStrokeDark
import com.pampa.store.ui.theme.GlassStrokeLight
import com.pampa.store.ui.theme.PrimaryViolet
import com.pampa.store.ui.theme.SecondaryTeal

@Composable
fun PampaNavigationBar(
    currentRoute: String?,
    isAdminVisible: Boolean,
    isDarkTheme: Boolean,
    onNavigateHome: () -> Unit,
    onNavigateAdmin: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.92f else 0.94f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.82f else 0.88f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (isDarkTheme) GlassStrokeDark else GlassStrokeLight,
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        NavigationBar(
            containerColor = Color.Transparent
        ) {
            NavigationBarItem(
                selected = currentRoute == "home",
                onClick = onNavigateHome,
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "home") Icons.Filled.Store else Icons.Outlined.Storefront,
                        contentDescription = "Store"
                    )
                },
                label = { Text("Store", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryViolet,
                    selectedTextColor = PrimaryViolet,
                    indicatorColor = PrimaryViolet.copy(alpha = 0.16f)
                )
            )

            if (isAdminVisible) {
                NavigationBarItem(
                    selected = currentRoute == "admin",
                    onClick = onNavigateAdmin,
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == "admin") Icons.Filled.AdminPanelSettings else Icons.Outlined.AdminPanelSettings,
                            contentDescription = "Admin"
                        )
                    },
                    label = { Text("Admin", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentOrange,
                        selectedTextColor = AccentOrange,
                        indicatorColor = AccentOrange.copy(alpha = 0.16f)
                    )
                )
            }

            NavigationBarItem(
                selected = currentRoute == "settings",
                onClick = onNavigateSettings,
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                        contentDescription = "Impostazioni"
                    )
                },
                label = { Text("Impostazioni", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SecondaryTeal,
                    selectedTextColor = SecondaryTeal,
                    indicatorColor = SecondaryTeal.copy(alpha = 0.16f)
                )
            )
        }
    }
}

@Composable
fun PampaNavigationRail(
    currentRoute: String?,
    isAdminVisible: Boolean,
    isDarkTheme: Boolean,
    onNavigateHome: () -> Unit,
    onNavigateAdmin: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 0.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.92f else 0.94f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.82f else 0.88f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (isDarkTheme) GlassStrokeDark else GlassStrokeLight,
                shape = RoundedCornerShape(32.dp)
            )
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        NavigationRail(
            containerColor = Color.Transparent
        ) {
            NavigationRailItem(
                selected = currentRoute == "home",
                onClick = onNavigateHome,
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "home") Icons.Filled.Store else Icons.Outlined.Storefront,
                        contentDescription = "Store"
                    )
                },
                label = { Text("Store", fontSize = 11.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = PrimaryViolet,
                    selectedTextColor = PrimaryViolet,
                    indicatorColor = PrimaryViolet.copy(alpha = 0.16f)
                )
            )

            if (isAdminVisible) {
                NavigationRailItem(
                    selected = currentRoute == "admin",
                    onClick = onNavigateAdmin,
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == "admin") Icons.Filled.AdminPanelSettings else Icons.Outlined.AdminPanelSettings,
                            contentDescription = "Admin"
                        )
                    },
                    label = { Text("Admin", fontSize = 11.sp) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = AccentOrange,
                        selectedTextColor = AccentOrange,
                        indicatorColor = AccentOrange.copy(alpha = 0.16f)
                    )
                )
            }

            NavigationRailItem(
                selected = currentRoute == "settings",
                onClick = onNavigateSettings,
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                        contentDescription = "Impostazioni"
                    )
                },
                label = { Text("Impostazioni", fontSize = 11.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = SecondaryTeal,
                    selectedTextColor = SecondaryTeal,
                    indicatorColor = SecondaryTeal.copy(alpha = 0.16f)
                )
            )
        }
    }
}
