package com.pampa.store.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pampa.store.data.StoreEvent
import com.pampa.store.data.StoreEventTone
import com.pampa.store.viewmodel.StoreViewModel
import dev.antigravity.fluidengine.ui.fluid.FluidFoldingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFoldingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.rememberFluidBarFold
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.theme.FluidRouteMotion
import dev.antigravity.fluidengine.ui.theme.FluidRouteMotionHost
import dev.antigravity.fluidengine.ui.theme.FluidScreenSurface
import dev.antigravity.fluidengine.ui.theme.LocalRouteMotionSignals
import dev.antigravity.fluidengine.ui.theme.MotionOrigin
import dev.antigravity.fluidengine.ui.theme.RouteMotionSignals
import dev.antigravity.fluidengine.ui.theme.rememberRouteMotionSignals
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import kotlin.math.roundToInt

private object Routes {
    const val Home = "home"
    const val Catalog = "catalog"
    const val Updates = "updates"
    const val More = "more"
    const val Detail = "detail/{appId}"
    fun detail(appId: String) = "detail/${Uri.encode(appId)}"
}

private val topLevelItems = listOf(
    FluidTabItem(Routes.Home, "Home", Icons.Rounded.Home),
    FluidTabItem(Routes.Catalog, "Catalogo", Icons.Rounded.Apps),
    FluidTabItem(Routes.Updates, "Aggiornamenti", Icons.Rounded.Download),
    FluidTabItem(Routes.More, "Altro", Icons.Rounded.Tune),
)

private val topLevelRouteOrder = topLevelItems.map(FluidTabItem::route)

/**
 * Una rotta chiesta da fuori, se è una che esiste.
 *
 * Il filtro non è difensivo per abitudine: un PendingIntent sopravvive agli aggiornamenti dell'app,
 * quindi una notifica pubblicata da una versione precedente può nominare una scheda che nel
 * frattempo è stata rinominata o tolta.
 */
private fun topLevelRouteFor(route: String): String? = topLevelRouteOrder.firstOrNull { it == route }

internal enum class StoreRouteMotionKind { TopLevelSwitch, Detail }

internal data class StoreRouteMotionDecision(
    val kind: StoreRouteMotionKind,
    val direction: Int = 0,
)

internal fun decideStoreRouteMotion(fromRoute: String?, toRoute: String?): StoreRouteMotionDecision {
    if (fromRoute.isDetailRoute() || toRoute.isDetailRoute()) {
        return StoreRouteMotionDecision(StoreRouteMotionKind.Detail)
    }
    val fromIndex = topLevelRouteOrder.indexOf(fromRoute)
    val toIndex = topLevelRouteOrder.indexOf(toRoute)
    val direction = when {
        fromIndex < 0 || toIndex < 0 -> 0
        toIndex > fromIndex -> 1
        toIndex < fromIndex -> -1
        else -> 0
    }
    return StoreRouteMotionDecision(StoreRouteMotionKind.TopLevelSwitch, direction)
}

private fun String?.isDetailRoute(): Boolean = this?.startsWith("detail/") == true

private fun RouteMotionSignals.resolveStoreRouteMotion(
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry,
): StoreRouteMotionDecision = decideStoreRouteMotion(
    fromRoute = initialState.destination.route,
    toRoute = targetState.destination.route,
).also { hierarchical = it.kind == StoreRouteMotionKind.Detail }

private fun peerSlideSpec() = tween<IntOffset>(
    durationMillis = FluidMotion.DurationPeer,
    easing = FluidMotion.EaseEmphasized,
)

private fun peerSlidePx(width: Int, direction: Int): Int =
    (width * FluidMotion.PeerSlideFraction * direction).roundToInt()

private fun storeRouteEnterTransition(
    decision: StoreRouteMotionDecision,
    isPop: Boolean,
): EnterTransition = when (decision.kind) {
    StoreRouteMotionKind.TopLevelSwitch -> slideInHorizontally(
        animationSpec = peerSlideSpec(),
    ) { width -> peerSlidePx(width, decision.direction) }

    StoreRouteMotionKind.Detail -> scaleIn(
        animationSpec = tween(
            durationMillis = if (isPop) FluidMotion.DurationCollapse else FluidMotion.DurationExpand,
            easing = FluidMotion.EaseEmphasized,
        ),
        initialScale = if (isPop) FluidRouteMotion.ExpandParentScale else FluidRouteMotion.ExpandInitialScale,
        transformOrigin = MotionOrigin.Center.toTransformOrigin(),
    )
}

private fun storeRouteExitTransition(
    decision: StoreRouteMotionDecision,
    isPop: Boolean,
): ExitTransition = when (decision.kind) {
    StoreRouteMotionKind.TopLevelSwitch -> {
        val settle = fadeOut(
            tween(FluidMotion.DurationPeerFadeOut, easing = FluidMotion.EaseIn),
        )
        settle + slideOutHorizontally(
            animationSpec = peerSlideSpec(),
        ) { width -> peerSlidePx(width, -decision.direction) }
    }

    StoreRouteMotionKind.Detail -> fadeOut(
        tween(FluidMotion.DurationRouteFadeOut, easing = FluidMotion.EaseIn),
    ) + scaleOut(
        animationSpec = tween(
            durationMillis = if (isPop) FluidMotion.DurationCollapse else FluidMotion.DurationExpand,
            easing = FluidMotion.EaseEmphasized,
        ),
        targetScale = if (isPop) FluidRouteMotion.ExpandInitialScale else FluidRouteMotion.ExpandParentScale,
        transformOrigin = MotionOrigin.Center.toTransformOrigin(),
    )
}

internal val LocalStoreHorizontalPadding = staticCompositionLocalOf { 20.dp }

/**
 * Il menù che nasce da un tasto, per la schermata che ne ha uno.
 *
 * L'host vive alla radice — è l'unico posto da cui una superficie può uscire *sopra* la barra di
 * navigazione — ma il tasto sta dentro una pagina, e fra i due non c'è una gerarchia di parametri da
 * attraversare. L'engine non espone un CompositionLocal per questo stato, quindi lo tiene l'app.
 */
internal val LocalStoreMorphMenu = staticCompositionLocalOf<FluidMorphMenuState?> { null }

@Composable
fun PampaStoreApp(
    viewModel: StoreViewModel,
    /** Una rotta chiesta da fuori — oggi solo il tocco sulla notifica degli aggiornamenti. */
    pendingRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PampaTheme(preferences = state.preferences) {
        FluidMotionPolicyProvider {
            FluidScreenSurface(modifier = Modifier.fillMaxSize()) {
                val navController = rememberNavController()
                val routeMotionSignals = rememberRouteMotionSignals()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route
                val showNavigation = route != Routes.Detail

                LaunchedEffect(pendingRoute) {
                    val target = pendingRoute?.let(::topLevelRouteFor) ?: return@LaunchedEffect
                    navController.navigate(target) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    onRouteConsumed()
                }

                CompositionLocalProvider(LocalRouteMotionSignals provides routeMotionSignals) {
                    PampaNavigationSuite(
                        currentRoute = route,
                        visible = showNavigation,
                        events = viewModel.events,
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
                            enterTransition = {
                                storeRouteEnterTransition(
                                    routeMotionSignals.resolveStoreRouteMotion(initialState, targetState),
                                    isPop = false,
                                )
                            },
                            exitTransition = {
                                storeRouteExitTransition(
                                    routeMotionSignals.resolveStoreRouteMotion(initialState, targetState),
                                    isPop = false,
                                )
                            },
                            popEnterTransition = {
                                storeRouteEnterTransition(
                                    routeMotionSignals.resolveStoreRouteMotion(initialState, targetState),
                                    isPop = true,
                                )
                            },
                            popExitTransition = {
                                storeRouteExitTransition(
                                    routeMotionSignals.resolveStoreRouteMotion(initialState, targetState),
                                    isPop = true,
                                )
                            },
                        ) {
                        composable(Routes.Home) {
                            FluidRouteMotionHost(this@composable) {
                                HomeScreen(
                                    state = state,
                                    onRefresh = viewModel::refreshCatalog,
                                    onOpenApp = { navController.navigate(Routes.detail(it)) },
                                    onOpenCatalog = { navController.navigate(Routes.Catalog) },
                                    onShowUpdates = { navController.navigate(Routes.Updates) },
                                    onLaunchApp = viewModel::openApp,
                                )
                            }
                        }
                        composable(Routes.Catalog) {
                            FluidRouteMotionHost(this@composable) {
                                CatalogScreen(
                                    state = state,
                                    onRefresh = viewModel::refreshCatalog,
                                    onSearch = viewModel::setSearchQuery,
                                    onCategory = viewModel::setCategory,
                                    onOwnership = viewModel::setOwnershipFilter,
                                    onOpenApp = { navController.navigate(Routes.detail(it)) },
                                    onLaunchApp = viewModel::openApp,
                                )
                            }
                        }
                        composable(Routes.Updates) {
                            FluidRouteMotionHost(this@composable) {
                                UpdatesScreen(
                                    state = state,
                                    onRefresh = viewModel::refreshCatalog,
                                    onUpdateAll = viewModel::updateAll,
                                    onUpdateOne = viewModel::updateOne,
                                    onInstallStoreUpdate = viewModel::installStoreUpdate,
                                    onRetryFailed = viewModel::retryFailed,
                                    onClearQueue = viewModel::clearQueue,
                                    onOpenApp = { navController.navigate(Routes.detail(it)) },
                                    onOpenCatalog = { navController.navigate(Routes.Catalog) },
                                    onOpenInstallSettings = viewModel::openInstallSettings,
                                )
                            }
                        }
                        composable(Routes.More) {
                            FluidRouteMotionHost(this@composable) {
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
                                    onOpenNotificationSettings = viewModel::openNotificationSettings,
                                )
                            }
                        }
                        composable(
                            route = Routes.Detail,
                            arguments = listOf(navArgument("appId") { type = NavType.StringType }),
                        ) { entry ->
                            val appId = entry.arguments?.getString("appId").orEmpty()
                            FluidRouteMotionHost(this@composable) {
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
    }
}

/**
 * Il guscio, nella forma che ha un'app vera.
 *
 * Barra, host dei modali, host del menù che si trasforma e host delle notifiche sono tutti fratelli
 * del contenuto e leggono tutti lo stesso backdrop. È l'unico montaggio in cui un pop-up esce
 * *sopra* la capsula di navigazione, e il motivo per cui serve: un pannello su cui la barra può
 * appoggiarsi non è un modale.
 */
@Composable
private fun PampaNavigationSuite(
    currentRoute: String?,
    visible: Boolean,
    events: Flow<StoreEvent>,
    onSelect: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val controller = rememberFluidChromeController()
    val fallbackBackdrop = rememberGlassBackdrop()
    val scrollToTop = remember { FluidScrollToTopBus() }
    val modalHost = rememberFluidGlassModalHostState()
    val notificationHost = rememberFluidNotificationHostState()
    val morphMenu = rememberFluidMorphMenuState()
    val barFold = rememberFluidBarFold()

    // Cambiare scheda riapre la barra. Restare ripiegati dopo un cambio di rotta lascia la persona
    // su una pagina nuova senza la navigazione che ce l'ha portata: si vede una pastiglia sola,
    // con l'icona della scheda in cui sei, e non c'e' modo di raggiungere le altre senza scorrere.
    LaunchedEffect(currentRoute) { barFold.unfold() }

    // Gli avvisi del ViewModel diventano banner in vetro in cima. La coda del banner e' dell'engine
    // (deduplica per id e li presenta uno alla volta), quindi qui basta consegnare.
    LaunchedEffect(events, notificationHost) {
        events.collect { event ->
            notificationHost.show(
                FluidNotification(
                    id = event.id,
                    title = event.title,
                    message = event.message,
                    tone = when (event.tone) {
                        StoreEventTone.Success -> FluidNotificationTone.Success
                        StoreEventTone.Warning -> FluidNotificationTone.Warning
                        StoreEventTone.Error -> FluidNotificationTone.Error
                        StoreEventTone.Info -> FluidNotificationTone.Info
                    },
                ),
            )
        }
    }

    CompositionLocalProvider(
        LocalFluidGlassModalHostState provides modalHost,
        LocalFluidNotificationHostState provides notificationHost,
        LocalStoreMorphMenu provides morphMenu,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp
            val bottomInset = if (visible && !useRail) FluidFoldingTabBarDefaults.ContentInset else 0.dp
            val screenHorizontalPadding = if (visible && useRail) 108.dp else 20.dp
            val backdrop = controller.activeBackdrop.value ?: fallbackBackdrop

            ProvideFluidChrome(controller = controller, bottomInset = bottomInset, scrollToTop = scrollToTop) {
                CompositionLocalProvider(LocalStoreHorizontalPadding provides screenHorizontalPadding) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Il rail non si ripiega: sul tablet ha una colonna sua e non toglie
                            // spazio al contenuto, quindi non c'e' niente da recuperare scorrendo.
                            .then(if (useRail) Modifier else Modifier.nestedScroll(barFold.connection))
                            .fluidGlassModalObscured(),
                    ) { content() }
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
                        onReselect = { scrollToTop.request() },
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
                        .navigationBarsPadding()
                        .padding(
                            horizontal = FluidFoldingTabBarDefaults.HorizontalMargin,
                            vertical = FluidFoldingTabBarDefaults.BottomMargin,
                        ),
                ) {
                    FluidFoldingTabBar(
                        items = topLevelItems,
                        selectedRoute = currentRoute,
                        onSelect = { onSelect(it.route) },
                        onReselect = { scrollToTop.request() },
                        onExpandRequest = barFold::unfold,
                        backdrop = backdrop,
                        fold = { barFold.progress.value },
                    )
                }
            }

            // Sopra la barra, ed e' esattamente il motivo per cui questi tre host stanno qui alla
            // radice invece che dentro la pagina che li usa.
            FluidGlassModalHost(state = modalHost, backdrop = backdrop)
            FluidMorphMenuHost(state = morphMenu, backdrop = backdrop)
            FluidNotificationHost(
                state = notificationHost,
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
