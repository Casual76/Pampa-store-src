package com.pampa.store.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pampa.store.data.StoreEvent
import com.pampa.store.data.StoreEventTone
import com.pampa.store.viewmodel.StoreViewModel
import dev.antigravity.fluidengine.ui.fluid.FluidChromeController
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPeerMove
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidScreenSurface
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

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

/**
 * Di quanto si sposta chi passa da una scheda all'altra, nell'ordine della barra: positivo se
 * [toRoute] sta dopo [fromRoute], negativo se sta prima, zero se non sono due schede (il dettaglio
 * non è una scheda, e un passo laterale verso di lui direbbe «sei tornato» mentre si va avanti).
 *
 * È il `peer` di `FluidNavHost`: due schede fanno il passo laterale, nel verso che la barra implica;
 * tutto il resto è una pagina che si apre da destra e si chiude sotto il dito.
 */
internal fun tabStep(fromRoute: String?, toRoute: String?): Int {
    val from = topLevelRouteOrder.indexOf(fromRoute)
    val to = topLevelRouteOrder.indexOf(toRoute)
    return if (from < 0 || to < 0) 0 else (to - from).coerceIn(-1, 1)
}

private fun String?.isDetailRoute(): Boolean = this == Routes.Detail

/**
 * Passa a un'altra scheda.
 *
 * Com'era con `NavHost`: la pila resta `[Home, scheda]`, e il back da una scheda torna a Home prima
 * di uscire. Si risale fino alla radice (che qui è Home e non esce mai dalla pila, quindi le schede
 * non si accumulano) e si salva lo stato di quelle lasciate, così tornandoci la lista è dov'era.
 */
private fun NavHostController.selectTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

internal val LocalStoreHorizontalPadding = staticCompositionLocalOf { 20.dp }

/** Vero se la pagina sta accanto al rail del tablet e non sopra la barra del telefono. */
internal val LocalStoreUsesRail = staticCompositionLocalOf { false }

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
                val pages = rememberFluidPageHostState()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route

                LaunchedEffect(pendingRoute) {
                    val target = pendingRoute?.let(::topLevelRouteFor) ?: return@LaunchedEffect
                    navController.selectTab(target)
                    onRouteConsumed()
                }

                // Fra due schede la pagina fa il passo laterale nel verso della barra.
                val peer = remember {
                    FluidPeerMove<NavBackStackEntry> { from, to ->
                        tabStep(from.destination.route, to.destination.route)
                    }
                }

                PampaNavigationSuite(
                    currentRoute = route,
                    pages = pages,
                    events = viewModel.events,
                    onSelect = navController::selectTab,
                ) { shell ->
                    FluidNavHost(
                        navController = navController,
                        startDestination = Routes.Home,
                        modifier = Modifier.fillMaxSize(),
                        peer = peer,
                        state = pages,
                    ) {
                        composable(Routes.Home) {
                            TabPage(shell) {
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
                            TabPage(shell) {
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
                            TabPage(shell) {
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
                            TabPage(shell) {
                                MoreScreen(
                                    state = state,
                                    onRefresh = viewModel::refreshCatalog,
                                    onAppearanceChange = { next ->
                                        viewModel.updatePreferences { it.withEngineSettings(next) }
                                    },
                                    onDefaultChannel = viewModel::setDefaultChannel,
                                    onStoreChannel = viewModel::setStoreUpdateChannel,
                                    onAutoCheck = viewModel::setAutoCheckStoreUpdates,
                                    onCheckStoreUpdate = viewModel::checkStoreUpdateNow,
                                    onInstallStoreUpdate = viewModel::installStoreUpdate,
                                    onOpenInstallSettings = viewModel::openInstallSettings,
                                    onOpenNotificationSettings = viewModel::openNotificationSettings,
                                )
                            }
                        }
                        // Il dettaglio non è una scheda: si apre da destra sopra la scheda da cui si
                        // arriva e si chiude col dito. Non lascia lo spazio della barra, che gli
                        // esce di sotto insieme alla pagina.
                        composable(
                            route = Routes.Detail,
                            arguments = listOf(navArgument("appId") { type = NavType.StringType }),
                        ) { entry ->
                            val appId = entry.arguments?.getString("appId").orEmpty()
                            DetailPage(shell) {
                                DetailScreen(
                                    app = state.apps.firstOrNull { it.entry.id == appId },
                                    preferences = state.preferences,
                                    prerequisites = state.prerequisites,
                                    onBack = { navController.popBackStack() },
                                    onInstall = { channel -> viewModel.installApp(appId, channel) },
                                    onInstallVersion = { version ->
                                        viewModel.installVersion(appId, version)
                                    },
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

/** Quello che le pagine prendono dal guscio: il registro della barra, il «torna in cima» e il tipo di navigazione. */
internal class StoreShell(
    val chrome: FluidChromeController,
    val scrollToTop: FluidScrollToTopBus,
    val useRail: Boolean,
)

/**
 * Una scheda: la lista lascia in fondo lo spazio della barra (che la copre), o a sinistra quello del
 * rail. Per pagina e non per guscio: mentre il dettaglio si apre la scheda sotto è ancora composta, e
 * deve tenere i suoi margini finché non è coperta.
 */
@Composable
private fun TabPage(shell: StoreShell, content: @Composable () -> Unit) {
    ProvideFluidChrome(
        controller = shell.chrome,
        bottomInset = if (shell.useRail) 0.dp else FluidFloatingTabBarDefaults.ContentInset,
        scrollToTop = shell.scrollToTop,
    ) {
        CompositionLocalProvider(
            LocalStoreHorizontalPadding provides if (shell.useRail) 108.dp else 20.dp,
            LocalStoreUsesRail provides shell.useRail,
            content = content,
        )
    }
}

/** Il dettaglio: niente barra sotto, niente rail accanto. */
@Composable
private fun DetailPage(shell: StoreShell, content: @Composable () -> Unit) {
    ProvideFluidChrome(
        controller = shell.chrome,
        bottomInset = 0.dp,
        scrollToTop = shell.scrollToTop,
    ) {
        CompositionLocalProvider(
            LocalStoreHorizontalPadding provides 20.dp,
            LocalStoreUsesRail provides false,
            content = content,
        )
    }
}

/**
 * Il guscio, nella forma che ha un'app vera.
 *
 * Barra, host dei modali, host del menù che si trasforma e host delle notifiche sono tutti fratelli
 * del contenuto e leggono tutti lo stesso backdrop. È l'unico montaggio in cui un pop-up esce
 * *sopra* la capsula di navigazione, e il motivo per cui serve: un pannello su cui la barra può
 * appoggiarsi non è un modale.
 *
 * La barra (o il rail) è una sola e sta ferma fuori dall'host delle pagine: esce insieme al
 * dettaglio che si apre e rientra insieme a quello che si chiude, **col dito**, perché legge
 * l'avanzamento dell'host e non un interruttore (vedi [tabBarHidden]).
 */
@Composable
private fun PampaNavigationSuite(
    currentRoute: String?,
    pages: FluidPageHostState,
    events: Flow<StoreEvent>,
    onSelect: (String) -> Unit,
    content: @Composable (StoreShell) -> Unit,
) {
    val controller = rememberFluidChromeController()
    val fallbackBackdrop = rememberGlassBackdrop()
    val scrollToTop = remember { FluidScrollToTopBus() }
    val modalHost = rememberFluidGlassModalHostState()
    val notificationHost = rememberFluidNotificationHostState()
    val morphMenu = rememberFluidMorphMenuState()
    val tabBarScroll = rememberFluidFloatingTabBarScrollConnection()
    val layoutDirection = LocalLayoutDirection.current

    // Prima che navigation abbia una rotta si è su Home; il dettaglio non ha una scheda.
    val tabRoute = if (currentRoute == null) Routes.Home else topLevelRouteFor(currentRoute)
    val onTab = tabRoute != null

    // L'ultima scheda davanti: mentre il dettaglio si apre e la barra se ne va, la barra resta
    // com'era invece di saltare su una scheda che non c'è.
    val lastTab = remember { LastTab() }
    if (tabRoute != null) lastTab.route = tabRoute

    // Cambiare scheda riapre la barra. Restare ripiegati dopo un cambio di rotta lascia la persona
    // su una pagina nuova senza la navigazione che ce l'ha portata: si vede una pastiglia sola,
    // con l'icona della scheda in cui sei, e non c'e' modo di raggiungere le altre senza scorrere.
    // `settle` perché una lista smontata a metà gesto non consegna il suo fling, e senza il flag di
    // scorrimento resta alzato: il vetro rifrangerebbe per sempre l'ultima cattura.
    LaunchedEffect(currentRoute) {
        tabBarScroll.settle()
        tabBarScroll.expand()
    }

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
            val backdrop = controller.activeBackdrop.value ?: fallbackBackdrop
            val shell = remember(controller, scrollToTop, useRail) { StoreShell(controller, scrollToTop, useRail) }
            // 0 al suo posto, 1 fuori dallo schermo: letto quando si posa, mai in composizione.
            val hidden: () -> Float = {
                tabBarHidden(
                    moving = pages.isMoving,
                    fromHidden = (pages.from as? NavBackStackEntry)?.destination?.route.isDetailRoute(),
                    toHidden = (pages.to as? NavBackStackEntry)?.destination?.route.isDetailRoute(),
                    progress = pages.progress,
                    restHidden = !onTab,
                )
            }

            ProvideFluidChrome(controller = controller, bottomInset = 0.dp, scrollToTop = scrollToTop) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Il rail non si ripiega: sul tablet ha una colonna sua e non toglie
                        // spazio al contenuto, quindi non c'e' niente da recuperare scorrendo.
                        .then(if (useRail) Modifier else Modifier.nestedScroll(tabBarScroll))
                        .fluidGlassModalObscured(),
                ) { content(shell) }
            }

            if (onTab || pages.isMoving) {
                if (useRail) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .slideOffStart(layoutDirection, hidden)
                            .systemBarsPadding()
                            .padding(start = 14.dp),
                    ) {
                        FluidTabRail(
                            items = topLevelItems,
                            selectedRoute = lastTab.route,
                            onSelect = { onSelect(it.route) },
                            onReselect = { scrollToTop.request() },
                            backdrop = backdrop,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .slideOffBottom(hidden)
                            .navigationBarsPadding()
                            .padding(
                                horizontal = FluidFloatingTabBarDefaults.HorizontalMargin,
                                vertical = FluidFloatingTabBarDefaults.BottomMargin,
                            ),
                    ) {
                        FluidFloatingTabBar(
                            items = topLevelItems,
                            selectedRoute = lastTab.route,
                            onSelect = { onSelect(it.route) },
                            onReselect = { scrollToTop.request() },
                            scrollConnection = tabBarScroll,
                            backdrop = backdrop,
                        )
                    }
                }
            }

            // Sopra la barra, ed e' esattamente il motivo per cui questi tre host stanno qui
            // alla radice invece che dentro la pagina che li usa.
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

/** Un valore ricordato fuori dallo stato di Compose: si scrive durante la composizione senza riscriverla. */
private class LastTab {
    var route: String = Routes.Home
}

/**
 * Sposta in basso, fuori dallo schermo, di [hidden] volte la propria altezza: 0 è al suo posto, 1 è via.
 * È un offset di posizione, letto quando si posa: il vetro della barra sa dov'è davvero, e non c'è mai
 * un layer traslato sopra una lastra che rifrange.
 */
private fun Modifier.slideOffBottom(hidden: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(0, (placeable.height * hidden().coerceIn(0f, 1f)).roundToInt())
    }
}

/** Lo stesso per il rail: esce dal lato da cui è ancorato (a sinistra, a destra nelle lingue da destra a sinistra). */
private fun Modifier.slideOffStart(direction: LayoutDirection, hidden: () -> Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val sign = if (direction == LayoutDirection.Rtl) 1 else -1
        layout(placeable.width, placeable.height) {
            placeable.place((sign * placeable.width * hidden().coerceIn(0f, 1f)).roundToInt(), 0)
        }
    }
