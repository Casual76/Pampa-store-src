package com.pampa.store.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pampa.store.data.model.AppDisplayInfo
import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DownloadState
import com.pampa.store.data.model.OwnershipFilter
import com.pampa.store.ui.theme.AccentGreen
import com.pampa.store.ui.theme.AccentOrange
import com.pampa.store.ui.theme.GlassStrokeDark
import com.pampa.store.ui.theme.GlassStrokeLight
import com.pampa.store.ui.theme.PrimaryViolet
import com.pampa.store.ui.theme.getCategoryColor

@Composable
fun AppCard(
    app: AppDisplayInfo,
    preferredChannel: Channel,
    onClick: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.74f else 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                getCategoryColor(app.entry.category).copy(alpha = 0.42f),
                                AccentOrange.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = categoryIconFor(app.entry.category),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .size(96.dp)
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.76f else 0.9f)
                ) {
                    Text(
                        text = app.entry.category,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = getCategoryColor(app.entry.category),
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppArtwork(
                        app = app.entry,
                        modifier = Modifier.size(78.dp),
                        cornerRadius = 24
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = app.entry.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.entry.developer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                    }
                }

                if (app.hasUpdate) {
                    StatusBadge(
                        text = "Aggiornamento",
                        background = AccentGreen.copy(alpha = 0.96f),
                        contentColor = Color(0xFF11261B),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = app.entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = buildVersionLabel(app, preferredChannel),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = app.entry.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = getCategoryColor(app.entry.category)
                        )
                    }

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
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isDarkTheme) {
                    GlassStrokeDark
                } else {
                    GlassStrokeLight
                },
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.74f else 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                getCategoryColor(app.entry.category).copy(alpha = 0.34f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
                    .padding(8.dp)
            ) {
                AppArtwork(
                    app = app.entry,
                    modifier = Modifier.size(64.dp),
                    cornerRadius = 18
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = app.entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = app.entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        StatusBadge(
                            text = app.entry.category,
                            background = getCategoryColor(app.entry.category).copy(alpha = 0.12f),
                            contentColor = getCategoryColor(app.entry.category)
                        )
                    if (app.installedVersion != null) {
                        StatusBadge(
                            text = "Installata",
                            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (app.hasUpdate) {
                        StatusBadge(
                            text = "Da aggiornare",
                            background = AccentGreen.copy(alpha = 0.2f),
                            contentColor = AccentGreen
                        )
                    }
                }
            }

            InstallButton(
                downloadState = app.downloadState,
                isInstalled = app.installedVersion != null,
                hasUpdate = app.hasUpdate,
                onClick = onInstallClick
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
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        getCategoryColor(app.category),
                        getCategoryColor(app.category).copy(alpha = 0.62f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIconFor(app.category),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
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
            Card(
                modifier = Modifier
                    .width(232.dp)
                    .aspectRatio(16f / 9f)
                    .border(
                        width = 1.dp,
                        color = if (MaterialTheme.colorScheme.background.red < 0.3f) {
                            GlassStrokeDark
                        } else {
                            GlassStrokeLight
                        },
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
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
                    color = PrimaryViolet
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
                    color = AccentOrange
                )
            }
        }

        is DownloadState.Error -> {
            FilledIconButton(
                onClick = onClick,
                modifier = modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = "Riprova")
            }
        }

        else -> {
            val (icon, containerColor, iconTint) = when {
                hasUpdate -> Triple(Icons.Filled.SystemUpdateAlt, AccentGreen, Color.Black)
                isInstalled -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Triple(Icons.Filled.Download, PrimaryViolet, Color.White)
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
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = if (isDarkTheme) {
                    GlassStrokeDark
                } else {
                    GlassStrokeLight
                },
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.7f else 0.82f),
                        MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.6f else 0.88f)
                    )
                )
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChannelChip(
            label = "Stable",
            selected = selectedChannel == Channel.STABLE,
            enabled = true,
            color = AccentGreen,
            onClick = { onChannelChanged(Channel.STABLE) }
        )
        ChannelChip(
            label = "Beta",
            selected = selectedChannel == Channel.BETA,
            enabled = hasBeta,
            color = AccentOrange,
            onClick = { onChannelChanged(Channel.BETA) }
        )
    }
}

@Composable
private fun ChannelChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) color.copy(alpha = 0.22f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                selected -> color
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
        shape = RoundedCornerShape(22.dp),
        placeholder = {
            Text("Cerca app, categorie o developer")
        },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = AccentOrange)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Pulisci ricerca")
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentOrange,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
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
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PrimaryViolet.copy(alpha = 0.24f),
                    selectedLabelColor = PrimaryViolet,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                label = { Text("Tutte le categorie") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryViolet.copy(alpha = 0.24f),
                    selectedLabelColor = PrimaryViolet,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        items(categories) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = getCategoryColor(category).copy(alpha = 0.18f),
                    selectedLabelColor = getCategoryColor(category),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = RoundedCornerShape(999.dp),
        color = background
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
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
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            AccentOrange.copy(alpha = 0.24f),
                            PrimaryViolet.copy(alpha = 0.18f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
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
            append("Ultima v${preferredVersion ?: "-"}")
        }
    }
}
