package com.pampa.store.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.pampa.store.viewmodel.StoreViewModel
import com.pampa.store.ui.theme.rememberFluidOverscroll
import com.pampa.store.ui.theme.fluidOverscroll
import com.pampa.store.ui.theme.fluidOverscrollContent

@OptIn(ExperimentalMaterial3Api::class)
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

        Column(modifier = Modifier.fillMaxSize()) {
            if (!compactLayout) {
                // Desktop/wide: Top app bar + filters card
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Pampa Store",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${displayApps.size} app · $installedCount installate · $updatableCount aggiornamenti",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        Surface(
                            modifier = Modifier.padding(start = 8.dp).size(40.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Filled.Store, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refreshApps) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna catalogo")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                // Filters section
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChanged = viewModel::onSearchQueryChanged
                    )
                    OwnershipFilterRow(
                        selectedFilter = ownershipFilter,
                        onFilterSelected = viewModel::onOwnershipFilterSelected
                    )
                    if (categories.isNotEmpty()) {
                        CategoryFilterRow(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = viewModel::onCategorySelected
                        )
                    }
                }
            }

            PullRefreshContainer(
                isRefreshing = isLoading,
                onRefresh = viewModel::refreshApps,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && apps.isEmpty() -> LoadingContent()

                    // Solo un guasto vero porta qui. Le note del catalogo stanno in `warnings` e
                    // non fanno sparire le app che si sono caricate benissimo.
                    error != null && displayApps.isEmpty() -> EmptyState(
                        title = "Connessione non disponibile",
                        subtitle = error ?: "Impossibile caricare il catalogo.",
                        action = {
                            Button(onClick = viewModel::refreshApps) {
                                Text("Riprova")
                            }
                        }
                    )

                    apps.isEmpty() -> EmptyState(
                        title = when {
                            searchQuery.isNotBlank() -> "Nessun risultato"
                            displayApps.isNotEmpty() -> "Nessuna app in questo filtro"
                            else -> "Catalogo vuoto"
                        },
                        subtitle = when {
                            searchQuery.isNotBlank() -> "Prova con un termine diverso o cambia filtri."
                            // Il caso che prima diceva "Connessione non disponibile": il catalogo
                            // c'e', semplicemente nessuna app soddisfa il filtro scelto.
                            displayApps.isNotEmpty() -> "Le app ci sono, ma nessuna rientra in questo filtro."
                            else -> "Non ci sono app pubblicate nello store."
                        }
                    )

                    compactLayout -> {
                        val overscroll = rememberFluidOverscroll()
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .fluidOverscroll(overscroll)
                                .fluidOverscrollContent(overscroll),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Compact header — minimal height
                            item(key = "header") {
                                CompactStoreHeader(
                                    totalApps = displayApps.size,
                                    installedApps = installedCount,
                                    updatableApps = updatableCount,
                                    defaultChannel = defaultChannel,
                                    onRefresh = viewModel::refreshApps
                                )
                            }

                            // Search + filters inline
                            item(key = "search") {
                                SearchBar(
                                    query = searchQuery,
                                    onQueryChanged = viewModel::onSearchQueryChanged
                                )
                            }

                            item(key = "filters") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OwnershipFilterRow(
                                        selectedFilter = ownershipFilter,
                                        onFilterSelected = viewModel::onOwnershipFilterSelected
                                    )
                                    if (categories.isNotEmpty()) {
                                        CategoryFilterRow(
                                            categories = categories,
                                            selectedCategory = selectedCategory,
                                            onCategorySelected = viewModel::onCategorySelected
                                        )
                                    }
                                }
                            }

                            item(key = "feed-title") {
                                CompactCatalogTitle(
                                    resultCount = apps.size,
                                    selectedCategory = selectedCategory,
                                    hasSearch = searchQuery.isNotBlank()
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
                        val overscroll = rememberFluidOverscroll()
                        LazyVerticalGrid(
                            modifier = Modifier
                                .fluidOverscroll(overscroll)
                                .fluidOverscrollContent(overscroll),
                            columns = GridCells.Adaptive(minSize = 260.dp),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
private fun CompactStoreHeader(
    totalApps: Int,
    installedApps: Int,
    updatableApps: Int,
    defaultChannel: Channel,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Filled.Store, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Pampa Store",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = "$totalApps app",
                    background = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (installedApps > 0) {
                    StatusBadge(
                        text = "$installedApps installate",
                        background = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (updatableApps > 0) {
                    StatusBadge(
                        text = "$updatableApps update",
                        background = AccentGreen.copy(alpha = 0.15f),
                        contentColor = AccentGreen
                    )
                }
            }
        }

        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Aggiorna",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactCatalogTitle(
    resultCount: Int,
    selectedCategory: String?,
    hasSearch: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = selectedCategory ?: "Catalogo",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (hasSearch) "$resultCount risultati" else "$resultCount app",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Caricamento catalogo...",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
