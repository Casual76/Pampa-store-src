package com.pampa.store.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.Channel
import com.pampa.store.ui.components.AppCard
import com.pampa.store.ui.components.AppListItem
import com.pampa.store.ui.components.CategoryFilterRow
import com.pampa.store.ui.components.EmptyState
import com.pampa.store.ui.components.OwnershipFilterRow
import com.pampa.store.ui.components.PullRefreshContainer
import com.pampa.store.ui.components.SearchBar
import com.pampa.store.ui.components.StatusBadge
import com.pampa.store.ui.theme.AccentGreen
import com.pampa.store.ui.theme.AccentOrange
import com.pampa.store.ui.theme.GlassStrokeDark
import com.pampa.store.ui.theme.GlassStrokeLight
import com.pampa.store.ui.theme.PrimaryViolet
import com.pampa.store.ui.theme.SecondaryTeal
import com.pampa.store.viewmodel.StoreViewModel

@Composable
fun HomeScreen(
    viewModel: StoreViewModel,
    onAppClicked: (AppEntry) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.filteredApps.collectAsState()
    val displayApps by viewModel.displayApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val ownershipFilter by viewModel.ownershipFilter.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val defaultChannel by viewModel.defaultChannel.collectAsState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        val compactLayout = maxWidth < 600.dp
        val installedCount = displayApps.count { it.installedVersion != null }
        val updatableCount = displayApps.count { it.hasUpdate }
        val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f

        Column(modifier = Modifier.fillMaxSize()) {
            if (!compactLayout) {
                HeaderSection(
                    totalApps = displayApps.size,
                    installedApps = installedCount,
                    updatableApps = updatableCount,
                    defaultChannel = defaultChannel,
                    onRefresh = viewModel::refreshApps
                )
                LibraryFiltersCard(
                    query = searchQuery,
                    onQueryChanged = viewModel::onSearchQueryChanged,
                    ownershipFilter = ownershipFilter,
                    onOwnershipFilterSelected = viewModel::onOwnershipFilterSelected,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = viewModel::onCategorySelected,
                    isDarkTheme = isDarkTheme
                )
            }

            PullRefreshContainer(
                isRefreshing = isLoading,
                onRefresh = viewModel::refreshApps,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && apps.isEmpty() -> LoadingContent()
                    error != null && apps.isEmpty() -> EmptyState(
                        title = "Connessione non disponibile",
                        subtitle = error ?: "Impossibile caricare il catalogo.",
                        action = {
                            Button(
                                onClick = viewModel::refreshApps,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
                            ) {
                                Text("Riprova")
                            }
                        }
                    )

                    apps.isEmpty() -> EmptyState(
                        title = if (searchQuery.isNotBlank()) "Nessun risultato" else "Catalogo vuoto",
                        subtitle = if (searchQuery.isNotBlank()) {
                            "Prova con un termine diverso o cambia filtri."
                        } else {
                            "Non ci sono app pubblicate nello store."
                        }
                    )

                    compactLayout -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "header") {
                                HeaderSection(
                                    totalApps = displayApps.size,
                                    installedApps = installedCount,
                                    updatableApps = updatableCount,
                                    defaultChannel = defaultChannel,
                                    onRefresh = viewModel::refreshApps,
                                    compact = true
                                )
                            }
                            item(key = "filters") {
                                LibraryFiltersCard(
                                    query = searchQuery,
                                    onQueryChanged = viewModel::onSearchQueryChanged,
                                    ownershipFilter = ownershipFilter,
                                    onOwnershipFilterSelected = viewModel::onOwnershipFilterSelected,
                                    categories = categories,
                                    selectedCategory = selectedCategory,
                                    onCategorySelected = viewModel::onCategorySelected,
                                    isDarkTheme = isDarkTheme,
                                    compact = true
                                )
                            }
                            items(apps, key = { it.entry.id }) { app ->
                                AppListItem(
                                    app = app,
                                    preferredChannel = app.installedChannel ?: defaultChannel,
                                    onClick = { onAppClicked(app.entry) },
                                    onInstallClick = {
                                        viewModel.downloadAndInstall(app.entry.id, app.installedChannel ?: defaultChannel)
                                    }
                                )
                            }
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 240.dp),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(apps, key = { it.entry.id }) { app ->
                                AppCard(
                                    app = app,
                                    preferredChannel = app.installedChannel ?: defaultChannel,
                                    onClick = { onAppClicked(app.entry) },
                                    onInstallClick = {
                                        viewModel.downloadAndInstall(app.entry.id, app.installedChannel ?: defaultChannel)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    totalApps: Int,
    installedApps: Int,
    updatableApps: Int,
    defaultChannel: Channel,
    onRefresh: () -> Unit,
    compact: Boolean = false
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    val outerShape = RoundedCornerShape(if (compact) 24.dp else 30.dp)
    val iconContainerSize = if (compact) 50.dp else 60.dp
    val iconSize = if (compact) 24.dp else 28.dp
    val contentPadding = if (compact) 18.dp else 22.dp
    val titleStyle = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .border(
                width = 1.dp,
                color = if (isDarkTheme) {
                    GlassStrokeDark
                } else {
                    GlassStrokeLight
                },
                shape = outerShape
            ),
        shape = outerShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.78f else 0.94f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            AccentOrange.copy(alpha = if (isDarkTheme) 0.2f else 0.14f),
                            PrimaryViolet.copy(alpha = if (isDarkTheme) 0.18f else 0.12f),
                            SecondaryTeal.copy(alpha = if (isDarkTheme) 0.14f else 0.08f)
                        )
                    )
                )
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(iconContainerSize)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(AccentOrange, com.pampa.store.ui.theme.AccentPink)
                            ),
                            shape = RoundedCornerShape(if (compact) 18.dp else 20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Store,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(
                        text = "Catalogo personale",
                        background = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.5f else 0.78f),
                        contentColor = AccentOrange
                    )
                    Text(
                        text = "Pampa Store",
                        style = titleStyle
                    )
                    Text(
                        text = "Le tue app, curate e ordinate come una libreria vera. Meno rumore, piu' controllo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (compact) 2 else 3
                    )
                }

                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Aggiorna catalogo",
                        tint = PrimaryViolet
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricPanel(
                    title = "Catalogo",
                    value = totalApps.toString(),
                    accent = PrimaryViolet,
                    modifier = Modifier.weight(1f),
                    compact = compact
                )
                MetricPanel(
                    title = "Installate",
                    value = installedApps.toString(),
                    accent = AccentGreen,
                    modifier = Modifier.weight(1f),
                    compact = compact
                )
                MetricPanel(
                    title = "Da aggiornare",
                    value = updatableApps.toString(),
                    accent = AccentOrange,
                    modifier = Modifier.weight(1f),
                    compact = compact
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Canale predefinito: ${defaultChannel.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricPanel(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 18.dp else 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 10.dp else 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LibraryFiltersCard(
    query: String,
    onQueryChanged: (String) -> Unit,
    ownershipFilter: com.pampa.store.data.model.OwnershipFilter,
    onOwnershipFilterSelected: (com.pampa.store.data.model.OwnershipFilter) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    isDarkTheme: Boolean,
    compact: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .border(
                width = 1.dp,
                color = if (isDarkTheme) {
                    GlassStrokeDark
                } else {
                    GlassStrokeLight
                },
                shape = RoundedCornerShape(if (compact) 20.dp else 24.dp)
            ),
        shape = RoundedCornerShape(if (compact) 20.dp else 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.62f else 0.78f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 16.dp,
                vertical = if (compact) 12.dp else 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Filtra la libreria",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            SearchBar(
                query = query,
                onQueryChanged = onQueryChanged
            )
            OwnershipFilterRow(
                selectedFilter = ownershipFilter,
                onFilterSelected = onOwnershipFilterSelected
            )
            if (categories.isNotEmpty()) {
                CategoryFilterRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = PrimaryViolet)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Caricamento catalogo...",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
