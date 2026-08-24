package com.pampa.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pampa.store.data.DownloadState
import com.pampa.store.data.StoreApp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone

@Composable
fun StoreAppGroup(
    apps: List<StoreApp>,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FluidListGroup(modifier) {
        apps.forEachIndexed { index, app ->
            StoreAppRow(app = app, onClick = { onOpenApp(app.entry.id) })
            if (index != apps.lastIndex) FluidListDivider()
        }
    }
}

@Composable
private fun StoreAppRow(app: StoreApp, onClick: () -> Unit) {
    val subtitle = appTagline(app)
    val stateLabel = when (val state = app.downloadState) {
        is DownloadState.Downloading -> "${(state.progress * 100).toInt().coerceIn(0, 100)}%"
        is DownloadState.Verifying -> "Verifica"
        is DownloadState.AwaitingUser -> "Conferma"
        is DownloadState.Installing -> "Installo"
        is DownloadState.Installed -> "Installata"
        is DownloadState.Error -> "Riprova"
        DownloadState.Idle -> when {
            app.hasUpdate -> "Aggiorna"
            app.installedVersion != null -> app.installedVersion
            app.entry.stable == null && app.entry.beta == null -> "In arrivo"
            else -> "Installa"
        }
    }
    val tone = when {
        app.downloadState is DownloadState.Error -> FluidTone.Danger
        app.hasUpdate -> FluidTone.Primary
        app.installedVersion != null -> FluidTone.Success
        else -> FluidTone.Neutral
    }
    FluidListRow(
        title = app.entry.name,
        subtitle = subtitle,
        tone = tone,
        leading = { AppIcon(app.entry.iconUrl, app.entry.packageName, app.entry.name) },
        badge = { StatusPill(stateLabel, tone) },
        onClick = onClick,
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
            .clip(ContinuousCornerShape(11.dp))
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

@Composable
fun StatusPill(label: String, tone: FluidTone = FluidTone.Primary) {
    val background: Color
    val foreground: Color
    when (tone) {
        FluidTone.Success -> {
            background = MaterialTheme.colorScheme.tertiaryContainer
            foreground = MaterialTheme.colorScheme.onTertiaryContainer
        }
        FluidTone.Danger -> {
            background = MaterialTheme.colorScheme.errorContainer
            foreground = MaterialTheme.colorScheme.onErrorContainer
        }
        FluidTone.Warning -> {
            background = MaterialTheme.colorScheme.secondaryContainer
            foreground = MaterialTheme.colorScheme.onSecondaryContainer
        }
        else -> {
            background = MaterialTheme.colorScheme.primaryContainer
            foreground = MaterialTheme.colorScheme.onPrimaryContainer
        }
    }
    Surface(shape = ContinuousCornerShape(10.dp), color = background, contentColor = foreground) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun InlineNotice(
    title: String,
    message: String,
    isError: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = ContinuousCornerShape(FluidRadius.Group),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CloudOff,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
