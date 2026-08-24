package com.pampa.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pampa.store.data.Channel
import com.pampa.store.data.DownloadState
import com.pampa.store.data.InstallPrerequisites
import com.pampa.store.data.StoreApp
import com.pampa.store.data.StorePreferences
import com.pampa.store.data.enumOrDefault
import com.pampa.store.data.versionFor
import com.pampa.store.util.formatBytes
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHero
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMetric
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone

@Composable
fun DetailScreen(
    app: StoreApp?,
    preferences: StorePreferences,
    prerequisites: InstallPrerequisites,
    onBack: () -> Unit,
    onInstall: (Channel) -> Unit,
    onOpen: (String) -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    if (app == null) {
        FluidScreen(title = "App non trovata", onBack = onBack) {
            item { FluidEmptyState("Contenuto non disponibile", "Torna al catalogo e aggiorna i dati.") }
        }
        return
    }

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
                    isError = false,
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
                                .clip(ContinuousCornerShape(22.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

        item { FluidSectionHeader("Informazioni") }
        item {
            FluidListGroup {
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
                Surface(
                    shape = ContinuousCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = changelog,
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        if (app.entry.history.isNotEmpty()) {
            item { FluidSectionHeader("Cronologia") }
            item {
                FluidListGroup {
                    app.entry.history.take(5).forEachIndexed { index, version ->
                        FluidListRow(
                            title = "Versione ${version.version}",
                            subtitle = version.releaseDate.ifBlank { "Release precedente" },
                            meta = version.changelog.takeIf(String::isNotBlank),
                            tone = FluidTone.Neutral,
                        )
                        if (index != app.entry.history.take(5).lastIndex) FluidListDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusCard(state: DownloadState) {
    val title: String
    val detail: String
    val progress: Float?
    when (state) {
        DownloadState.Idle -> return
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
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("download-status"),
        shape = ContinuousCornerShape(20.dp),
        color = if (state is DownloadState.Error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().clip(ContinuousCornerShape(6.dp)),
                )
            }
        }
    }
}
