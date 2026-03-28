package com.pampa.store.ui

import com.pampa.store.automation.NoOpReleaseAutomationService
import com.pampa.store.automation.ReleaseAutomationService
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pampa.store.data.api.AdminPublishingService
import com.pampa.store.data.api.GitHubCatalogService
import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.data.model.PendingReleaseDraft
import com.pampa.store.platform.InstallerService
import com.pampa.store.platform.PlatformAppService
import com.pampa.store.ui.components.EmptyState
import com.pampa.store.ui.screens.AdminScreen
import com.pampa.store.ui.screens.DetailScreen
import com.pampa.store.ui.screens.HomeScreen
import com.pampa.store.ui.screens.SettingsScreen
import com.pampa.store.ui.theme.GlassStrokeDark
import com.pampa.store.ui.theme.GlassStrokeLight
import com.pampa.store.ui.theme.AccentOrange
import com.pampa.store.ui.theme.PampaStoreTheme
import com.pampa.store.ui.theme.PrimaryViolet
import com.pampa.store.ui.theme.SecondaryTeal
import com.pampa.store.viewmodel.AdminViewModel
import com.pampa.store.viewmodel.StoreViewModel

private object Routes {
    const val Home = "home"
    const val Settings = "settings"
    const val Admin = "admin"
    const val Detail = "detail/{appId}"

    fun detail(appId: String): String = "detail/$appId"
}

@Composable
fun PampaStoreApp(
    localStorage: LocalStorage,
    installerProvider: () -> InstallerService,
    platformAppService: PlatformAppService,
    adminPublishingService: AdminPublishingService,
    releaseAutomationService: ReleaseAutomationService = NoOpReleaseAutomationService,
    storeRepository: GitHubRepositoryRef,
    appVersion: String,
    adminPanelBuildEnabled: Boolean = false,
    catalogGitHubToken: String = ""
) {
    val prefs by localStorage.preferences.collectAsState()
    val navController = rememberNavController()
    val isAdminVisible = adminPanelBuildEnabled || prefs.adminPanelEnabled
    val isPublishingEnabled = adminPublishingService.isConfigured
    val adminDisabledReason = when {
        isPublishingEnabled -> null
        !isAdminVisible -> "Strumenti admin nascosti. Attivali dalle Impostazioni per configurare l'automazione da questa installazione."
        else -> adminPublishingService.disabledReason
            ?: "Publishing non configurato. Fornisci credenziali GitHub valide."
    }

    val storeViewModel = remember {
        StoreViewModel(
            localStorage = localStorage,
            installerProvider = installerProvider,
            platformAppService = platformAppService,
            appVersion = appVersion,
            catalogServiceFactory = { GitHubCatalogService(storeRepository, token = catalogGitHubToken) }
        ).also { it.init() }
    }
    val adminViewModel = remember {
        AdminViewModel(
            localStorage = localStorage,
            storeRepository = storeRepository,
            publishingService = adminPublishingService,
            releaseAutomationService = releaseAutomationService
        ).also { it.init() }
    }

    DisposableEffect(Unit) {
        onDispose {
            storeViewModel.dispose()
            adminViewModel.dispose()
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = currentRoute != Routes.Detail
    val activePromptDraft by adminViewModel.activePromptDraft.collectAsState()

    PampaStoreTheme(darkTheme = prefs.darkMode) {
        val isDarkTheme = prefs.darkMode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.92f else 0.45f)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                AccentOrange.copy(alpha = if (isDarkTheme) 0.2f else 0.12f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(180f, 120f),
                            radius = 760f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                PrimaryViolet.copy(alpha = if (isDarkTheme) 0.22f else 0.13f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(1180f, 360f),
                            radius = 1120f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                SecondaryTeal.copy(alpha = if (isDarkTheme) 0.18f else 0.1f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(1040f, 2080f),
                            radius = 1180f
                        )
                    )
            )

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    if (showBottomBar) {
                        Box(
                            modifier = Modifier
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
                                    color = if (isDarkTheme) {
                                        GlassStrokeDark
                                    } else {
                                        GlassStrokeLight
                                    },
                                    shape = RoundedCornerShape(32.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == Routes.Home,
                                    onClick = {
                                        navController.navigate(Routes.Home) {
                                            launchSingleTop = true
                                            restoreState = true
                                            popUpTo(Routes.Home) { saveState = true }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == Routes.Home) Icons.Filled.Store else Icons.Outlined.Storefront,
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
                                        selected = currentRoute == Routes.Admin,
                                        onClick = {
                                            navController.navigate(Routes.Admin) {
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (currentRoute == Routes.Admin) {
                                                    Icons.Filled.AdminPanelSettings
                                                } else {
                                                    Icons.Outlined.AdminPanelSettings
                                                },
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
                                    selected = currentRoute == Routes.Settings,
                                    onClick = {
                                        navController.navigate(Routes.Settings) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == Routes.Settings) Icons.Filled.Settings else Icons.Outlined.Settings,
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
                }
            ) { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.Home,
                    modifier = Modifier
                ) {
                    composable(Routes.Home) {
                        HomeScreen(
                            viewModel = storeViewModel,
                            onAppClicked = { app ->
                                navController.navigate(Routes.detail(app.id))
                            },
                            contentPadding = paddingValues
                        )
                    }

                    composable(
                        route = Routes.Detail,
                        arguments = listOf(navArgument("appId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val appId = backStackEntry.arguments?.getString("appId").orEmpty()
                        DetailScreen(
                            appId = appId,
                            viewModel = storeViewModel,
                            onBack = { navController.navigateUp() },
                            contentPadding = paddingValues
                        )
                    }

                    composable(Routes.Settings) {
                        SettingsScreen(
                            viewModel = storeViewModel,
                            localStorage = localStorage,
                            appVersion = appVersion,
                            adminPanelVisible = isAdminVisible,
                            adminPanelBuildEnabled = adminPanelBuildEnabled,
                            adminPublishingEnabled = isPublishingEnabled,
                            adminDisabledReason = adminDisabledReason,
                            onBack = { navController.navigateUp() },
                            onConfigSaved = { storeViewModel.onConfigUpdated() },
                            contentPadding = paddingValues
                        )
                    }

                    composable(Routes.Admin) {
                        if (isAdminVisible) {
                            AdminScreen(
                                viewModel = adminViewModel,
                                onBack = { navController.navigateUp() },
                                contentPadding = paddingValues
                            )
                        } else {
                            AdminLockedScreen(
                                contentPadding = paddingValues,
                                reason = adminDisabledReason
                                    ?: "Pannello admin non disponibile in questa configurazione.",
                                onOpenSettings = {
                                    navController.navigate(Routes.Settings) {
                                        popUpTo(Routes.Admin) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            activePromptDraft?.let { draft ->
                AutomationPromptDialog(
                    draft = draft,
                    onPublishStable = { adminViewModel.publishAutomationDraft(draft.id, Channel.STABLE) },
                    onPublishBeta = { adminViewModel.publishAutomationDraft(draft.id, Channel.BETA) },
                    onLater = { adminViewModel.deferAutomationDraft(draft.id) }
                )
            }
        }
    }
}

@Composable
private fun AdminLockedScreen(
    contentPadding: PaddingValues,
    reason: String,
    onOpenSettings: () -> Unit
) {
    EmptyState(
        title = "Pannello admin nascosto",
        subtitle = reason,
        modifier = Modifier.padding(contentPadding),
        action = {
            Button(onClick = onOpenSettings) {
                Text("Apri impostazioni")
            }
        }
    )
}

@Composable
private fun AutomationPromptDialog(
    draft: PendingReleaseDraft,
    onPublishStable: () -> Unit,
    onPublishBeta: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Release pronta") },
        text = {
            Text(
                "${draft.profileName} v${draft.version}\n${draft.changelog}"
            )
        },
        confirmButton = {
            Button(onClick = onPublishStable) {
                Text("Pubblica Stable")
            }
        },
        dismissButton = {
            Box {
                RowActions(
                    onPublishBeta = onPublishBeta,
                    onLater = onLater
                )
            }
        }
    )
}

@Composable
private fun RowActions(
    onPublishBeta: () -> Unit,
    onLater: () -> Unit
) {
    androidx.compose.foundation.layout.Row {
        TextButton(onClick = onPublishBeta) {
            Text("Pubblica Beta")
        }
        TextButton(onClick = onLater) {
            Text("Più tardi")
        }
    }
}
