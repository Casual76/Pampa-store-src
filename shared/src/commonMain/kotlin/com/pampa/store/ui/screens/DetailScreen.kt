package com.pampa.store.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pampa.store.data.model.AppDisplayInfo
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DownloadState
import com.pampa.store.data.model.VersionInfo
import com.pampa.store.ui.components.AppArtwork
import com.pampa.store.ui.components.AppScreenshotCarousel
import com.pampa.store.ui.components.ChannelToggle
import com.pampa.store.ui.components.EmptyState
import com.pampa.store.ui.components.StatusBadge
import com.pampa.store.ui.theme.AccentGreen
import com.pampa.store.ui.theme.AccentOrange
import com.pampa.store.ui.theme.getCategoryColor
import com.pampa.store.viewmodel.StoreViewModel
import com.pampa.store.ui.theme.rememberFluidOverscroll
import com.pampa.store.ui.theme.fluidOverscroll
import com.pampa.store.ui.theme.fluidOverscrollContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    appId: String,
    viewModel: StoreViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.displayApps.collectAsState()
    val defaultChannel by viewModel.defaultChannel.collectAsState()
    val installPrerequisites by viewModel.installPrerequisites.collectAsState()
    val appDisplay = apps.firstOrNull { it.entry.id == appId }

    LaunchedEffect(appId) {
        viewModel.refreshInstallPrerequisites()
    }

    if (appDisplay == null) {
        EmptyState(
            title = "App non trovata",
            subtitle = "L'app richiesta non e' presente nel catalogo corrente.",
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
        return
    }

    var selectedChannelName by rememberSaveable(appId) { mutableStateOf<String?>(null) }
    val fallbackChannel: Channel = when {
        appDisplay.installedChannel != null -> appDisplay.installedChannel ?: Channel.STABLE
        appDisplay.entry.beta == null && defaultChannel == Channel.BETA -> Channel.STABLE
        else -> defaultChannel
    }
    val selectedChannel = selectedChannelName?.let { Channel.valueOf(it) } ?: fallbackChannel
    val versionInfo = when (selectedChannel) {
        Channel.STABLE -> appDisplay.entry.stable
        Channel.BETA -> appDisplay.entry.beta
    }

    val isBusy = appDisplay.downloadState is DownloadState.Downloading ||
        appDisplay.downloadState is DownloadState.Verifying ||
        appDisplay.downloadState is DownloadState.AwaitingUserAction ||
        appDisplay.downloadState is DownloadState.Installing
    val hasLaunchTarget = appDisplay.installedVersion != null && appDisplay.entry.packageName.isNotBlank()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appDisplay.entry.name,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!installPrerequisites.canInstallPackages) {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SettingsApplications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = installPrerequisites.message ?: "Abilita le origini sconosciute per continuare.",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }

                        Button(
                            onClick = viewModel::openInstallSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(Icons.Filled.SettingsApplications, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Apri impostazioni installazione")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.downloadAndInstall(appId, selectedChannel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = versionInfo != null && !isBusy &&
                                (appDisplay.installedVersion == null || appDisplay.hasUpdate || appDisplay.downloadState is DownloadState.Error),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (appDisplay.hasUpdate) AccentGreen else MaterialTheme.colorScheme.primary,
                                contentColor = if (appDisplay.hasUpdate) Color.White else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            when (val state = appDisplay.downloadState) {
                                is DownloadState.Downloading -> {
                                    CircularProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 3.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Text("Download ${(state.progress * 100).toInt()}%")
                                }

                                is DownloadState.Verifying -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 3.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Text(state.message)
                                }

                                is DownloadState.AwaitingUserAction -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 3.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Text(state.message)
                                }

                                is DownloadState.Installing -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 3.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Text(state.message)
                                }

                                is DownloadState.Error -> {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Riprova installazione")
                                }

                                else -> {
                                    val icon = when {
                                        appDisplay.hasUpdate -> Icons.Filled.SystemUpdateAlt
                                        appDisplay.installedVersion != null -> Icons.Filled.CheckCircle
                                        else -> Icons.Filled.Download
                                    }
                                    Icon(icon, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(primaryCtaLabel(appDisplay, versionInfo))
                                }
                            }
                        }

                        if (hasLaunchTarget) {
                            OutlinedButton(
                                onClick = { viewModel.launchInstalledApp(appDisplay.entry.packageName) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Apri app")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val overscroll = rememberFluidOverscroll()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .fluidOverscroll(overscroll)
                .fluidOverscrollContent(overscroll),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroSection(appDisplay = appDisplay)
            }

            item {
                ChannelToggle(
                    selectedChannel = selectedChannel,
                    onChannelChanged = { selectedChannelName = it.name },
                    hasBeta = appDisplay.entry.beta != null
                )
            }

            item {
                VersionInfoSection(
                    appDisplay = appDisplay,
                    selectedChannel = selectedChannel,
                    versionInfo = versionInfo
                )
            }

            if (appDisplay.entry.screenshots.isNotEmpty()) {
                item {
                    SectionTitle(title = "Screenshot", icon = Icons.Filled.Image)
                }
                item {
                    AppScreenshotCarousel(screenshots = appDisplay.entry.screenshots)
                }
            }

            if (appDisplay.downloadState is DownloadState.Error) {
                item {
                    ErrorCard(message = (appDisplay.downloadState as DownloadState.Error).message)
                }
            }

            item {
                SectionTitle(title = "Novita e dettagli", icon = Icons.Filled.Info)
            }
            item {
                DescriptionCard(description = appDisplay.entry.description)
            }

            if (appDisplay.entry.history.isNotEmpty()) {
                item {
                    SectionTitle(title = "Versioni precedenti", icon = Icons.Filled.History)
                }
                items(appDisplay.entry.history) { version ->
                    HistoryCard(version)
                }
            }
        }
    }
}

@Composable
private fun HeroSection(appDisplay: AppDisplayInfo) {
    val categoryColor = getCategoryColor(appDisplay.entry.category)

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            categoryColor.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppArtwork(
                    app = appDisplay.entry,
                    modifier = Modifier.size(88.dp),
                    cornerRadius = 24
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(
                        text = appDisplay.entry.category,
                        background = categoryColor.copy(alpha = 0.12f),
                        contentColor = categoryColor
                    )

                    Text(
                        text = appDisplay.entry.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = appDisplay.entry.developer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (appDisplay.installedVersion != null) {
                            StatusBadge(
                                text = "v${appDisplay.installedVersion}",
                                background = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (appDisplay.hasUpdate) {
                            StatusBadge(
                                text = "Aggiornamento",
                                background = AccentGreen.copy(alpha = 0.15f),
                                contentColor = AccentGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionInfoSection(
    appDisplay: AppDisplayInfo,
    selectedChannel: Channel,
    versionInfo: VersionInfo?
) {
    if (versionInfo == null) {
        ElevatedCard(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Text(
                text = "Nessuna release ${selectedChannel.displayName} disponibile.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Release ${selectedChannel.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(
                    text = "v${versionInfo.version}",
                    background = if (selectedChannel == Channel.BETA) {
                        AccentOrange.copy(alpha = 0.15f)
                    } else {
                        AccentGreen.copy(alpha = 0.15f)
                    },
                    contentColor = if (selectedChannel == Channel.BETA) AccentOrange else AccentGreen
                )
            }

            Text(
                text = "Pubblicata il ${versionInfo.releaseDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (appDisplay.installedVersion != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Versione installata: ${appDisplay.installedVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (versionInfo.changelog.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = versionInfo.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DescriptionCard(description: String) {
    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            text = description,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun HistoryCard(version: VersionInfo) {
    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v${version.version}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = version.releaseDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (version.changelog.isNotBlank()) {
                Text(
                    text = version.changelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun primaryCtaLabel(appDisplay: AppDisplayInfo, versionInfo: VersionInfo?): String {
    return when {
        appDisplay.hasUpdate -> "Aggiorna a v${versionInfo?.version.orEmpty()}"
        appDisplay.installedVersion != null -> "Gia' installata"
        else -> "Installa"
    }
}
