package com.pampa.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pampa.store.data.OwnershipFilter
import com.pampa.store.data.StoreUiState
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidLoadingBlock
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidTone

@Composable
fun CatalogScreen(
    state: StoreUiState,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onOwnership: (OwnershipFilter) -> Unit,
    onOpenApp: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
) {
    FluidScreen(
        title = "Catalogo",
        subtitle = "Tutto ciò che puoi installare.",
        horizontalPadding = LocalStoreHorizontalPadding.current,
        titleFacets = listOf("${state.visibleApps.size} risultati", state.ownershipFilter.label),
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        ambient = FluidAmbient(
            tone = FluidHeroTone.SecondaryToTertiary,
            motif = FluidHeroMotif.Cards,
        ),
        actions = {
            FluidBarAction(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Aggiorna catalogo",
                onClick = onRefresh,
            )
        },
    ) {
        item {
            FluidTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                placeholder = "Cerca app, categorie o sviluppatori",
                leading = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.testTag("catalog-search"),
            )
        }
        item {
            FluidSegmentedControl(
                options = OwnershipFilter.entries,
                selected = state.ownershipFilter,
                onSelect = onOwnership,
                label = OwnershipFilter::label,
                modifier = Modifier.testTag("catalog-ownership-filter"),
            )
        }
        if (state.categories.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FluidChip(
                            label = "Tutte le categorie",
                            selected = state.selectedCategory == null,
                            onClick = { onCategory(null) },
                        )
                    }
                    items(state.categories, key = { it }) { category ->
                        FluidChip(
                            label = category,
                            selected = state.selectedCategory == category,
                            onClick = { onCategory(category) },
                        )
                    }
                }
            }
        }

        if (state.isLoading && state.apps.isEmpty()) {
            item { FluidLoadingBlock() }
        } else if (state.error != null && state.apps.isEmpty()) {
            item {
                FluidInlineMessage(
                    title = "Il catalogo non si è caricato",
                    message = state.error,
                    tone = FluidTone.Danger,
                    onClick = onRefresh,
                    leading = { Icon(noticeIcon(FluidTone.Danger), contentDescription = null) },
                )
            }
        } else if (state.visibleApps.isEmpty()) {
            item {
                FluidEmptyState(
                    title = "Nessun risultato",
                    detail = "Prova una ricerca diversa o cambia i filtri.",
                )
            }
        } else {
            item {
                FluidSectionHeader(
                    title = "App disponibili",
                    detail = "${state.visibleApps.size} nel filtro corrente",
                )
            }
            item {
                StoreAppGroup(
                    apps = state.visibleApps,
                    onOpenApp = onOpenApp,
                    modifier = Modifier.testTag("catalog-app-list"),
                    onLaunchApp = onLaunchApp,
                )
            }
        }

        state.warning?.let { warning ->
            item {
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
