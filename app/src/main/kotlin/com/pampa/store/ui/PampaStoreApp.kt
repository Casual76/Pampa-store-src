package com.pampa.store.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pampa.store.data.OwnershipFilter
import com.pampa.store.viewmodel.StoreViewModel
import dev.antigravity.fluidengine.ui.fluid.FluidTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidScreenSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune

private object Routes {
    const val Home = "home"
    const val Catalog = "catalog"
    const val More = "more"
    const val Detail = "detail/{appId}"
    fun detail(appId: String) = "detail/${Uri.encode(appId)}"
}

private val topLevelItems = listOf(
    FluidTabItem(Routes.Home, "Home", Icons.Rounded.Home),
    FluidTabItem(Routes.Catalog, "Catalogo", Icons.Rounded.Apps),
    FluidTabItem(Routes.More, "Altro", Icons.Rounded.Tune),
)

internal val LocalStoreHorizontalPadding = staticCompositionLocalOf { 20.dp }

@Composable
fun PampaStoreApp(viewModel: StoreViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PampaTheme(preferences = state.preferences) {
        FluidMotionPolicyProvider {
            FluidScreenSurface(modifier = Modifier.fillMaxSize()) {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route
                val showNavigation = route != Routes.Detail

                PampaNavigationSuite(
                    currentRoute = route,
                    visible = showNavigation,
                    onSelect = { target ->
                        navController.navigate(target) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.Home,
                        modifier = Modifier.fillMaxSize().clipToBounds(),
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() },
                        popExitTransition = { fadeOut() },
                    ) {
                        composable(Routes.Home) {
                            HomeScreen(
                                state = state,
                                onRefresh = viewModel::refreshCatalog,
                                onOpenApp = { navController.navigate(Routes.detail(it)) },
                                onOpenCatalog = { navController.navigate(Routes.Catalog) },
                                onShowUpdates = {
                                    viewModel.setOwnershipFilter(OwnershipFilter.UPDATABLE)
                                    navController.navigate(Routes.Catalog)
                                },
                            )
                        }
                        composable(Routes.Catalog) {
                            CatalogScreen(
                                state = state,
                                onRefresh = viewModel::refreshCatalog,
                                onSearch = viewModel::setSearchQuery,
                                onCategory = viewModel::setCategory,
                                onOwnership = viewModel::setOwnershipFilter,
                                onOpenApp = { navController.navigate(Routes.detail(it)) },
                            )
                        }
                        composable(Routes.More) {
                            MoreScreen(
                                state = state,
                                onRefresh = viewModel::refreshCatalog,
                                onThemeMode = viewModel::setThemeMode,
                                onAccentSource = viewModel::setAccentSource,
                                onAccentPreset = viewModel::setAccentPreset,
                                onDefaultChannel = viewModel::setDefaultChannel,
                                onStoreChannel = viewModel::setStoreUpdateChannel,
                                onAutoCheck = viewModel::setAutoCheckStoreUpdates,
                                onOpenInstallSettings = viewModel::openInstallSettings,
                                onInstallStoreUpdate = viewModel::installStoreUpdate,
                            )
                        }
                        composable(
                            route = Routes.Detail,
                            arguments = listOf(navArgument("appId") { type = NavType.StringType }),
                        ) { entry ->
                            val appId = entry.arguments?.getString("appId").orEmpty()
                            DetailScreen(
                                app = state.apps.firstOrNull { it.entry.id == appId },
                                preferences = state.preferences,
                                prerequisites = state.prerequisites,
                                onBack = navController::navigateUp,
                                onInstall = { channel -> viewModel.installApp(appId, channel) },
                                onOpen = viewModel::openApp,
                                onOpenInstallSettings = viewModel::openInstallSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PampaNavigationSuite(
    currentRoute: String?,
    visible: Boolean,
    onSelect: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val controller = rememberFluidChromeController()
    val fallbackBackdrop = rememberGlassBackdrop()
    val scrollToTop = remember { FluidScrollToTopBus() }
    val bottomTravel = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        FluidTabBarDefaults.Height + FluidTabBarDefaults.BottomMargin
    val bottomTravelPx = with(LocalDensity.current) { bottomTravel.toPx() }
    LaunchedEffect(controller, bottomTravelPx) { controller.updateBottomBarTravel(bottomTravelPx) }
    val scrollConnection = rememberFluidChromeScrollConnection(controller, visible)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
    ) {
        val useRail = maxWidth >= 600.dp
        val bottomInset = if (visible && !useRail) FluidTabBarDefaults.ContentInset else 0.dp
        val screenHorizontalPadding = if (visible && useRail) 108.dp else 20.dp
        val backdrop = controller.activeBackdrop.value ?: fallbackBackdrop

        ProvideFluidChrome(controller = controller, bottomInset = bottomInset, scrollToTop = scrollToTop) {
            CompositionLocalProvider(LocalStoreHorizontalPadding provides screenHorizontalPadding) {
                Box(modifier = Modifier.fillMaxSize()) { content() }
            }
        }

        if (useRail) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
                modifier = Modifier.align(Alignment.CenterStart).systemBarsPadding().padding(start = 14.dp),
            ) {
                FluidTabRail(
                    items = topLevelItems,
                    selectedRoute = currentRoute,
                    onSelect = { onSelect(it.route) },
                    backdrop = backdrop,
                )
            }
        } else {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { translationY = controller.bottomBarOffsetPx.value }
                    .navigationBarsPadding()
                    .padding(
                        horizontal = FluidTabBarDefaults.HorizontalMargin,
                        vertical = FluidTabBarDefaults.BottomMargin,
                    ),
            ) {
                FluidTabBar(
                    items = topLevelItems,
                    selectedRoute = currentRoute,
                    onSelect = { onSelect(it.route) },
                    backdrop = backdrop,
                )
            }
        }
    }
}
