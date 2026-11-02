package com.pampa.store.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.pampa.store.data.StoreUiState
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHero
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMetric
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidLoadingBlock
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    state: StoreUiState,
    onRefresh: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onShowUpdates: () -> Unit,
    onLaunchApp: (String) -> Unit,
) {
    val installed = state.apps.filter { it.installedVersion != null }
    val shownApps = installed.ifEmpty { state.apps.take(4) }
    val updateTitle = if (state.updateCount == 1) "aggiornamento pronto" else "aggiornamenti pronti"
    val heroValue = if (state.updateCount > 0) state.updateCount.toString() else state.installedCount.toString()
    val heroTitle = if (state.updateCount > 0) updateTitle else "app nella raccolta"
    val heroDescription = if (state.updateCount > 0) {
        "Le app che usi sono raccolte qui, con versioni e novità sempre sotto controllo."
    } else {
        "Il tuo catalogo Android è aggiornato. Scopri strumenti, giochi e utility Pampa."
    }

    FluidScreen(
        title = "Pampa Store",
        subtitle = homeSubtitle(state.lastUpdatedAt, state.isUsingCache),
        horizontalPadding = LocalStoreHorizontalPadding.current,
        titleFacets = listOf("${state.apps.size} app", "${state.updateCount} aggiornamenti"),
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        // Il fondale della pagina. Serve perche' il vetro sopra una superficie piatta e' invisibile
        // per costruzione: una lente che rifrange un grigio uniforme produce un grigio uniforme.
        ambient = FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Ripples),
        actions = {
            FluidBarAction(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Aggiorna catalogo",
                onClick = onRefresh,
            )
        },
    ) {
        if (state.isLoading && state.apps.isEmpty()) {
            item { FluidLoadingBlock(modifier = Modifier.testTag("home-loading")) }
        } else {
            item {
                FluidHero(
                    tone = FluidHeroTone.Primary,
                    motif = FluidHeroMotif.Ripples,
                    eyebrow = "La tua raccolta",
                    value = heroValue,
                    title = heroTitle,
                    description = heroDescription,
                    icon = Icons.Rounded.GridView,
                    metrics = listOf(
                        FluidHeroMetric("App disponibili", state.apps.size.toString(), onOpenCatalog),
                        FluidHeroMetric("Installate", state.installedCount.toString(), onOpenCatalog),
                        FluidHeroMetric("Canale beta", state.betaCount.toString(), onOpenCatalog),
                    ),
                    actionLabel = if (state.updateCount > 0) "Mostra aggiornamenti" else "Apri il catalogo",
                    onAction = if (state.updateCount > 0) onShowUpdates else onOpenCatalog,
                    modifier = Modifier.testTag("home-hero"),
                )
            }

            state.storeUpdate?.let { update ->
                item {
                    FluidInlineMessage(
                        title = "Pampa Store ${update.version.version} è disponibile",
                        message = update.version.changelog.ifBlank { "Nuova versione pronta da installare." },
                        tone = FluidTone.Primary,
                        onClick = onShowUpdates,
                        leading = { Icon(noticeIcon(FluidTone.Primary), contentDescription = null) },
                    )
                }
            }

            state.error?.let { message ->
                item {
                    FluidInlineMessage(
                        title = "Catalogo non disponibile",
                        message = message,
                        tone = FluidTone.Danger,
                        onClick = onRefresh,
                        leading = { Icon(noticeIcon(FluidTone.Danger), contentDescription = null) },
                    )
                }
            }
            state.warning?.takeIf { state.isUsingCache }?.let { message ->
                item {
                    FluidInlineMessage(
                        title = "Modalità offline",
                        message = message,
                        tone = FluidTone.Warning,
                        onClick = onRefresh,
                        leading = { Icon(noticeIcon(FluidTone.Warning), contentDescription = null) },
                    )
                }
            }

            if (shownApps.isNotEmpty()) {
                item {
                    FluidSectionHeader(
                        title = if (installed.isEmpty()) "Da scoprire" else "Le tue app",
                        detail = when {
                            installed.isEmpty() -> "Una selezione per iniziare"
                            state.updateCount > 0 -> "${state.updateCount} da aggiornare"
                            else -> "Installate su questo dispositivo"
                        },
                    )
                }
                item {
                    StoreAppGroup(
                        apps = shownApps,
                        onOpenApp = onOpenApp,
                        onLaunchApp = onLaunchApp,
                    )
                }
            }
        }
    }
}

private val homeDateFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

private fun homeSubtitle(timestamp: Long?, usingCache: Boolean): String {
    if (timestamp == null) return "La tua raccolta Android, tutta qui."
    val age = System.currentTimeMillis() - timestamp
    val prefix = if (usingCache) "Ultimo catalogo" else "Aggiornato"
    if (!usingCache && age in 0..59_999) return "Aggiornato adesso"
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(homeDateFormatter)
    return "$prefix $date"
}
