package com.pampa.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pampa.store.data.Channel
import com.pampa.store.data.DownloadState
import com.pampa.store.data.InstallPrerequisites
import com.pampa.store.data.StoreApp
import com.pampa.store.data.StorePreferences
import com.pampa.store.data.VersionInfo
import com.pampa.store.data.enumOrDefault
import com.pampa.store.data.versionFor
import com.pampa.store.util.formatBytes
import com.pampa.store.data.requiresUninstallFirst
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.fluid.FluidHero
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMetric
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidProgressBar
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.LocalFluidItemSpacing
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidGroupOpenEdge
import dev.antigravity.fluidengine.ui.theme.FluidGroupSegment
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

@Composable
fun DetailScreen(
    app: StoreApp?,
    preferences: StorePreferences,
    prerequisites: InstallPrerequisites,
    onBack: () -> Unit,
    onInstall: (Channel) -> Unit,
    onInstallVersion: (VersionInfo) -> Unit,
    onOpen: (String) -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    if (app == null) {
        FluidScreen(title = "App non trovata", onBack = onBack) {
            item { FluidEmptyState("Contenuto non disponibile", "Torna al catalogo e aggiorna i dati.") }
        }
        return
    }

    // L'indice, non la stringa di versione: la cronologia di ClasseViva ha due 4.1.4 e due 4.0.0, e
    // con la versione come chiave i due gemelli si sovrascrivevano il rettangolo a vicenda, si
    // nascondevano insieme, e il pannello mostrava sempre il primo dei due.
    var historyPick by rememberSaveable(app.entry.id) { mutableIntStateOf(-1) }
    val pickedVersion = app.entry.history.getOrNull(historyPick)
    val historyOrigins = remember(app.entry.id) { mutableMapOf<Int, Rect>() }

    // Dove torna il pannello quando si chiude.
    //
    // Snapshot state, e soprattutto **non azzerato** alla chiusura: l'host legge `origin` mentre
    // l'uscita e' in corso, e una lambda che smette di sapere da dove era partita fa collassare il
    // pannello al centro dello schermo invece che dentro la riga che l'aveva aperto.
    var pickedOrigin by remember(app.entry.id) { mutableStateOf<Rect?>(null) }

    val defaultChannel = enumOrDefault(preferences.defaultChannel, Channel.STABLE)
    var selectedChannel by rememberSaveable(app.entry.id) {
        mutableStateOf(if (defaultChannel == Channel.BETA && app.entry.beta != null) Channel.BETA else Channel.STABLE)
    }
    val selectedVersion = app.entry.versionFor(selectedChannel)
    val isBusy = app.downloadState !is DownloadState.Idle &&
        app.downloadState !is DownloadState.Error &&
        app.downloadState !is DownloadState.Installed
    val targetInstalled = selectedVersion?.version == app.installedVersion
    val actionLabel = when {
        isBusy -> "Installazione in corso"
        selectedVersion == null -> null
        targetInstalled -> "Apri"
        app.installedVersion != null -> "Aggiorna a ${selectedVersion.version}"
        else -> "Installa ${selectedVersion.version}"
    }

    FluidScreen(
        title = app.entry.name,
        subtitle = "${app.entry.developer}  •  ${app.entry.category}",
        horizontalPadding = LocalStoreHorizontalPadding.current,
        onBack = onBack,
        titleFacets = listOf(selectedChannel.label, selectedVersion?.version.orEmpty()),
        ambient = FluidAmbient(
            tone = FluidHeroTone.PrimaryToSecondary,
            motif = FluidHeroMotif.Cards,
        ),
    ) {
        item {
            FluidHero(
                tone = if (app.hasUpdate) FluidHeroTone.Primary else FluidHeroTone.PrimaryToSecondary,
                motif = FluidHeroMotif.Cards,
                eyebrow = if (app.hasUpdate) "Aggiornamento disponibile" else "App Pampa",
                value = selectedVersion?.version?.let { "v$it" } ?: "—",
                title = app.entry.name,
                description = app.entry.description,
                icon = Icons.Rounded.Apps,
                metrics = listOf(
                    FluidHeroMetric("Dimensione", formatBytes(selectedVersion?.sizeBytes ?: 0L)),
                    FluidHeroMetric("Canale", selectedChannel.label),
                    FluidHeroMetric("Categoria", app.entry.category),
                ),
                actionLabel = actionLabel,
                onAction = selectedVersion?.let {
                    {
                        if (targetInstalled) onOpen(app.entry.packageName) else onInstall(selectedChannel)
                    }
                },
                modifier = Modifier.testTag("detail-hero"),
            )
        }

        if (app.entry.beta != null) {
            item {
                FluidSectionHeader("Canale di rilascio")
                FluidSegmentedControl(
                    options = Channel.entries,
                    selected = selectedChannel,
                    onSelect = { selectedChannel = it },
                    label = Channel::label,
                )
            }
        }

        if (!prerequisites.canInstallPackages && !targetInstalled) {
            item {
                InlineNotice(
                    title = "Autorizzazione necessaria",
                    message = prerequisites.message.orEmpty(),
                    tone = FluidTone.Warning,
                    onClick = onOpenInstallSettings,
                )
            }
        }

        if (app.downloadState !is DownloadState.Idle) {
            item { DownloadStatusCard(app.downloadState) }
        }

        if (app.entry.screenshots.isNotEmpty()) {
            item { FluidSectionHeader("Anteprima") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(app.entry.screenshots, key = { it }) { screenshot ->
                        AsyncImage(
                            model = screenshot,
                            contentDescription = "Schermata di ${app.entry.name}",
                            modifier = Modifier
                                .width(260.dp)
                                .height(520.dp)
                                .clip(ContinuousCornerShape(FluidRadius.Group)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

        item { FluidSectionHeader("Informazioni") }
        item {
            FluidListGroup(glass = true) {
                FluidListRow(
                    title = "Versione disponibile",
                    subtitle = selectedVersion?.version ?: "Non disponibile",
                    meta = selectedVersion?.releaseDate?.takeIf(String::isNotBlank),
                    tone = FluidTone.Primary,
                )
                FluidListDivider()
                FluidListRow(
                    title = "Versione installata",
                    subtitle = app.installedVersion ?: "Non installata",
                    tone = if (app.installedVersion != null) FluidTone.Success else FluidTone.Neutral,
                )
                FluidListDivider()
                FluidListRow(
                    title = "Pacchetto Android",
                    subtitle = app.entry.packageName.ifBlank { "Non dichiarato" },
                    tone = FluidTone.Neutral,
                )
            }
        }

        selectedVersion?.changelog?.takeIf(String::isNotBlank)?.let { changelog ->
            item { FluidSectionHeader("Novità") }
            item {
                FluidCard(glass = true) {
                    Text(
                        text = changelog,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        if (app.entry.history.isNotEmpty()) {
            item {
                FluidSectionHeader(
                    title = "Cronologia",
                    detail = "Tocca una versione per rimetterla",
                )
            }
            versionHistory(
                history = app.entry.history,
                installedVersion = app.installedVersion,
                pickedIndex = { historyPick },
                onMeasured = { index, rect -> historyOrigins[index] = rect },
                onPick = { index ->
                    pickedOrigin = historyOrigins[index]
                    historyPick = index
                },
            )
        }
    }

    // La release scelta dalla cronologia cresce dalla propria riga, come fa un aggiornamento nella
    // scheda Aggiornamenti: stesso gesto per la stessa cosa, cioè una versione che si guarda prima
    // di installarla.
    FluidGlassModalPortal(
        item = pickedVersion,
        onDismissRequest = { historyPick = -1 },
        origin = { pickedOrigin },
        presentation = FluidGlassModalPresentation.Popover,
        paneTitle = pickedVersion?.let { "Versione ${it.version}" },
    ) { version ->
        HistoryVersionPane(
            version = version,
            installedVersion = app.installedVersion,
            appName = app.entry.name,
            onInstall = {
                onInstallVersion(version)
                historyPick = -1
            },
        )
    }
}

/**
 * La cronologia, tagliata in piu' pannelli.
 *
 * Non e' un'ottimizzazione: e' cio' che rende toccabili le righe in fondo. ClasseViva ha
 * settantotto release, e settantotto righe con le loro note dentro **un solo** gruppo dentro **un
 * solo** item della lista fanno un nodo alto decine di migliaia di pixel — che smette di ricevere i
 * tocchi molto prima di smettere di disegnarsi. Le versioni vecchie si vedevano benissimo e non
 * rispondevano al dito: nessun errore, nessun pannello, niente.
 *
 * A vedersi resta un gruppo solo. [FluidGroupSegment] arrotonda soltanto gli angoli che sono
 * davvero l'inizio e la fine e toglie il bordo speculare dai lati aperti; ogni pezzo tranne
 * l'ultimo dichiara meno altezza di quella che disegna, cosi' il successivo gli sale addosso e
 * richiude l'aria che la lista mette fra un item e l'altro.
 */
private fun LazyListScope.versionHistory(
    history: List<VersionInfo>,
    installedVersion: String?,
    pickedIndex: () -> Int,
    onMeasured: (Int, Rect) -> Unit,
    onPick: (Int) -> Unit,
) {
    val chunks = history.chunked(HistoryChunk)
    chunks.forEachIndexed { chunkIndex, chunk ->
        val first = chunkIndex == 0
        val last = chunkIndex == chunks.lastIndex
        val segment = when {
            first && last -> FluidGroupSegment.Whole
            first -> FluidGroupSegment.First
            last -> FluidGroupSegment.Last
            else -> FluidGroupSegment.Middle
        }
        item(key = "history-$chunkIndex") {
            val seam = LocalFluidItemSpacing.current + FluidGroupOpenEdge * 2
            FluidListGroup(
                glass = true,
                segment = segment,
                modifier = Modifier
                    .then(if (last) Modifier else Modifier.closeSeam(seam))
                    .then(if (first) Modifier.testTag("detail-history") else Modifier),
            ) {
                chunk.forEachIndexed { rowIndex, version ->
                    val index = chunkIndex * HistoryChunk + rowIndex
                    val installable = version.apkAsset.isNotBlank() &&
                        version.releaseTag.isNotBlank()
                    val isCurrent = version.version == installedVersion
                    FluidListRow(
                        title = "Versione ${version.version}",
                        subtitle = version.releaseDate.ifBlank { "Release precedente" },
                        meta = version.changelog.takeIf(String::isNotBlank),
                        tone = if (isCurrent) FluidTone.Success else FluidTone.Neutral,
                        badge = {
                            when {
                                isCurrent -> FluidStatusBadge("Installata", tone = FluidTone.Success)
                                installable -> FluidStatusBadge("Installa", tone = FluidTone.Primary)
                                else -> FluidStatusBadge("Senza APK", tone = FluidTone.Neutral)
                            }
                        },
                        onClick = if (installable && !isCurrent) {
                            { onPick(index) }
                        } else {
                            null
                        },
                        modifier = Modifier.fluidExpandOrigin(
                            open = { pickedIndex() == index },
                            onMeasured = { onMeasured(index, it) },
                        ),
                    )
                    if (rowIndex != chunk.lastIndex) FluidListDivider()
                }
            }
        }
    }
}

/** Quante release per pannello. Otto e' quanto ne sta comodamente dentro un solo nodo. */
private const val HistoryChunk = 8

/**
 * Dichiara meno altezza di quella che disegna, cosi' il pezzo successivo sale a chiudere il vuoto.
 *
 * E' il meccanismo che [FluidGroupSegment] si aspetta da chi taglia: i lati aperti non disegnano il
 * bordo, ma l'aria fra un item e l'altro della lista resta, e senza richiuderla la giuntura si vede
 * come una fessura.
 */
private fun Modifier.closeSeam(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val closed = (placeable.height - amount.roundToPx()).coerceAtLeast(0)
    layout(placeable.width, closed) { placeable.place(0, 0) }
}

/**
 * Cosa succede davvero se rimetti questa versione.
 *
 * La distinzione che il pannello deve far capire *prima* del tocco: risalire è un aggiornamento
 * qualunque, tornare indietro no. Android riserva al sistema il permesso di sovrascrivere un
 * pacchetto con uno più vecchio, quindi per un installer come questo l'unica strada è disinstallare
 * — e con l'app se ne vanno i suoi dati. Scoprirlo dopo aver toccato sarebbe scoprirlo troppo tardi.
 */
@Composable
private fun HistoryVersionPane(
    version: VersionInfo,
    installedVersion: String?,
    appName: String,
    onInstall: () -> Unit,
) {
    val isDowngrade = requiresUninstallFirst(installedVersion, version.version)
    // fillMaxWidth + padding come nella galleria dell'engine: il pannello e' una card di larghezza
    // fissa, e un contenuto che non la riempie le lascia decidere la propria misura su niente.
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (installedVersion != null) {
            Text(
                text = "$installedVersion → ${version.version}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Il peso solo quando si sa: quindici delle settantotto release di ClasseViva non lo
        // dichiarano, e `formatBytes` per zero restituisce un trattino — che accanto alla data si
        // legge come un dato mancante messo in mostra invece che taciuto.
        listOfNotNull(
            version.releaseDate.takeIf(String::isNotBlank)?.let { "Pubblicata il $it" },
            version.sizeBytes.takeIf { it > 0L }?.let(::formatBytes),
        ).takeIf(List<String>::isNotEmpty)?.let { parts ->
            Text(
                text = parts.joinToString("  •  "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = version.changelog.takeIf(String::isNotBlank) ?: "Questa release non porta note.",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
        )
        if (isDowngrade) {
            InlineNotice(
                title = "Tornare indietro disinstalla l’app",
                message = "Android non lascia sovrascrivere una versione con una più vecchia. " +
                    "Ti chiedo di rimuovere $appName, e i suoi dati se ne vanno con lei; " +
                    "subito dopo installo la ${version.version}.",
                tone = FluidTone.Warning,
            )
        }
        FluidButton(
            text = if (isDowngrade) {
                "Disinstalla e installa la ${version.version}"
            } else {
                "Installa la ${version.version}"
            },
            onClick = onInstall,
            style = FluidButtonStyle.Filled,
            fillWidth = true,
        )
    }
}

@Composable
private fun DownloadStatusCard(state: DownloadState) {
    val title: String
    val detail: String
    val progress: Float?
    when (state) {
        DownloadState.Idle -> return
        DownloadState.Queued -> {
            title = "In coda"
            detail = "Parte appena finisce l'installazione precedente."
            progress = null
        }
        is DownloadState.Downloading -> {
            title = "Download ${(state.progress * 100).toInt().coerceIn(0, 100)}%"
            detail = "Sto scaricando l’APK verificato."
            progress = state.progress.coerceIn(0f, 1f)
        }
        is DownloadState.Verifying -> {
            title = "Verifica del pacchetto"
            detail = state.message
            progress = null
        }
        is DownloadState.AwaitingUser -> {
            title = "Conferma richiesta"
            detail = state.message
            progress = null
        }
        is DownloadState.Installing -> {
            title = "Installazione"
            detail = state.message
            progress = null
        }
        is DownloadState.Installed -> {
            title = "Installazione completata"
            detail = "Versione ${state.version} pronta."
            progress = 1f
        }
        is DownloadState.Error -> {
            title = "Operazione non riuscita"
            detail = state.message
            progress = null
        }
    }
    FluidCard(glass = true, modifier = Modifier.testTag("download-status")) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (progress != null) {
                FluidProgressBar(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
