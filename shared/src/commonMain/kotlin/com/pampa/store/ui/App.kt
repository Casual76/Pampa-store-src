package com.pampa.store.ui

import com.pampa.store.automation.NoOpReleaseAutomationService
import com.pampa.store.automation.ReleaseAutomationService
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import com.pampa.store.ui.components.PampaNavigationBar
import com.pampa.store.ui.components.PampaNavigationRail
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
import com.pampa.store.data.api.GitHubAdminConfig
import com.pampa.store.data.api.GitHubAdminPublishingService
import com.pampa.store.data.api.DisabledAdminPublishingService
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    
    // Ricalcola il servizio di publishing in base alle preferenze locali o di sistema
    val currentAdminPublishingService by remember(prefs.githubToken, prefs.repoOwner, prefs.repoName) {
        derivedStateOf {
            val token = prefs.githubToken.ifBlank { catalogGitHubToken }
            val repo = if (prefs.repoOwner.isNotBlank() && prefs.repoName.isNotBlank()) {
                GitHubRepositoryRef(prefs.repoOwner, prefs.repoName, storeRepository.manifestPath)
            } else {
                storeRepository
            }
            
            if (token.isNotBlank()) {
                GitHubAdminPublishingService(GitHubAdminConfig(repo, token))
            } else {
                adminPublishingService
            }
        }
    }

    val currentStoreRepository by remember(prefs.repoOwner, prefs.repoName) {
        derivedStateOf {
            if (prefs.repoOwner.isNotBlank() && prefs.repoName.isNotBlank()) {
                GitHubRepositoryRef(prefs.repoOwner, prefs.repoName, storeRepository.manifestPath)
            } else {
                storeRepository
            }
        }
    }

    val currentToken by remember(prefs.githubToken) {
        derivedStateOf { prefs.githubToken.ifBlank { catalogGitHubToken } }
    }

    val navController = rememberNavController()
    val isAdminVisible = adminPanelBuildEnabled || prefs.adminPanelEnabled
    val isPublishingEnabled = currentAdminPublishingService.isConfigured
    val adminDisabledReason = when {
        isPublishingEnabled -> null
        !isAdminVisible -> "Strumenti admin nascosti. Attivali dalle Impostazioni per configurare l'automazione da questa installazione."
        else -> currentAdminPublishingService.disabledReason
            ?: "Publishing non configurato. Fornisci credenziali GitHub valide."
    }

    val storeViewModel = remember {
        StoreViewModel(
            preferencesRepository = com.pampa.store.data.repository.DefaultPreferencesRepository(localStorage),
            installerProvider = installerProvider,
            platformAppService = platformAppService,
            appVersion = appVersion,
            appRepositoryFactory = { com.pampa.store.data.repository.DefaultAppRepository(GitHubCatalogService(currentStoreRepository, token = currentToken)) }
        ).also { it.init() }
    }
    
    val adminViewModel = remember {
        AdminViewModel(
            preferencesRepository = com.pampa.store.data.repository.DefaultPreferencesRepository(localStorage),
            storeRepository = currentStoreRepository,
            appRepository = com.pampa.store.data.repository.DefaultAppRepository(GitHubCatalogService(currentStoreRepository, token = currentToken)),
            publishingRepository = com.pampa.store.data.repository.DefaultPublishingRepository(currentAdminPublishingService),
            releaseAutomationService = releaseAutomationService
        ).also { it.init() }
    }
    
    // Aggiorna i ViewModel quando cambiano i servizi (configurazione dinamica)
    LaunchedEffect(currentAdminPublishingService, currentStoreRepository, currentToken) {
        // Qui potremmo chiamare dei metodi di refresh sui ViewModel se necessario
        // Per ora i ViewModel useranno i nuovi repository nelle loro prossime chiamate
        // se passiamo delle factory o reference aggiornabili.
        // Dato che i ViewModel nel codice attuale ricevono istanze fisse, 
        // forziamo un refresh delle dipendenze interne se possibile.
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

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp
                Row(modifier = Modifier.fillMaxSize()) {
                    if (isWideScreen && showBottomBar) {
                        PampaNavigationRail(
                            currentRoute = currentRoute,
                            isAdminVisible = isAdminVisible,
                            isDarkTheme = isDarkTheme,
                            onNavigateHome = {
                                navController.navigate(Routes.Home) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Routes.Home) { saveState = true }
                                }
                            },
                            onNavigateAdmin = {
                                navController.navigate(Routes.Admin) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateSettings = {
                                navController.navigate(Routes.Settings) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        containerColor = Color.Transparent,
                        bottomBar = {
                            if (!isWideScreen && showBottomBar) {
                                PampaNavigationBar(
                                    currentRoute = currentRoute,
                                    isAdminVisible = isAdminVisible,
                                    isDarkTheme = isDarkTheme,
                                    onNavigateHome = {
                                        navController.navigate(Routes.Home) {
                                            launchSingleTop = true
                                            restoreState = true
                                            popUpTo(Routes.Home) { saveState = true }
                                        }
                                    },
                                    onNavigateAdmin = {
                                        navController.navigate(Routes.Admin) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateSettings = {
                                        navController.navigate(Routes.Settings) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
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
    Row {
        TextButton(onClick = onPublishBeta) {
            Text("Pubblica Beta")
        }
        TextButton(onClick = onLater) {
            Text("Più tardi")
        }
    }
}
