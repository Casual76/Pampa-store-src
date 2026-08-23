package com.pampa.store.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pampa.store.data.model.AppDisplayInfo
import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DownloadState
import com.pampa.store.data.model.OwnershipFilter
import com.pampa.store.ui.theme.ContinuousCornerShape
import com.pampa.store.ui.theme.AccentGreen
import com.pampa.store.ui.theme.AccentOrange
import com.pampa.store.ui.theme.getCategoryColor

@Composable
fun AppCard(
    app: AppDisplayInfo,
    preferredChannel: Channel,
    onClick: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(app.entry.category)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Hero area with category gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                categoryColor.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = categoryIconFor(app.entry.category),
                    contentDescription = null,
                    tint = categoryColor.copy(alpha = 0.1f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(80.dp)
                )

                // Category chip
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = MaterialTheme.shapes.small,
                    color = categoryColor.copy(alpha = 0.15f),
                    contentColor = categoryColor
                ) {
                    Text(
                        text = app.entry.category,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // App artwork + name overlay at bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppArtwork(
                        app = app.entry,
                        modifier = Modifier.size(64.dp),
                        cornerRadius = 18
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = app.entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.entry.developer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (app.hasUpdate) {
                    StatusBadge(
                        text = "Aggiornamento",
                        background = AccentGreen,
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }
            }

            // Content area
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = app.entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = buildVersionLabel(app, preferredChannel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InstallButton(
                        downloadState = app.downloadState,
                        isInstalled = app.installedVersion != null,
                        hasUpdate = app.hasUpdate,
                        onClick = onInstallClick
                    )
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppDisplayInfo,
    preferredChannel: Channel,
    onClick: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(app.entry.category)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppArtwork(
                app = app.entry,
                modifier = Modifier.size(56.dp),
                cornerRadius = 16
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = app.entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.entry.developer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        text = app.entry.category,
                        background = categoryColor.copy(alpha = 0.12f),
                        contentColor = categoryColor
                    )
                    if (app.hasUpdate) {
                        StatusBadge(
                            text = "Update",
                            background = AccentGreen.copy(alpha = 0.15f),
                            contentColor = AccentGreen
                        )
                    } else if (app.installedVersion != null) {
                        StatusBadge(
                            text = "Installata",
                            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            InstallButton(
                downloadState = app.downloadState,
                isInstalled = app.installedVersion != null,
                hasUpdate = app.hasUpdate,
                onClick = onInstallClick,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
fun AppArtwork(
    app: AppEntry,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20
) {
    Box(
        modifier = modifier
            .clip(ContinuousCornerShape(cornerRadius.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        getCategoryColor(app.category),
                        getCategoryColor(app.category).copy(alpha = 0.6f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIconFor(app.category),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )

        if (app.iconUrl.isNotBlank()) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun AppScreenshotCarousel(
    screenshots: List<String>,
    modifier: Modifier = Modifier
) {
    if (screenshots.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        items(screenshots) { screenshot ->
            ElevatedCard(
                modifier = Modifier
                    .width(220.dp)
                    .aspectRatio(16f / 9f),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    )
                    AsyncImage(
                        model = screenshot,
                        contentDescription = "Screenshot app",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
fun InstallButton(
    downloadState: DownloadState,
    isInstalled: Boolean,
    hasUpdate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (downloadState) {
        is DownloadState.Downloading -> {
            Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(downloadState.progress * 100).toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        is DownloadState.Verifying,
        is DownloadState.AwaitingUserAction,
        is DownloadState.Installing -> {
            Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        is DownloadState.Error -> {
            FilledIconButton(
                onClick = onClick,
                modifier = modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = "Riprova")
            }
        }

        else -> {
            val (icon, containerColor, iconTint) = when {
                hasUpdate -> Triple(Icons.Filled.SystemUpdateAlt, AccentGreen, Color.White)
                isInstalled -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Triple(Icons.Filled.Download, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            }

            if (isInstalled && !hasUpdate) {
                IconButton(
                    onClick = { },
                    enabled = false,
                    modifier = modifier.size(40.dp)
                ) {
                    Icon(icon, contentDescription = "Installata", tint = iconTint)
                }
            } else {
                FilledIconButton(
                    onClick = onClick,
                    modifier = modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = containerColor,
                        contentColor = iconTint
                    )
                ) {
                    Icon(icon, contentDescription = if (hasUpdate) "Aggiorna" else "Installa")
                }
            }
        }
    }
}

@Composable
fun ChannelToggle(
    selectedChannel: Channel,
    onChannelChanged: (Channel) -> Unit,
    hasBeta: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedChannel == Channel.STABLE,
            onClick = { onChannelChanged(Channel.STABLE) },
            label = { Text("Stable") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentGreen.copy(alpha = 0.15f),
                selectedLabelColor = AccentGreen
            )
        )
        FilterChip(
            selected = selectedChannel == Channel.BETA,
            onClick = { onChannelChanged(Channel.BETA) },
            enabled = hasBeta,
            label = { Text("Beta") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentOrange.copy(alpha = 0.15f),
                selectedLabelColor = AccentOrange
            )
        )
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = {
            Text("Cerca nello store")
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Pulisci ricerca")
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}

@Composable
fun OwnershipFilterRow(
    selectedFilter: OwnershipFilter,
    onFilterSelected: (OwnershipFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(OwnershipFilter.entries) { filter ->
            val label = when (filter) {
                OwnershipFilter.ALL -> "Tutte"
                OwnershipFilter.INSTALLED -> "Installate"
                OwnershipFilter.UPDATABLE -> "Aggiornabili"
            }
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("Tutte") }
            )
        }

        items(categories) { category ->
            val color = getCategoryColor(category)
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.15f),
                    selectedLabelColor = color
                )
            )
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = background
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

private fun categoryIconFor(category: String) = when (category) {
    "AI" -> Icons.Filled.AutoAwesome
    "Games" -> Icons.Filled.SportsEsports
    "Tools" -> Icons.Filled.Build
    "Media" -> Icons.Filled.PhotoCamera
    "Social" -> Icons.Filled.People
    else -> Icons.Filled.Apps
}

private fun buildVersionLabel(app: AppDisplayInfo, preferredChannel: Channel): String {
    val preferredVersion = when (preferredChannel) {
        Channel.STABLE -> app.entry.stable?.version
        Channel.BETA -> app.entry.beta?.version ?: app.entry.stable?.version
    }
    return buildString {
        if (app.installedVersion != null) {
            append("Installata v${app.installedVersion}")
        } else {
            append("v${preferredVersion ?: "-"}")
        }
    }
}
