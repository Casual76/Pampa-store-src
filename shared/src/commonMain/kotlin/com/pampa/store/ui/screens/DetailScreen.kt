package com.pampa.store.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.pampa.store.ui.theme.GlassStrokeDark
import com.pampa.store.ui.theme.GlassStrokeLight
import com.pampa.store.ui.theme.PrimaryViolet
import com.pampa.store.ui.theme.getCategoryColor
import com.pampa.store.viewmodel.StoreViewModel

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
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f

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
        bottomBar = {
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.04f else 0.08f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.86f else 0.92f)
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!installPrerequisites.canInstallPackages) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = AccentOrange.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SettingsApplications,
                                    contentDescription = null,
                                    tint = AccentOrange
                                )
                                Text(
                                    text = installPrerequisites.message ?: "Abilita le origini sconosciute per continuare.",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Button(
                            onClick = viewModel::openInstallSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
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
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (appDisplay.hasUpdate) AccentGreen else PrimaryViolet,
                                contentColor = if (appDisplay.hasUpdate) Color.Black else Color.White
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
                                shape = RoundedCornerShape(18.dp)
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    Card(
        modifier = Modifier.border(
            width = 1.dp,
            color = if (isDarkTheme) {
                GlassStrokeDark
            } else {
                GlassStrokeLight
            },
            shape = RoundedCornerShape(30.dp)
        ),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.76f else 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            getCategoryColor(appDisplay.entry.category).copy(alpha = 0.4f),
                            AccentOrange.copy(alpha = if (isDarkTheme) 0.16f else 0.1f),
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(110.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppArtwork(
                    app = appDisplay.entry,
                    modifier = Modifier.size(112.dp),
                    cornerRadius = 30
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusBadge(
                        text = appDisplay.entry.category,
                        background = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.52f else 0.78f),
                        contentColor = getCategoryColor(appDisplay.entry.category)
                    )

                    Text(
                        text = appDisplay.entry.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = appDisplay.entry.developer,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (appDisplay.installedVersion != null) {
                            StatusBadge(
                                text = "Installata v${appDisplay.installedVersion}",
                                background = PrimaryViolet.copy(alpha = 0.15f),
                                contentColor = PrimaryViolet
                            )
                        }
                        if (appDisplay.hasUpdate) {
                            StatusBadge(
                                text = "Aggiornamento disponibile",
                                background = AccentGreen.copy(alpha = 0.18f),
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
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    if (versionInfo == null) {
        Card(
            modifier = Modifier.border(
                width = 1.dp,
                color = if (isDarkTheme) {
                    GlassStrokeDark
                } else {
                    GlassStrokeLight
                },
                shape = RoundedCornerShape(22.dp)
            ),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.64f else 0.82f)
            )
        ) {
            Text(
                text = "Nessuna release ${selectedChannel.displayName} disponibile.",
                modifier = Modifier.padding(18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Card(
        modifier = Modifier.border(
            width = 1.dp,
            color = if (isDarkTheme) {
                GlassStrokeDark
            } else {
                GlassStrokeLight
            },
            shape = RoundedCornerShape(22.dp)
        ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.72f else 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Release ${selectedChannel.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(
                    text = "v${versionInfo.version}",
                    background = if (selectedChannel == Channel.BETA) {
                        AccentOrange.copy(alpha = 0.2f)
                    } else {
                        AccentGreen.copy(alpha = 0.2f)
                    },
                    contentColor = if (selectedChannel == Channel.BETA) AccentOrange else AccentGreen
                )
            }

            Text(
                text = "Pubblicata il ${versionInfo.releaseDate}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (appDisplay.installedVersion != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Text(
                    text = "Versione installata: ${appDisplay.installedVersion}",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (versionInfo.changelog.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Text(
                    text = versionInfo.changelog,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DescriptionCard(description: String) {
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    Card(
        modifier = Modifier.border(
            width = 1.dp,
            color = if (isDarkTheme) {
                GlassStrokeDark
            } else {
                GlassStrokeLight
            },
            shape = RoundedCornerShape(22.dp)
        ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.72f else 0.9f)
        )
    ) {
        Text(
            text = description,
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun HistoryCard(version: VersionInfo) {
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    Card(
        modifier = Modifier.border(
            width = 1.dp,
            color = if (isDarkTheme) {
                GlassStrokeDark
            } else {
                GlassStrokeLight
            },
            shape = RoundedCornerShape(20.dp)
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.6f else 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "v${version.version}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = version.releaseDate,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (version.changelog.isNotBlank()) {
                Text(
                    text = version.changelog,
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
        Icon(imageVector = icon, contentDescription = null, tint = AccentOrange)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
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
