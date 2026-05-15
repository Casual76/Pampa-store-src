package com.pampa.store.ui

import com.pampa.store.automation.NoOpReleaseAutomationService
import com.pampa.store.automation.ReleaseAutomationService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import com.pampa.store.ui.components.PampaNavigationBar
import com.pampa.store.ui.components.PampaNavigationRail
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.pampa.store.ui.components.SeamlessUpdateBanner
import com.pampa.store.ui.screens.AdminScreen
import com.pampa.store.ui.screens.DetailScreen
import com.pampa.store.ui.screens.HomeScreen
import com.pampa.store.ui.screens.SettingsScreen
import com.pampa.store.ui.theme.PampaStoreTheme
import com.pampa.store.viewmodel.AdminViewModel
import com.pampa.store.viewmodel.StoreViewModel
import com.pampa.store.data.model.SeamlessUpdateState
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

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

    // Ricalcola i ViewModel se le dipendenze critiche (token, repository) cambiano
    // Catturiamo esplicitamente i valori per evitare "Snapshot state" exceptions nei thread in background
    val repoValue = currentStoreRepository
    val tokenValue = currentToken
    val adminServiceValue = currentAdminPublishingService

    val storeViewModel = remember(repoValue, tokenValue) {
        StoreViewModel(
            preferencesRepository = com.pampa.store.data.repository.DefaultPreferencesRepository(localStorage),
            installerProvider = installerProvider,
            platformAppService = platformAppService,
            appVersion = appVersion,
            appRepositoryFactory = { com.pampa.store.data.repository.DefaultAppRepository(GitHubCatalogService(repoValue, token = tokenValue)) }
        ).also { it.init() }
    }
    
    val adminViewModel = remember(adminServiceValue, repoValue, tokenValue) {
        AdminViewModel(
            preferencesRepository = com.pampa.store.data.repository.DefaultPreferencesRepository(localStorage),
            storeRepository = repoValue,
            appRepository = com.pampa.store.data.repository.DefaultAppRepository(GitHubCatalogService(repoValue, token = tokenValue)),
            publishingRepository = com.pampa.store.data.repository.DefaultPublishingRepository(adminServiceValue),
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
    val seamlessUpdateState by storeViewModel.seamlessUpdateState.collectAsState()

    PampaStoreTheme(darkTheme = prefs.darkMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp
                Row(modifier = Modifier.fillMaxSize()) {
                    if (isWideScreen && showBottomBar) {
                        PampaNavigationRail(
                            currentRoute = currentRoute,
                            isAdminVisible = isAdminVisible,
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
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            if (!isWideScreen && showBottomBar) {
                                PampaNavigationBar(
                                    currentRoute = currentRoute,
                                    isAdminVisible = isAdminVisible,
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Seamless update banner
                            AnimatedVisibility(
                                visible = seamlessUpdateState is SeamlessUpdateState.Ready,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                val readyState = seamlessUpdateState as? SeamlessUpdateState.Ready
                                if (readyState != null) {
                                    SeamlessUpdateBanner(
                                        version = readyState.version,
                                        changelog = readyState.changelog,
                                        onInstall = { storeViewModel.installPendingUpdate() },
                                        onDismiss = { storeViewModel.dismissUpdate() }
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
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
