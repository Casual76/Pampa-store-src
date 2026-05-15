package com.pampa.store.ui.screens

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DownloadState
import com.pampa.store.ui.theme.AccentGreen
import com.pampa.store.ui.theme.AccentOrange
import com.pampa.store.ui.theme.AccentPink
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

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        topBar = {
            TopAppBar(
                title = {
                    Text("Impostazioni", style = MaterialTheme.typography.titleLarge)
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(
                title = "Preferenze",
                icon = Icons.Filled.Tune,
                iconColor = MaterialTheme.colorScheme.secondary
            ) {
                SettingsToggle(
                    title = "Tema scuro",
                    subtitle = "Usa il tema scuro in tutta l'app",
                    icon = Icons.Filled.Brightness4,
                    checked = darkMode,
                    onCheckedChange = { darkMode = it }
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Canale predefinito",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Channel.entries.forEach { channel ->
                        FilterChip(
                            selected = defaultChannel == channel,
                            onClick = { defaultChannel = channel },
                            label = { Text(channel.displayName) }
                        )
                    }
                }
            }

            SettingsSection(
                title = "Installazione",
                icon = Icons.Filled.SettingsApplications,
                iconColor = MaterialTheme.colorScheme.tertiary
            ) {
                SettingsInfoRow(
                    label = "Stato permessi",
                    value = if (installPrerequisites.canInstallPackages) "Pronto" else "Azione richiesta"
                )
                if (!installPrerequisites.canInstallPackages) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = installPrerequisites.message ?: "Abilita i permessi per installare APK.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::openInstallSettings,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) {
                        Text("Apri impostazioni installazione")
                    }
                }

                Spacer(Modifier.height(12.dp))
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
                var githubToken by remember(prefs.githubToken) { mutableStateOf(prefs.githubToken) }
                var repoOwner by remember(prefs.repoOwner) { mutableStateOf(prefs.repoOwner) }
                var repoName by remember(prefs.repoName) { mutableStateOf(prefs.repoName) }
                var showToken by remember { mutableStateOf(false) }

                val hasGitHubChanges = githubToken != prefs.githubToken ||
                    repoOwner != prefs.repoOwner ||
                    repoName != prefs.repoName

                SettingsInfoRow(
                    label = "Publishing GitHub",
                    value = if (adminPublishingEnabled) "Configurato" else "Non configurato"
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = githubToken,
                    onValueChange = { githubToken = it },
                    label = { Text("GitHub Personal Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showToken) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(Modifier.height(10.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = repoOwner,
                        onValueChange = { repoOwner = it },
                        label = { Text("Repo Owner") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    OutlinedTextField(
                        value = repoName,
                        onValueChange = { repoName = it },
                        label = { Text("Repo Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (hasGitHubChanges) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                localStorage.setGitHubCredentials(githubToken, repoOwner, repoName)
                                onConfigSaved()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Salva credenziali GitHub")
                    }
                }

                Spacer(Modifier.height(12.dp))
                
                SettingsToggle(
                    title = "Mostra strumenti admin",
                    subtitle = "Sblocca la tab Admin anche nella build stable.",
                    icon = Icons.Filled.AdminPanelSettings,
                    checked = prefs.adminPanelEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            localStorage.setAdminPanelEnabled(enabled)
                        }
                    }
                )
            }

            SettingsSection(
                title = "Pampa Store",
                icon = Icons.Filled.Info,
                iconColor = MaterialTheme.colorScheme.primary
            ) {
                SettingsInfoRow(label = "Versione app", value = appVersion)
                Spacer(Modifier.height(6.dp))
                SettingsInfoRow(label = "Developer", value = "Pampa")
                Spacer(Modifier.height(6.dp))
                SettingsInfoRow(label = "Catalogo", value = "Indice GitHub + repository dedicate per ogni app")
                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Canale aggiornamenti store",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Channel.entries.forEach { channel ->
                        FilterChip(
                            selected = storeUpdateChannel == channel,
                            onClick = { storeUpdateChannel = channel },
                            label = { Text(channel.displayName) }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                SettingsToggle(
                    title = "Controllo automatico aggiornamenti store",
                    subtitle = "Verifica le nuove release di Pampa Store all'avvio",
                    icon = Icons.Filled.SystemUpdate,
                    checked = autoCheckStoreUpdates,
                    onCheckedChange = { autoCheckStoreUpdates = it }
                )
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.checkStoreUpdate(storeUpdateChannel) },
                        shape = MaterialTheme.shapes.medium,
                        enabled = !storeUpdateState.isChecking
                    ) {
                        Text(if (storeUpdateState.isChecking) "Controllo..." else "Controlla aggiornamenti")
                    }

                    if (storeUpdateState.availableUpdate != null) {
                        Button(
                            onClick = viewModel::downloadAndInstallStoreUpdate,
                            shape = MaterialTheme.shapes.medium,
                            enabled = storeUpdateDownloadState !is DownloadState.Downloading &&
                                storeUpdateDownloadState !is DownloadState.Verifying &&
                                storeUpdateDownloadState !is DownloadState.AwaitingUserAction &&
                                storeUpdateDownloadState !is DownloadState.Installing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Aggiorna ora")
                        }
                    }
                }

                storeUpdateState.availableUpdate?.let { update ->
                    Spacer(Modifier.height(12.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Aggiornamento disponibile",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                "Versione: ${update.versionInfo.version}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (update.versionInfo.changelog.isNotBlank()) {
                                Text(
                                    text = update.versionInfo.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                storeUpdateState.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!storeUpdateState.isChecking &&
                    storeUpdateState.lastCheckedAt != null &&
                    storeUpdateState.availableUpdate == null &&
                    storeUpdateState.error == null
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Lo store e' gia' aggiornato all'ultima release disponibile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (storeUpdateDownloadState is DownloadState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = (storeUpdateDownloadState as DownloadState.Error).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (storeUpdateDownloadState is DownloadState.AwaitingUserAction) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = (storeUpdateDownloadState as DownloadState.AwaitingUserAction).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (storeUpdateDownloadState is DownloadState.Installing) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = (storeUpdateDownloadState as DownloadState.Installing).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Save actions
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Salva modifiche")
                }
            }

            Spacer(Modifier.height(16.dp))
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
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = MaterialTheme.shapes.small,
                    color = iconColor.copy(alpha = 0.12f),
                    contentColor = iconColor
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
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
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
