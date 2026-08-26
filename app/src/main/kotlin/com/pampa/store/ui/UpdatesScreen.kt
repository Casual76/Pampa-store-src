package com.pampa.store.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pampa.store.data.DownloadState
import com.pampa.store.data.QueueEntry
import com.pampa.store.data.StoreApp
import com.pampa.store.data.StoreUiState
import com.pampa.store.data.versionFor
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFoldingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidHero
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMetric
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidLoadingBlock
import dev.antigravity.fluidengine.ui.fluid.FluidProgressBar
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

/**
 * La scheda che dice cosa c'è da aggiornare, e lo fa fare.
 *
 * Esisteva già un filtro "Aggiornabili" nel Catalogo, e non è la stessa cosa: un filtro è un modo di
 * guardare l'elenco delle app da installare, mentre questo è l'elenco di quello che *hai già* e che
 * è rimasto indietro. Due domande diverse, e la seconda merita un posto suo perché è quella per cui
 * arriva una notifica.
 */
@Composable
fun UpdatesScreen(
    state: StoreUiState,
    onRefresh: () -> Unit,
    onUpdateAll: () -> Unit,
    onUpdateOne: (String) -> Unit,
    onInstallStoreUpdate: () -> Unit,
    onRetryFailed: () -> Unit,
    onClearQueue: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    val updatable = state.updatableApps
    val queue = state.updateQueue
    val total = updatable.size + if (state.storeUpdate != null) 1 else 0
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val expanded = updatable.firstOrNull { it.entry.id == expandedId }

    // Dove sta la riga che ha aperto il pannello. Una mappa e non uno stato per riga: l'origine
    // serve al *portale*, che vive fuori dalla lista, e nel momento in cui il modale chiede "da
    // dove cresco" la riga potrebbe essere già stata riciclata dalla lazy list.
    //
    // Mappa normale: qui si scrive durante il layout, a ogni spostamento della riga.
    val origins = remember { mutableMapOf<String, Rect>() }

    // Il rettangolo su cui il pannello torna, fissato al tocco e **non azzerato** alla chiusura.
    // L'host legge `origin` mentre l'uscita e' in corso: se in quel momento la lambda non sa piu'
    // da dove era partita, il pannello si richiude al centro dello schermo invece che dentro la
    // riga. Questo e' snapshot state, che e' anche cio' che il contratto di `origin` chiede.
    var pickedOrigin by remember { mutableStateOf<Rect?>(null) }

    RequestNotificationPermissionOnce(missing = !state.notificationsEnabled)

    FluidScreen(
        title = "Aggiornamenti",
        subtitle = updatesSubtitle(total, queue),
        horizontalPadding = LocalStoreHorizontalPadding.current,
        titleFacets = listOf(
            if (total == 1) "1 in attesa" else "$total in attesa",
            "${state.installedCount} installate",
        ),
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        // Il colore della pagina significa qualcosa. Con qualcosa da fare e' Alert (il secondario
        // virato verso l'errore) portato a errore pieno da `urgent`; a coda vuota e' il terziario,
        // che e' calmo: una pagina rossa che dice "sei all'ultima versione" da' l'allarme sbagliato,
        // e una pagina che lo da' sempre smette di darlo.
        ambient = FluidAmbient(
            tone = if (total > 0) FluidHeroTone.Alert else FluidHeroTone.Tertiary,
            motif = FluidHeroMotif.Ticks,
            urgent = total > 0,
        ),
        actions = {
            FluidBarAction(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Cerca aggiornamenti",
                onClick = onRefresh,
            )
        },
        overlay = { backdrop ->
            if (total > 0) {
                UpdateAllCapsule(
                    onRecheck = onRefresh,
                    hasFailures = queue.any(QueueEntry::isFailed),
                    hasPending = queue.any { it.isWaiting || it.isFinished },
                    onUpdateAll = onUpdateAll,
                    onRetryFailed = onRetryFailed,
                    onClearQueue = onClearQueue,
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        },
    ) {
        if (state.isLoading && state.apps.isEmpty()) {
            item { FluidLoadingBlock(modifier = Modifier.testTag("updates-loading")) }
            return@FluidScreen
        }

        item {
            FluidHero(
                tone = if (total > 0) FluidHeroTone.Alert else FluidHeroTone.Tertiary,
                motif = FluidHeroMotif.Ticks,
                urgent = total > 0,
                eyebrow = "Le tue app",
                value = total.toString(),
                title = if (total == 1) "aggiornamento pronto" else "aggiornamenti pronti",
                description = if (total > 0) {
                    "Scarico e verifico io. Android chiede conferma una volta per pacchetto, " +
                        "quindi la coda procede una alla volta."
                } else {
                    "Tutto quello che hai installato dal Pampa Store è all’ultima versione."
                },
                icon = Icons.Rounded.Download,
                metrics = listOf(
                    FluidHeroMetric("Installate", state.installedCount.toString(), onOpenCatalog),
                    FluidHeroMetric("Nel catalogo", state.apps.size.toString(), onOpenCatalog),
                    FluidHeroMetric("In coda", queue.count { !it.isFinished }.toString()),
                ),
                actionLabel = if (total > 0) "Aggiorna tutto" else null,
                onAction = if (total > 0) onUpdateAll else null,
                modifier = Modifier.testTag("updates-hero"),
            )
        }

        if (!state.prerequisites.canInstallPackages) {
            item {
                InlineNotice(
                    title = "Autorizzazione necessaria",
                    message = state.prerequisites.message
                        ?: "Abilita Pampa Store fra le origini consentite per installare gli APK.",
                    tone = FluidTone.Warning,
                    onClick = onOpenInstallSettings,
                )
            }
        }

        if (!state.notificationsEnabled) {
            item {
                InlineNotice(
                    title = "Notifiche disattivate",
                    message = "Il controllo automatico continua lo stesso, ma senza notifiche " +
                        "scopri gli aggiornamenti solo aprendo lo Store.",
                    tone = FluidTone.Info,
                )
            }
        }

        if (queue.isNotEmpty()) {
            item { FluidSectionHeader(title = "Coda", detail = queueDetail(queue)) }
            item {
                FluidListGroup(glass = true, modifier = Modifier.testTag("update-queue")) {
                    queue.forEachIndexed { index, entry ->
                        QueueRow(entry)
                        if (index != queue.lastIndex) FluidListDivider()
                    }
                }
            }
        }

        state.storeUpdate?.let { update ->
            item { FluidSectionHeader(title = "Pampa Store", detail = "L’app che stai usando") }
            item {
                FluidListGroup(glass = true) {
                    FluidListRow(
                        title = "Versione ${update.version.version}",
                        subtitle = update.version.changelog.ifBlank {
                            "Aggiornamento pronto da installare."
                        },
                        meta = update.version.releaseDate.takeIf(String::isNotBlank),
                        tone = FluidTone.Primary,
                        leading = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                        badge = { FluidStatusBadge("Aggiorna", tone = FluidTone.Primary) },
                        onClick = onInstallStoreUpdate,
                    )
                }
            }
        }

        if (updatable.isNotEmpty()) {
            item {
                FluidSectionHeader(
                    title = "Da aggiornare",
                    detail = "Tocca una riga per vedere cosa cambia",
                )
            }
            item {
                FluidListGroup(glass = true, modifier = Modifier.testTag("updatable-apps")) {
                    updatable.forEachIndexed { index, app ->
                        UpdatableRow(
                            app = app,
                            isExpanded = { expandedId == app.entry.id },
                            onMeasured = { origins[app.entry.id] = it },
                            onExpand = {
                                pickedOrigin = origins[app.entry.id]
                                expandedId = app.entry.id
                            },
                            onUpdate = { onUpdateOne(app.entry.id) },
                            onOpenDetail = { onOpenApp(app.entry.id) },
                        )
                        if (index != updatable.lastIndex) FluidListDivider()
                    }
                }
            }
        } else if (queue.isEmpty()) {
            item {
                FluidEmptyState(
                    title = "Tutto aggiornato",
                    detail = "Ricontrollo ogni sei ore, anche quando lo Store è chiuso.",
                )
            }
        }

        item {
            FluidSectionFootnote(
                "Il controllo automatico gira ogni sei ore quando c’è rete, e quello che scarica " +
                    "finisce nella cache dello Store: la prossima apertura è già piena.",
            )
        }
    }

    // Il pannello che nasce dalla riga toccata: cresce dal suo rettangolo con la rifrazione addosso
    // e ci ritorna dentro. Sta fuori dalla lista perché l'host dei modali vive alla radice — un
    // pop-up su cui la barra di navigazione può appoggiarsi non è un pop-up.
    //
    // Popover con un'origine, non Expand: dalla 1.9.5 è l'ancora a fare il viaggio con la
    // rifrazione addosso, quindi il morph dalla riga si ha comunque. Expand è invece la
    // presentazione del *menù di azioni* — l'host disegna la lista di FluidContextAction e ignora
    // in silenzio il `content` che gli passi, quindi il pannello uscirebbe vuoto.
    FluidGlassModalPortal(
        item = expanded,
        onDismissRequest = { expandedId = null },
        origin = { pickedOrigin },
        presentation = FluidGlassModalPresentation.Popover,
        paneTitle = expanded?.entry?.name,
    ) { app ->
        UpdateDetailPane(
            app = app,
            onUpdate = {
                onUpdateOne(app.entry.id)
                expandedId = null
            },
            onOpenDetail = {
                onOpenApp(app.entry.id)
                expandedId = null
            },
        )
    }
}

@Composable
private fun UpdatableRow(
    app: StoreApp,
    isExpanded: () -> Boolean,
    onMeasured: (Rect) -> Unit,
    onExpand: () -> Unit,
    onUpdate: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val from = app.installedVersion.orEmpty()
    val to = app.availableVersion.orEmpty()
    FluidListRow(
        title = app.entry.name,
        subtitle = if (from.isNotBlank() && to.isNotBlank()) "$from → $to" else app.entry.category,
        meta = app.installedChannel?.label,
        tone = FluidTone.Primary,
        leading = { AppIcon(app.entry.iconUrl, app.entry.packageName, app.entry.name) },
        badge = {
            FluidStatusBadge(downloadLabel(app.downloadState), tone = downloadTone(app.downloadState))
        },
        onClick = onExpand,
        contextActions = {
            listOf(
                FluidContextAction("Aggiorna ora", Icons.Rounded.Download, onClick = onUpdate),
                FluidContextAction("Apri la scheda", Icons.AutoMirrored.Rounded.OpenInNew, onClick = onOpenDetail),
            )
        },
        modifier = Modifier.fluidExpandOrigin(open = isExpanded, onMeasured = onMeasured),
    )
}

@Composable
private fun QueueRow(entry: QueueEntry) {
    FluidListRow(
        title = entry.name,
        subtitle = queueSubtitle(entry),
        meta = "v${entry.targetVersion}",
        tone = downloadTone(entry.state),
        badge = { FluidStatusBadge(downloadLabel(entry.state), tone = downloadTone(entry.state)) },
    )
    // La barra vive sotto la riga invece che dentro: FluidListRow non ha uno slot per una cosa
    // larga quanto se stessa, e infilarcela come badge la schiaccerebbe nella colonna di destra.
    (entry.state as? DownloadState.Downloading)?.let { downloading ->
        FluidProgressBar(
            progress = { downloading.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 14.dp),
        )
    }
}

@Composable
private fun UpdateDetailPane(app: StoreApp, onUpdate: () -> Unit, onOpenDetail: () -> Unit) {
    val version = app.installedChannel?.let(app.entry::versionFor)
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${app.installedVersion.orEmpty()} → ${app.availableVersion.orEmpty()}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = version?.changelog?.takeIf(String::isNotBlank) ?: "Questa release non porta note.",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
        )
        FluidListGroup {
            FluidListRow(
                title = "Aggiorna ora",
                subtitle = "Android chiede conferma una volta",
                tone = FluidTone.Primary,
                leading = { Icon(Icons.Rounded.Download, contentDescription = null) },
                onClick = onUpdate,
            )
            FluidListDivider()
            FluidListRow(
                title = "Apri la scheda",
                subtitle = "Cronologia, canali e dimensione",
                tone = FluidTone.Neutral,
                leading = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                onClick = onOpenDetail,
            )
        }
    }
}

/**
 * Il tasto che si trasforma nel proprio menù.
 *
 * Il tocco fa la cosa ovvia — aggiorna tutto — e tenerlo premuto apre le tre che non meritano un
 * tasto proprio. È l'unica superficie dell'app in cui Fluid-physics si vede al lavoro, e sta qui
 * perché è anche l'unico posto in cui quelle tre azioni hanno davvero qualcosa da dire.
 *
 * Resta abilitata anche mentre la coda gira, ed è deliberato: "svuota la coda" e "riprova i
 * falliti" servono **proprio** durante una corsa, e spegnere il tasto le porterebbe via nel momento
 * in cui uno le cerca. Un tocco di troppo non fa danni, perché accodare due volte la stessa app è
 * già un'operazione che non fa niente.
 */
@Composable
private fun UpdateAllCapsule(
    onRecheck: () -> Unit,
    hasFailures: Boolean,
    hasPending: Boolean,
    onUpdateAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onClearQueue: () -> Unit,
    backdrop: GlassBackdropState,
    modifier: Modifier = Modifier,
) {
    val morphMenu = LocalStoreMorphMenu.current ?: return
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = FluidFoldingTabBarDefaults.ContentInset + 8.dp),
    ) {
        FluidMorphMenuButton(
            state = morphMenu,
            actions = {
                listOf(
                    FluidContextAction(
                        label = "Riprova i falliti",
                        icon = Icons.Rounded.Replay,
                        enabled = hasFailures,
                        onClick = onRetryFailed,
                    ),
                    FluidContextAction(
                        label = "Svuota la coda",
                        icon = Icons.Rounded.DeleteSweep,
                        enabled = hasPending,
                        destructive = true,
                        onClick = onClearQueue,
                    ),
                    FluidContextAction(
                        label = "Cerca adesso",
                        icon = Icons.Rounded.Refresh,
                        onClick = onRecheck,
                    ),
                )
            },
            onClick = onUpdateAll,
            text = "Aggiorna tutto",
            icon = Icons.Rounded.Download,
            backdrop = backdrop,
            modifier = Modifier.testTag("update-all"),
        )
    }
}

/**
 * Chiede il permesso notifiche una volta sola, e solo qui.
 *
 * Qui e non all'avvio: un dialogo di sistema come primo fotogramma di un'app appena installata è una
 * domanda senza contesto, e la risposta prevedibile è no. Aprire la scheda degli aggiornamenti è
 * invece il momento in cui la richiesta si spiega da sola.
 */
@Composable
private fun RequestNotificationPermissionOnce(missing: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(missing, asked) {
        if (missing && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun updatesSubtitle(total: Int, queue: List<QueueEntry>): String {
    queue.firstOrNull(QueueEntry::isRunning)?.let { return "Sto lavorando su ${it.name}" }
    return when (total) {
        0 -> "Niente da fare: sei all’ultima versione."
        1 -> "Un’app che hai installato è rimasta indietro."
        else -> "$total app che hai installato sono rimaste indietro."
    }
}

private fun queueDetail(queue: List<QueueEntry>): String {
    val done = queue.count(QueueEntry::isDone)
    val failed = queue.count(QueueEntry::isFailed)
    return buildList {
        add("${queue.size} in lista")
        if (done > 0) add("$done completate")
        if (failed > 0) add("$failed non riuscite")
    }.joinToString(" • ")
}

private fun queueSubtitle(entry: QueueEntry): String = when (val state = entry.state) {
    is DownloadState.Error -> state.message
    is DownloadState.Verifying -> state.message
    is DownloadState.AwaitingUser -> state.message
    is DownloadState.Installing -> state.message
    is DownloadState.Installed -> "Versione ${state.version} installata"
    is DownloadState.Downloading -> "Scarico l’APK verificato"
    DownloadState.Queued -> "In attesa del proprio turno"
    DownloadState.Idle -> "Pronta"
}

/** L'etichetta della pillola di stato, in un posto solo per riga, coda e catalogo. */
internal fun downloadLabel(state: DownloadState): String = when (state) {
    is DownloadState.Downloading -> "${(state.progress * 100).toInt().coerceIn(0, 100)}%"
    is DownloadState.Verifying -> "Verifica"
    is DownloadState.AwaitingUser -> "Conferma"
    is DownloadState.Installing -> "Installo"
    is DownloadState.Installed -> "Fatto"
    is DownloadState.Error -> "Riprova"
    DownloadState.Queued -> "In coda"
    DownloadState.Idle -> "Aggiorna"
}

internal fun downloadTone(state: DownloadState): FluidTone = when (state) {
    is DownloadState.Error -> FluidTone.Danger
    is DownloadState.Installed -> FluidTone.Success
    DownloadState.Queued -> FluidTone.Neutral
    DownloadState.Idle -> FluidTone.Primary
    else -> FluidTone.Info
}
