package com.pampa.store.ui.components

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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@Composable
fun PampaNavigationBar(
    currentRoute: String?,
    isAdminVisible: Boolean,
    onNavigateHome: () -> Unit,
    onNavigateAdmin: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
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
            label = { Text("Store", fontSize = 12.sp) }
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
                label = { Text("Admin", fontSize = 12.sp) }
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
            label = { Text("Impostazioni", fontSize = 12.sp) }
        )
    }
}

@Composable
fun PampaNavigationRail(
    currentRoute: String?,
    isAdminVisible: Boolean,
    onNavigateHome: () -> Unit,
    onNavigateAdmin: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
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
            label = { Text("Store", fontSize = 11.sp) }
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
                label = { Text("Admin", fontSize = 11.sp) }
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
            label = { Text("Impostazioni", fontSize = 11.sp) }
        )
    }
}
