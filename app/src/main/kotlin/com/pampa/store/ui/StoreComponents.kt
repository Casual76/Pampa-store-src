package com.pampa.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pampa.store.data.DownloadState
import com.pampa.store.data.StoreApp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

@Composable
fun StoreAppGroup(
    apps: List<StoreApp>,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Avvia un'app installata. Quando c'è, una pressione lunga la apre senza passare dal dettaglio. */
    onLaunchApp: ((String) -> Unit)? = null,
) {
    // Il vetro va sul contenitore, mai sulla riga: un pannello per il gruppo, quante che siano le
    // righe che ci finiscono dentro.
    FluidListGroup(modifier = modifier, glass = true) {
        apps.forEachIndexed { index, app ->
            StoreAppRow(
                app = app,
                onClick = { onOpenApp(app.entry.id) },
                onLaunch = onLaunchApp
                    ?.takeIf { app.installedVersion != null && app.entry.packageName.isNotBlank() }
                    ?.let { launch -> { launch(app.entry.packageName) } },
            )
            if (index != apps.lastIndex) FluidListDivider()
        }
    }
}

@Composable
private fun StoreAppRow(app: StoreApp, onClick: () -> Unit, onLaunch: (() -> Unit)? = null) {
    // Un'app senza release non ha un bottone da mostrare, e chiamarla "Installa" sarebbe una
    // promessa che il dettaglio poi non mantiene.
    val unreleased = app.entry.stable == null && app.entry.beta == null
    val label = when {
        app.downloadState != DownloadState.Idle -> downloadLabel(app.downloadState)
        app.hasUpdate -> "Aggiorna"
        app.installedVersion != null -> app.installedVersion
        unreleased -> "In arrivo"
        else -> "Installa"
    }
    val tone = when {
        app.downloadState != DownloadState.Idle -> downloadTone(app.downloadState)
        app.hasUpdate -> FluidTone.Primary
        app.installedVersion != null -> FluidTone.Success
        else -> FluidTone.Neutral
    }
    FluidListRow(
        title = app.entry.name,
        subtitle = appTagline(app),
        tone = tone,
        leading = { AppIcon(app.entry.iconUrl, app.entry.packageName, app.entry.name) },
        badge = { FluidStatusBadge(label, tone = tone) },
        onClick = onClick,
        contextActions = onLaunch?.let { launch ->
            {
                listOf(
                    FluidContextAction("Apri l’app", Icons.AutoMirrored.Rounded.OpenInNew, onClick = launch),
                    FluidContextAction("Scheda nello Store", Icons.Rounded.Info, onClick = onClick),
                )
            }
        },
    )
}

private fun appTagline(app: StoreApp): String = when (app.entry.id) {
    "classeviva-expressive" -> "Gestione scuola"
    "keyvoice" -> "Accessibilità e sintesi vocale"
    "pampa-widgets" -> "Widget utili per tutti"
    "universal-converter" -> "Conversioni rapide"
    "mycloud-gallery" -> "Galleria cloud personale"
    "galleria" -> "Foto organizzate con semplicità"
    "codex" -> "Messaggistica privata"
    "fluid-glass" -> "Il vetro liquido, da provare con le dita"
    "pampa-invaders", "pampa-skylines", "pampa-star-shooter" -> "Gioco arcade"
    else -> app.entry.description.ifBlank { app.entry.category }
}

@Composable
fun AppIcon(url: String, packageName: String, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val installedIcon = remember(packageName) {
        packageName.takeIf(String::isNotBlank)?.let {
            runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull()
        }
    }
    val model: Any? = url.takeIf(String::isNotBlank) ?: installedIcon
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(ContinuousCornerShape(FluidRadius.Small))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(24.dp))
        } else {
            AsyncImage(
                model = model,
                contentDescription = "Icona di $name",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * Un avviso dentro la pagina.
 *
 * Due forme, e la differenza è se c'è qualcosa da toccare. Senza azione è il messaggio in linea
 * dell'engine; con un'azione è una riga di lista con un pannello suo — che è esattamente cosa *è*
 * un avviso toccabile, e ha già il tocco, il bersaglio e la piastrella colorata dell'icona.
 *
 * L'engine non ha un [FluidInlineMessage] cliccabile, e inventarne uno qui significherebbe
 * riscrivere a mano la superficie, i colori del tono e il feedback del tocco — cioè le tre cose che
 * il design system esiste per non far riscrivere.
 */
@Composable
fun InlineNotice(
    title: String,
    message: String,
    tone: FluidTone = FluidTone.Info,
    onClick: (() -> Unit)? = null,
) {
    if (onClick == null) {
        FluidInlineMessage(message = message, title = title, tone = tone)
        return
    }
    FluidListGroup(glass = true) {
        FluidListRow(
            title = title,
            subtitle = message,
            tone = tone,
            leading = { Icon(noticeIcon(tone), contentDescription = null) },
            onClick = onClick,
        )
    }
}

private fun noticeIcon(tone: FluidTone) = when (tone) {
    FluidTone.Danger -> Icons.Rounded.ErrorOutline
    FluidTone.Warning -> Icons.Rounded.WarningAmber
    FluidTone.Neutral -> Icons.Rounded.CloudOff
    else -> Icons.Rounded.Info
}
