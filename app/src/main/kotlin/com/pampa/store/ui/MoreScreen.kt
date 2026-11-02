package com.pampa.store.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pampa.store.BuildConfig
import com.pampa.store.data.AccentSource
import com.pampa.store.data.AppThemeMode
import com.pampa.store.data.Channel
import com.pampa.store.data.StoreUiState
import com.pampa.store.data.enumOrDefault
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.fluidAppearanceSection
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

@Composable
fun MoreScreen(
    state: StoreUiState,
    onRefresh: () -> Unit,
    /** La sezione «Aspetto» riscrive le impostazioni dell'engine intere: chi le riceve le traduce in preferenze. */
    onAppearanceChange: (EngineSettings) -> Unit,
    onDefaultChannel: (Channel) -> Unit,
    onStoreChannel: (Channel) -> Unit,
    onAutoCheck: (Boolean) -> Unit,
    onCheckStoreUpdate: () -> Unit,
    onInstallStoreUpdate: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val preferences = state.preferences
    val themeMode = enumOrDefault(preferences.themeMode, AppThemeMode.SYSTEM)
    val defaultChannel = enumOrDefault(preferences.defaultChannel, Channel.STABLE)
    val storeChannel = enumOrDefault(preferences.storeUpdateChannel, Channel.STABLE)

    FluidScreen(
        title = "Altro",
        subtitle = "Il tuo Store, come lo vuoi tu.",
        horizontalPadding = LocalStoreHorizontalPadding.current,
        titleFacets = listOf(themeMode.label, selectedAccentLabel(state)),
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        ambient = FluidAmbient(tone = FluidHeroTone.Secondary, motif = FluidHeroMotif.Dots),
    ) {
        // Tema, accento, aptica: la sezione e' dell'engine (la stessa di ogni app Pampa), e lo Store la
        // alimenta con quello che salva da sempre. Nebula 76 e' il marchio: il selettore offre gli altri.
        fluidAppearanceSection(
            settings = preferences.toEngineSettings(),
            onSettingsChange = onAppearanceChange,
            brand = PampaBrand,
            presets = PampaSelectablePresets,
            glass = true,
        )

        item { FluidSectionHeader(title = "Installazione", detail = "Canali e autorizzazioni Android") }
        item {
            FluidListGroup(glass = true) {
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
                        FluidStatusBadge(
                            if (state.prerequisites.canInstallPackages) "Attiva" else "Configura",
                            tone = if (state.prerequisites.canInstallPackages) FluidTone.Success else FluidTone.Warning,
                        )
                    },
                    onClick = onOpenInstallSettings,
                )
            }
        }

        item { FluidSectionHeader(title = "Pampa Store", detail = "Aggiornamenti e diagnostica") }
        item {
            val storeUpdate = state.storeUpdate
            FluidListGroup(glass = true) {
                // Il controllo a mano vive qui e non solo dietro il refresh della schermata
                // Aggiornamenti: questa è la sezione in cui si viene a cercarlo, ed è l'unico
                // punto in cui la risposta — anche quando è "niente di nuovo" — viene detta.
                FluidListRow(
                    title = if (storeUpdate != null) {
                        "Aggiorna alla ${storeUpdate.version.version}"
                    } else {
                        "Controlla aggiornamenti"
                    },
                    subtitle = when {
                        storeUpdate != null -> "Tocca per scaricare e installare."
                        state.isRefreshing -> "Controllo in corso…"
                        !preferences.autoCheckStoreUpdates ->
                            "Il controllo automatico è spento: da qui lo chiedi a mano."
                        else -> "Nessuna versione più recente sul canale ${storeChannel.label}."
                    },
                    tone = if (storeUpdate != null) FluidTone.Primary else FluidTone.Neutral,
                    leading = {
                        Icon(
                            if (storeUpdate != null) Icons.Rounded.Download else Icons.Rounded.Refresh,
                            contentDescription = null,
                        )
                    },
                    badge = {
                        FluidStatusBadge(
                            if (storeUpdate != null) "Aggiorna" else "Controlla",
                            tone = if (storeUpdate != null) FluidTone.Primary else FluidTone.Info,
                        )
                    },
                    onClick = if (storeUpdate != null) onInstallStoreUpdate else onCheckStoreUpdate,
                )
                FluidListDivider()
                FluidToggleRow(
                    title = "Controllo automatico",
                    subtitle = "Cerca una nuova versione dello Store all’avvio e ogni sei ore, " +
                        "anche a Store chiuso.",
                    checked = preferences.autoCheckStoreUpdates,
                    onCheckedChange = onAutoCheck,
                    tone = FluidTone.Primary,
                    leading = { Icon(Icons.Rounded.Sync, contentDescription = null) },
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
                    title = "Notifiche",
                    subtitle = if (state.notificationsEnabled) {
                        "Ti avviso quando un’app installata — o lo Store stesso — ha una versione nuova."
                    } else {
                        "Disattivate: il controllo continua, ma nessun avviso arriva."
                    },
                    meta = "Controllo ogni 6 ore",
                    tone = if (state.notificationsEnabled) FluidTone.Success else FluidTone.Warning,
                    leading = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                    badge = {
                        FluidStatusBadge(
                            if (state.notificationsEnabled) "Attive" else "Configura",
                            tone = if (state.notificationsEnabled) FluidTone.Success else FluidTone.Warning,
                        )
                    },
                    // Il permesso si concede una volta sola: dopo un rifiuto Android non mostra piu'
                    // il dialogo, e l'unica strada che resta sono le impostazioni di sistema.
                    onClick = onOpenNotificationSettings,
                )
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
