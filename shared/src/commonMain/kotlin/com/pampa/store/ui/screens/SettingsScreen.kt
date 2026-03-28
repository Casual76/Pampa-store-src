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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DownloadState
import com.pampa.store.ui.theme.AccentOrange
import com.pampa.store.ui.theme.AccentPink
import com.pampa.store.ui.theme.GlassStrokeDark
import com.pampa.store.ui.theme.GlassStrokeLight
import com.pampa.store.ui.theme.PrimaryViolet
import com.pampa.store.ui.theme.SecondaryTeal
import com.pampa.store.viewmodel.StoreViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: StoreViewModel,
    localStorage: LocalStorage,
    appVersion: String,
    adminPanelVisible: Boolean,
    adminPanelBuildEnabled: Boolean,
    adminPublishingEnabled: Boolean,
    adminDisabledReason: String?,
    onBack: () -> Unit,
    onConfigSaved: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val prefs by localStorage.preferences.collectAsState()
    val storeUpdateState by viewModel.storeUpdateState.collectAsState()
    val storeUpdateDownloadState by viewModel.storeUpdateDownloadState.collectAsState()
    val installPrerequisites by viewModel.installPrerequisites.collectAsState()

    var darkMode by remember(prefs.darkMode) { mutableStateOf(prefs.darkMode) }
    var defaultChannel by remember(prefs.defaultChannel) { mutableStateOf(localStorage.getDefaultChannel()) }
    var storeUpdateChannel by remember(prefs.storeUpdateChannel) { mutableStateOf(localStorage.getStoreUpdateChannel()) }
    var autoCheckStoreUpdates by remember(prefs.storeUpdateAutoCheckEnabled) {
        mutableStateOf(prefs.storeUpdateAutoCheckEnabled)
    }

    val hasChanges = darkMode != prefs.darkMode ||
        defaultChannel.name != prefs.defaultChannel ||
        storeUpdateChannel.name != prefs.storeUpdateChannel ||
        autoCheckStoreUpdates != prefs.storeUpdateAutoCheckEnabled
    val scope = rememberCoroutineScope()
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SecondaryTeal.copy(alpha = if (isDarkTheme) 0.2f else 0.14f),
                            AccentPink.copy(alpha = if (isDarkTheme) 0.12f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            TopAppBar(
                title = {
                    Text("Impostazioni", style = MaterialTheme.typography.headlineSmall)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshApps) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Ricarica catalogo")
                    }
                }
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingsSection(
                title = "Preferenze",
                icon = Icons.Filled.Tune,
                iconColor = SecondaryTeal
            ) {
                SettingsToggle(
                    title = "Tema scuro",
                    subtitle = "Usa il tema scuro in tutta l'app",
                    icon = Icons.Filled.Brightness4,
                    checked = darkMode,
                    onCheckedChange = { darkMode = it }
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Canale predefinito",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Channel.entries.forEach { channel ->
                        FilterChip(
                            selected = defaultChannel == channel,
                            onClick = { defaultChannel = channel },
                            label = { Text(channel.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryViolet.copy(alpha = 0.18f),
                                selectedLabelColor = PrimaryViolet
                            )
                        )
                    }
                }
            }

            SettingsSection(
                title = "Installazione",
                icon = Icons.Filled.SettingsApplications,
                iconColor = AccentOrange
            ) {
                SettingsInfoRow(
                    label = "Stato permessi",
                    value = if (installPrerequisites.canInstallPackages) "Pronto" else "Azione richiesta"
                )
                if (!installPrerequisites.canInstallPackages) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = installPrerequisites.message ?: "Abilita i permessi per installare APK.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::openInstallSettings,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                    ) {
                        Text("Apri impostazioni installazione")
                    }
                }

                Spacer(Modifier.height(16.dp))
                SettingsInfoRow(
                    label = "Percorso download",
                    value = prefs.downloadPath.ifBlank { "Non configurato" }
                )
            }

            SettingsSection(
                title = "Accesso admin",
                icon = Icons.Filled.AdminPanelSettings,
                iconColor = AccentOrange
            ) {
                SettingsInfoRow(
                    label = "Strumenti admin",
                    value = if (adminPanelVisible) "Visibili" else "Nascosti"
                )
                Spacer(Modifier.height(8.dp))
                SettingsInfoRow(
                    label = "Publishing GitHub",
                    value = if (adminPublishingEnabled) "Configurato" else "Non configurato"
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (adminPanelVisible && adminPublishingEnabled) {
                        "La tab Admin e' disponibile e puoi gestire anche l'automazione release."
                    } else if (adminPanelVisible) {
                        adminDisabledReason
                            ?: "Puoi configurare i profili di automazione, ma per pubblicare serve un token GitHub valido."
                    } else {
                        adminDisabledReason
                            ?: "La tab Admin resta nascosta in questa installazione."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!adminPanelBuildEnabled) {
                    Spacer(Modifier.height(12.dp))
                    SettingsToggle(
                        title = "Mostra strumenti admin su questo PC",
                        subtitle = "Sblocca la tab Admin anche nella build stable. Il publish resta protetto dalle credenziali GitHub.",
                        icon = Icons.Filled.AdminPanelSettings,
                        checked = prefs.adminPanelEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                localStorage.setAdminPanelEnabled(enabled)
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (adminPublishingEnabled) {
                            "Dopo l'attivazione trovi i profili di automazione nella tab Admin."
                        } else {
                            "Dopo l'attivazione puoi configurare i profili di automazione dalla tab Admin. Per pubblicare serve PAMPA_GH_TOKEN o un token GitHub equivalente."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsSection(
                title = "Pampa Store",
                icon = Icons.Filled.Info,
                iconColor = AccentPink
            ) {
                SettingsInfoRow(label = "Versione app", value = appVersion)
                Spacer(Modifier.height(8.dp))
                SettingsInfoRow(label = "Developer", value = "Pampa")
                Spacer(Modifier.height(8.dp))
                SettingsInfoRow(label = "Catalogo", value = "Indice GitHub + repository dedicate per ogni app")
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Canale aggiornamenti store",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Channel.entries.forEach { channel ->
                        FilterChip(
                            selected = storeUpdateChannel == channel,
                            onClick = { storeUpdateChannel = channel },
                            label = { Text(channel.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPink.copy(alpha = 0.2f),
                                selectedLabelColor = AccentPink
                            )
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                SettingsToggle(
                    title = "Controllo automatico aggiornamenti store",
                    subtitle = "Verifica le nuove release di Pampa Store all'avvio",
                    icon = Icons.Filled.SystemUpdate,
                    checked = autoCheckStoreUpdates,
                    onCheckedChange = { autoCheckStoreUpdates = it }
                )
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.checkStoreUpdate(storeUpdateChannel) },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !storeUpdateState.isChecking,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
                    ) {
                        Text(if (storeUpdateState.isChecking) "Controllo..." else "Controlla aggiornamenti")
                    }

                    if (storeUpdateState.availableUpdate != null) {
                        Button(
                            onClick = viewModel::downloadAndInstallStoreUpdate,
                            shape = RoundedCornerShape(12.dp),
                            enabled = storeUpdateDownloadState !is DownloadState.Downloading &&
                                storeUpdateDownloadState !is DownloadState.Verifying &&
                                storeUpdateDownloadState !is DownloadState.AwaitingUserAction &&
                                storeUpdateDownloadState !is DownloadState.Installing,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
                        ) {
                            Text("Aggiorna ora")
                        }
                    }
                }

                storeUpdateState.availableUpdate?.let { update ->
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentPink.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = AccentPink)
                                Text(
                                    text = "Aggiornamento disponibile",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPink
                                )
                            }
                            Text("Versione pronta da installare: ${update.versionInfo.version}")
                            Text(
                                text = "Canale: ${storeUpdateChannel.displayName}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (update.versionInfo.changelog.isNotBlank()) {
                                Text(
                                    text = update.versionInfo.changelog,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                storeUpdateState.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!storeUpdateState.isChecking &&
                    storeUpdateState.lastCheckedAt != null &&
                    storeUpdateState.availableUpdate == null &&
                    storeUpdateState.error == null
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Lo store e' gia' aggiornato all'ultima release disponibile.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (storeUpdateDownloadState is DownloadState.Error) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = (storeUpdateDownloadState as DownloadState.Error).message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (storeUpdateDownloadState is DownloadState.AwaitingUserAction) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = (storeUpdateDownloadState as DownloadState.AwaitingUserAction).message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (storeUpdateDownloadState is DownloadState.Installing) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = (storeUpdateDownloadState as DownloadState.Installing).message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsSection(
                title = "Azioni rapide",
                icon = Icons.Filled.Download,
                iconColor = PrimaryViolet
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            viewModel.refreshInstallPrerequisites()
                            viewModel.refreshApps()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
                    ) {
                        Text("Ricarica stato")
                    }

                    if (hasChanges) {
                        Button(
                            onClick = {
                                scope.launch {
                                    localStorage.setDarkMode(darkMode)
                                    localStorage.setDefaultChannel(defaultChannel)
                                    localStorage.setStoreUpdateChannel(storeUpdateChannel)
                                    localStorage.setStoreUpdateAutoCheckEnabled(autoCheckStoreUpdates)
                                    onConfigSaved()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Salva")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    Card(
        modifier = Modifier.border(
            width = 1.dp,
            color = if (isDarkTheme) {
                GlassStrokeDark
            } else {
                GlassStrokeLight
            },
            shape = RoundedCornerShape(24.dp)
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.68f else 0.82f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            iconColor.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(iconColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SettingsInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.padding(start = 12.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SettingsToggle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryViolet
            )
        )
    }
}
