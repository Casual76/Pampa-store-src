package com.pampa.store.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pampa.store.BuildConfig
import com.pampa.store.data.AccentPresetId
import com.pampa.store.data.AccentSource
import com.pampa.store.data.AppThemeMode
import com.pampa.store.data.Channel
import com.pampa.store.data.StoreUiState
import com.pampa.store.data.enumOrDefault
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone

@Composable
fun MoreScreen(
    state: StoreUiState,
    onRefresh: () -> Unit,
    onThemeMode: (AppThemeMode) -> Unit,
    onAccentSource: (AccentSource) -> Unit,
    onAccentPreset: (AccentPresetId) -> Unit,
    onDefaultChannel: (Channel) -> Unit,
    onStoreChannel: (Channel) -> Unit,
    onAutoCheck: (Boolean) -> Unit,
    onOpenInstallSettings: () -> Unit,
    onInstallStoreUpdate: () -> Unit,
) {
    val preferences = state.preferences
    val themeMode = enumOrDefault(preferences.themeMode, AppThemeMode.SYSTEM)
    val accentSource = enumOrDefault(preferences.accentSource, AccentSource.PRESET)
    val defaultChannel = enumOrDefault(preferences.defaultChannel, Channel.STABLE)
    val storeChannel = enumOrDefault(preferences.storeUpdateChannel, Channel.STABLE)

    FluidScreen(
        title = "Altro",
        subtitle = "Il tuo Store, come lo vuoi tu.",
        horizontalPadding = LocalStoreHorizontalPadding.current,
        titleFacets = listOf(themeMode.label, selectedAccentLabel(state)),
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
    ) {
        item { FluidSectionHeader(title = "Aspetto", detail = "Tema, accento e colori dinamici") }
        item {
            FluidListGroup(modifier = Modifier.testTag("appearance-settings")) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FluidListRow(
                        title = "Tema",
                        subtitle = "Segui il sistema oppure scegli chiaro o scuro.",
                        tone = FluidTone.Primary,
                        leading = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                    )
                    FluidSegmentedControl(
                        options = AppThemeMode.entries,
                        selected = themeMode,
                        onSelect = onThemeMode,
                        label = AppThemeMode::label,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            FluidSegmentedControl(
                options = AccentSource.entries,
                selected = accentSource,
                onSelect = onAccentSource,
                label = AccentSource::label,
            )
        }
        if (accentSource == AccentSource.PRESET) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AccentPresetId.entries, key = AccentPresetId::storageName) { preset ->
                        FluidChip(
                            label = preset.label,
                            selected = preferences.selectedAccentPreset() == preset,
                            onClick = { onAccentPreset(preset) },
                            leading = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                        )
                    }
                }
            }
        } else {
            item {
                FluidSectionFootnote(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Material You usa la palette del wallpaper; puoi tornare ai preset in qualsiasi momento."
                    } else {
                        "Material You richiede Android 12: su questo dispositivo viene usato Nebula 76."
                    },
                )
            }
        }

        item { FluidSectionHeader(title = "Installazione", detail = "Canali e autorizzazioni Android") }
        item {
            FluidListGroup {
                FluidListRow(
                    title = "Canale predefinito",
                    subtitle = "Usato quando apri una nuova app dal catalogo.",
                    tone = FluidTone.Info,
                    leading = { Icon(Icons.Rounded.Download, contentDescription = null) },
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    FluidSegmentedControl(
                        options = Channel.entries,
                        selected = defaultChannel,
                        onSelect = onDefaultChannel,
                        label = Channel::label,
                    )
                }
                FluidListDivider()
                FluidListRow(
                    title = "Origini consentite",
                    subtitle = if (state.prerequisites.canInstallPackages) {
                        "Pampa Store può aprire il Package Installer."
                    } else {
                        "Autorizzazione necessaria per installare APK."
                    },
                    tone = if (state.prerequisites.canInstallPackages) FluidTone.Success else FluidTone.Warning,
                    leading = { Icon(Icons.Rounded.Security, contentDescription = null) },
                    badge = {
                        StatusPill(
                            if (state.prerequisites.canInstallPackages) "Attiva" else "Configura",
                            if (state.prerequisites.canInstallPackages) FluidTone.Success else FluidTone.Warning,
                        )
                    },
                    onClick = onOpenInstallSettings,
                )
            }
        }

        item { FluidSectionHeader(title = "Pampa Store", detail = "Aggiornamenti e diagnostica") }
        item {
            FluidListGroup {
                FluidListRow(
                    title = "Controllo automatico",
                    subtitle = "Cerca una nuova versione dello Store all’avvio.",
                    tone = FluidTone.Primary,
                    leading = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                    badge = {
                        FluidSwitch(
                            checked = preferences.autoCheckStoreUpdates,
                            onCheckedChange = onAutoCheck,
                        )
                    },
                )
                FluidListDivider()
                FluidListRow(
                    title = "Canale dello Store",
                    subtitle = storeChannel.label,
                    tone = FluidTone.Neutral,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    FluidSegmentedControl(
                        options = Channel.entries,
                        selected = storeChannel,
                        onSelect = onStoreChannel,
                        label = Channel::label,
                    )
                }
                FluidListDivider()
                FluidListRow(
                    title = "Versione installata",
                    subtitle = BuildConfig.VERSION_NAME,
                    tone = FluidTone.Neutral,
                    leading = { Icon(Icons.Rounded.Info, contentDescription = null) },
                )
                FluidListDivider()
                FluidListRow(
                    title = "Catalogo",
                    subtitle = "${state.apps.size} app • ${state.installedCount} installate • ${state.updateCount} aggiornabili",
                    meta = if (state.isUsingCache) "Cache offline attiva" else "Sincronizzazione online",
                    tone = if (state.isUsingCache) FluidTone.Warning else FluidTone.Success,
                    leading = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                )
            }
        }

        state.storeUpdate?.let { update ->
            item {
                InlineNotice(
                    title = "Versione ${update.version.version} disponibile",
                    message = update.version.changelog.ifBlank { "Aggiornamento pronto da installare." },
                    isError = false,
                )
            }
            item {
                FluidButton(
                    text = "Scarica e installa",
                    onClick = onInstallStoreUpdate,
                    style = FluidButtonStyle.Filled,
                    fillWidth = true,
                )
            }
        }

        item {
            FluidSectionFootnote(
                "Pampa Store distribuisce esclusivamente app Android. Le vecchie build Windows sono storiche e non più supportate.",
            )
        }

        // Le opere di terze parti che il Fluid Engine porta dentro l'APK — il vetro di Kyant e il
        // font Inter. Le rispettive licenze chiedono che l'avviso viaggi con la distribuzione, e un
        // file di licenza dentro un repository non e' la distribuzione. L'elenco vive nell'engine,
        // cosi' non resta indietro qui mentre viene aggiornato altrove.
        fluidLicensesSection()
    }
}

private fun selectedAccentLabel(state: StoreUiState): String =
    if (enumOrDefault(state.preferences.accentSource, AccentSource.PRESET) == AccentSource.DYNAMIC) {
        "Material You"
    } else {
        state.preferences.selectedAccentPreset().label
    }
