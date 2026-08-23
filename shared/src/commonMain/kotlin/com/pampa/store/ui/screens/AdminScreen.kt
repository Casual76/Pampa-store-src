package com.pampa.store.ui.screens

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.AppPlatform
import com.pampa.store.data.model.ApkValidationMetadata
import com.pampa.store.data.model.ApkValidationResult
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DEFAULT_GITHUB_MANIFEST_PATH
import com.pampa.store.data.model.STORE_APP_ID
import com.pampa.store.data.model.StoreIndexManifest
import com.pampa.store.data.model.VersionInfo
import com.pampa.store.platform.PlatformFileDropButton
import com.pampa.store.platform.getCurrentPlatform
import com.pampa.store.platform.rememberFilePicker
import com.pampa.store.ui.components.AppArtwork
import com.pampa.store.ui.components.EmptyState
import com.pampa.store.ui.components.StatusBadge
import com.pampa.store.ui.theme.ContinuousCornerShape
import com.pampa.store.ui.theme.AccentGreen
import com.pampa.store.ui.theme.AccentOrange
import com.pampa.store.viewmodel.AdminViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val manifest by viewModel.manifest.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val message by viewModel.message.collectAsState()
    val storeIndex by viewModel.storeIndex.collectAsState()
    val showEditor by viewModel.showEditor.collectAsState()
    val editingApp by viewModel.editingApp.collectAsState()
    val publishingEnabled by viewModel.publishingEnabled.collectAsState()
    val publishingDisabledReason by viewModel.publishingDisabledReason.collectAsState()
    val automationProfiles by viewModel.automationProfiles.collectAsState()
    val pendingReleaseDrafts by viewModel.pendingReleaseDrafts.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "${manifest.apps.size} app nello store",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna")
                    }
                }
            )
        },
        floatingActionButton = {
            if (publishingEnabled && !showEditor) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::startNewApp,
                    shape = ContinuousCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nuova app") }
                )
            }
        }
    ) { innerPadding ->
        if (showEditor && editingApp != null) {
            AdminEditor(
                app = editingApp!!,
                isSaving = isSaving,
                canPublish = publishingEnabled,
                onSave = viewModel::saveApp,
                onCancel = viewModel::closeEditor,
                onAddVersion = { appId, channel, version, apkPath, exePath ->
                    viewModel.addVersion(appId, channel, version, apkPath, exePath)
                },
                onPromoteBetaToStable = viewModel::promoteAppBetaToStable,
                validateApk = { path -> viewModel.validateApk(path) },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            AdminOverview(
                apps = manifest.apps,
                storeIndex = storeIndex,
                automationProfiles = automationProfiles,
                pendingReleaseDrafts = pendingReleaseDrafts,
                isLoading = isLoading,
                isSaving = isSaving,
                canPublish = publishingEnabled,
                disabledReason = publishingDisabledReason,
                onEdit = viewModel::editApp,
                onDelete = { app -> viewModel.deleteApp(app.id) },
                automationSettingsEnabled = true,
                onSaveAutomationProfile = viewModel::saveAutomationProfile,
                onDeleteAutomationProfile = viewModel::deleteAutomationProfile,
                onToggleAutomationProfile = viewModel::setAutomationProfileEnabled,
                onPublishAutomationDraft = viewModel::publishAutomationDraft,
                onDeferAutomationDraft = viewModel::deferAutomationDraft,
                onDismissAutomationDraft = viewModel::dismissAutomationDraft,
                onRetryAutomationDraft = viewModel::retryAutomationDraft,
                onPublishStoreUpdate = viewModel::publishStoreUpdate,
                onPromoteStoreBetaToStable = viewModel::promoteStoreBetaToStable,
                validateApk = { path -> viewModel.validateApk(path) },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun AdminOverview(
    apps: List<AppEntry>,
    storeIndex: StoreIndexManifest,
    automationProfiles: List<com.pampa.store.data.model.ReleaseAutomationProfile>,
    pendingReleaseDrafts: List<com.pampa.store.data.model.PendingReleaseDraft>,
    isLoading: Boolean,
    isSaving: Boolean,
    canPublish: Boolean,
    disabledReason: String?,
    onEdit: (AppEntry) -> Unit,
    onDelete: (AppEntry) -> Unit,
    automationSettingsEnabled: Boolean,
    onSaveAutomationProfile: (com.pampa.store.data.model.ReleaseAutomationProfile) -> Unit,
    onDeleteAutomationProfile: (String) -> Unit,
    onToggleAutomationProfile: (String, Boolean) -> Unit,
    onPublishAutomationDraft: (String, Channel) -> Unit,
    onDeferAutomationDraft: (String) -> Unit,
    onDismissAutomationDraft: (String) -> Unit,
    onRetryAutomationDraft: (String) -> Unit,
    onPublishStoreUpdate: (String, Channel, String, String?, String?) -> Unit,
    onPromoteStoreBetaToStable: () -> Unit,
    validateApk: suspend (String) -> ApkValidationResult,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (disabledReason != null) {
            item {
                WarningCard(disabledReason)
            }
        }

        item {
            AutomationOverviewSection(
                apps = apps,
                profiles = automationProfiles,
                drafts = pendingReleaseDrafts,
                settingsEnabled = automationSettingsEnabled,
                publishEnabled = canPublish,
                isSaving = isSaving,
                onSaveProfile = onSaveAutomationProfile,
                onDeleteProfile = onDeleteAutomationProfile,
                onToggleProfile = onToggleAutomationProfile,
                onPublishDraft = onPublishAutomationDraft,
                onDeferDraft = onDeferAutomationDraft,
                onDismissDraft = onDismissAutomationDraft,
                onRetryDraft = onRetryAutomationDraft
            )
        }

        item {
            StoreUpdateCard(
                storeIndex = storeIndex,
                enabled = canPublish,
                isSaving = isSaving,
                onPublishStoreUpdate = onPublishStoreUpdate,
                onPromoteStoreBetaToStable = onPromoteStoreBetaToStable,
                validateApk = validateApk
            )
        }

        if (apps.isEmpty() && !isLoading) {
            item {
                EmptyState(
                    title = "Nessuna app nello store",
                    subtitle = if (canPublish) {
                        "Usa il bottone in basso per aggiungere la prima scheda."
                    } else {
                        "Il catalogo e' vuoto e il publishing non e' attivo su questa piattaforma."
                    }
                )
            }
        }

        items(apps, key = { it.id }) { app ->
            AdminAppCard(
                app = app,
                canEdit = canPublish,
                onEdit = { onEdit(app) },
                onDelete = { onDelete(app) }
            )
        }
    }
}

@Composable
private fun StoreUpdateCard(
    storeIndex: StoreIndexManifest,
    enabled: Boolean,
    isSaving: Boolean,
    onPublishStoreUpdate: (String, Channel, String, String?, String?) -> Unit,
    onPromoteStoreBetaToStable: () -> Unit,
    validateApk: suspend (String) -> ApkValidationResult
) {
    val filePicker = rememberFilePicker()
    val scope = rememberCoroutineScope()
    val isDesktopPlatform = getCurrentPlatform() == AppPlatform.WINDOWS

    var releaseChannel by rememberSaveable { mutableStateOf(Channel.STABLE) }
    var version by rememberSaveable { mutableStateOf("") }
    var changelog by rememberSaveable { mutableStateOf("") }
    var exePath by rememberSaveable { mutableStateOf<String?>(null) }
    var apkPath by rememberSaveable { mutableStateOf<String?>(null) }
    var apkValidation by remember { mutableStateOf<ApkValidationResult?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    fun onApkPicked(path: String?) {
        apkPath = path
        apkValidation = null
        if (path != null) {
            scope.launch {
                isValidating = true
                apkValidation = validateApk(path)
                isValidating = false
            }
        }
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = AccentOrange)
                Text(
                    text = "Pubblica aggiornamento store",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Channel.entries.forEach { channel ->
                    FilterChip(
                        selected = releaseChannel == channel,
                        onClick = { releaseChannel = channel },
                        label = { Text(channel.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                storeIndex.storeReleases.stable?.let {
                    StatusBadge(
                        text = "Stable ${it.version}",
                        background = AccentGreen.copy(alpha = 0.2f),
                        contentColor = AccentGreen
                    )
                }
                storeIndex.storeReleases.beta?.let {
                    StatusBadge(
                        text = "Beta ${it.version}",
                        background = AccentOrange.copy(alpha = 0.2f),
                        contentColor = AccentOrange
                    )
                }
            }

            AdminTextField("Versione", version, { version = it }, "es. 1.1.0")
            AdminTextField(
                "Changelog",
                changelog,
                { changelog = it },
                "Cosa cambia in questa versione",
                singleLine = false,
                minLines = 3
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropSelectButton(
                    emptyLabel = "Trascina o scegli installer Windows",
                    readyLabel = "Installer Windows pronto",
                    selectedPath = exePath,
                    extensions = listOf("exe", "msi"),
                    enabled = enabled,
                    onSelectClick = {
                        scope.launch {
                            exePath = filePicker.pickFile("Seleziona installer Windows", listOf("exe", "msi"))
                        }
                    },
                    onFileDropped = { exePath = it },
                    modifier = Modifier.weight(1f)
                )

                DropSelectButton(
                    emptyLabel = "Trascina o scegli APK",
                    readyLabel = "APK pronto",
                    selectedPath = apkPath,
                    extensions = listOf("apk"),
                    enabled = enabled,
                    onSelectClick = {
                        scope.launch {
                            onApkPicked(filePicker.pickFile("Seleziona APK", listOf("apk")))
                        }
                    },
                    onFileDropped = ::onApkPicked,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Puoi trascinare direttamente gli installer Windows (.exe/.msi) e i file .apk su questi riquadri.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Per Windows usa sempre l'installer generato, non il launcher portatile, cosi' gli aggiornamenti avvengono in-place.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isDesktopPlatform) {
                Text(
                    text = "Se il drag da Esplora file non entra, usa il clic. Su Windows il drop puo' essere bloccato se Pampa Store e' avviato come amministratore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ApkValidationCard(result = apkValidation, isValidating = isValidating)

            Button(
                onClick = {
                    onPublishStoreUpdate(version, releaseChannel, changelog, exePath, apkPath)
                    version = ""
                    changelog = ""
                    exePath = null
                    apkPath = null
                    apkValidation = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled &&
                    version.isNotBlank() &&
                    (exePath != null || apkPath != null) &&
                    !isSaving &&
                    !isValidating &&
                    (apkPath == null || apkValidation is ApkValidationResult.Valid),
                shape = ContinuousCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (enabled) {
                            "Pubblica ${releaseChannel.displayName.lowercase()} su GitHub"
                        } else {
                            "Disponibile solo su desktop"
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = onPromoteStoreBetaToStable,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && !isSaving && storeIndex.storeReleases.beta != null,
                shape = ContinuousCornerShape(14.dp)
            ) {
                Text("Promuovi beta a stable")
            }
        }
    }
}

@Composable
private fun AdminAppCard(
    app: AppEntry,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppArtwork(app = app, modifier = Modifier.size(64.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = app.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.repository.repoOwner.isNotBlank() && app.repository.repoName.isNotBlank()) {
                    Text(
                        text = "${app.repository.repoOwner}/${app.repository.repoName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(
                        text = app.category,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    app.stable?.version?.let {
                        StatusBadge(
                            text = "Stable $it",
                            background = AccentGreen.copy(alpha = 0.2f),
                            contentColor = AccentGreen
                        )
                    }
                    app.beta?.version?.let {
                        StatusBadge(
                            text = "Beta $it",
                            background = AccentOrange.copy(alpha = 0.2f),
                            contentColor = AccentOrange
                        )
                    }
                }
            }

            if (canEdit) {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifica")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare ${app.name}?") },
            text = { Text("La scheda verra rimossa dal manifest dello store.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        confirmDelete = false
                    }
                ) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
private fun AdminEditor(
    app: AppEntry,
    isSaving: Boolean,
    canPublish: Boolean,
    onSave: (AppEntry) -> Unit,
    onCancel: () -> Unit,
    onAddVersion: (String, Channel, VersionInfo, String?, String?) -> Unit,
    onPromoteBetaToStable: (String) -> Unit,
    validateApk: suspend (String) -> ApkValidationResult,
    modifier: Modifier = Modifier
) {
    val filePicker = rememberFilePicker()
    val scope = rememberCoroutineScope()
    val isDesktopPlatform = getCurrentPlatform() == AppPlatform.WINDOWS

    var id by rememberSaveable(app.id) { mutableStateOf(app.id) }
    var name by rememberSaveable(app.id) { mutableStateOf(app.name) }
    var description by rememberSaveable(app.id) { mutableStateOf(app.description) }
    var category by rememberSaveable(app.id) { mutableStateOf(app.category) }
    var developer by rememberSaveable(app.id) { mutableStateOf(app.developer) }
    var repoOwner by rememberSaveable(app.id) { mutableStateOf(app.repository.repoOwner) }
    var repoName by rememberSaveable(app.id) { mutableStateOf(app.repository.repoName) }
    var manifestPath by rememberSaveable(app.id) {
        mutableStateOf(app.repository.manifestPath.ifBlank { DEFAULT_GITHUB_MANIFEST_PATH })
    }

    var versionChannel by rememberSaveable(app.id) { mutableStateOf(Channel.STABLE) }
    var versionNumber by rememberSaveable(app.id) { mutableStateOf("") }
    var versionTag by rememberSaveable(app.id) { mutableStateOf("") }
    var versionChangelog by rememberSaveable(app.id) { mutableStateOf("") }
    var apkFilePath by rememberSaveable(app.id) { mutableStateOf<String?>(null) }
    var exeFilePath by rememberSaveable(app.id) { mutableStateOf<String?>(null) }
    var apkValidation by remember { mutableStateOf<ApkValidationResult?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    fun onApkPicked(path: String?) {
        apkFilePath = path
        apkValidation = null
        if (path != null) {
            scope.launch {
                isValidating = true
                apkValidation = validateApk(path)
                isValidating = false
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSection(
                title = if (app.id.isBlank()) "Nuova app" else "Modifica app",
                icon = Icons.Filled.Edit,
                iconColor = MaterialTheme.colorScheme.primary
            ) {
                AdminTextField("ID", id, { id = it }, "es. my-app")
                Spacer(Modifier.height(12.dp))
                AdminTextField("Nome", name, { name = it }, "Nome app")
                Spacer(Modifier.height(12.dp))
                AdminTextField(
                    "Descrizione",
                    description,
                    { description = it },
                    "Descrizione per lo store",
                    singleLine = false,
                    minLines = 4
                )
                Spacer(Modifier.height(12.dp))
                AdminTextField("Categoria", category, { category = it }, "es. Tools")
                Spacer(Modifier.height(12.dp))
                AdminTextField("Developer", developer, { developer = it }, "Pampa")
                Spacer(Modifier.height(12.dp))
                AdminTextField("Repo owner", repoOwner, { repoOwner = it }, "es. Casual76")
                Spacer(Modifier.height(12.dp))
                AdminTextField("Repo nome", repoName, { repoName = it }, "es. La-mia-app")
                Spacer(Modifier.height(12.dp))
                AdminTextField(
                    "Manifest path",
                    manifestPath,
                    { manifestPath = it },
                    DEFAULT_GITHUB_MANIFEST_PATH
                )
                if (app.packageName.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    SettingsInfoRow(label = "Package name", value = app.packageName)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = ContinuousCornerShape(14.dp)
                    ) {
                        Text("Annulla")
                    }
                    Button(
                        onClick = {
                            onSave(
                                app.copy(
                                    id = id,
                                    name = name,
                                    description = description,
                                    category = category,
                                    developer = developer,
                                    repository = app.repository.copy(
                                        repoOwner = repoOwner,
                                        repoName = repoName,
                                        manifestPath = manifestPath
                                    )
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = canPublish &&
                            !isSaving &&
                            id.isNotBlank() &&
                            name.isNotBlank() &&
                            repoOwner.isNotBlank() &&
                            repoName.isNotBlank(),
                        shape = ContinuousCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Salva scheda")
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(
                title = "Versioni pubblicate",
                icon = Icons.Filled.History,
                iconColor = AccentOrange
            ) {
                if (app.stable == null && app.beta == null && app.history.isEmpty()) {
                    Text(
                        text = "Nessuna release pubblicata.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    app.stable?.let {
                        VersionLine(label = "Stable", version = it, color = AccentGreen)
                        Spacer(Modifier.height(10.dp))
                    }
                    app.beta?.let {
                        VersionLine(label = "Beta", version = it, color = AccentOrange)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (app.history.isNotEmpty()) {
                        app.history.forEach { version ->
                            VersionLine(label = "Storico", version = version, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                if (app.id.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onPromoteBetaToStable(app.id) },
                        enabled = canPublish && !isSaving && app.beta != null,
                        shape = ContinuousCornerShape(12.dp)
                    ) {
                        Text("Promuovi beta a stable")
                    }
                }
            }
        }

        if (app.id.isNotBlank()) {
            item {
                SettingsSection(
                    title = "Pubblica nuova versione",
                    icon = Icons.Filled.CloudUpload,
                    iconColor = AccentOrange
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Channel.entries.forEach { channel ->
                            FilterChip(
                                selected = versionChannel == channel,
                                onClick = { versionChannel = channel },
                                label = { Text(channel.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    AdminTextField("Versione", versionNumber, { versionNumber = it }, "es. 1.2.0")
                    Spacer(Modifier.height(12.dp))
                    AdminTextField(
                        "Tag release",
                        versionTag,
                        { versionTag = it },
                        defaultReleaseTag(id, versionChannel, versionNumber)
                    )
                    Spacer(Modifier.height(12.dp))
                    AdminTextField(
                        "Changelog",
                        versionChangelog,
                        { versionChangelog = it },
                        "Novita della release",
                        singleLine = false,
                        minLines = 4
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropSelectButton(
                            emptyLabel = "Trascina o scegli installer Windows",
                            readyLabel = "Installer Windows pronto",
                            selectedPath = exeFilePath,
                            extensions = listOf("exe", "msi"),
                            enabled = canPublish,
                            onSelectClick = {
                                scope.launch {
                                    exeFilePath = filePicker.pickFile("Seleziona installer Windows", listOf("exe", "msi"))
                                }
                            },
                            onFileDropped = { exeFilePath = it },
                            modifier = Modifier.weight(1f)
                        )
                        DropSelectButton(
                            emptyLabel = "Trascina o scegli APK",
                            readyLabel = "APK pronto",
                            selectedPath = apkFilePath,
                            extensions = listOf("apk"),
                            enabled = canPublish,
                            onSelectClick = {
                                scope.launch {
                                    onApkPicked(filePicker.pickFile("Seleziona APK", listOf("apk")))
                                }
                            },
                            onFileDropped = ::onApkPicked,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Trascina qui l'installer Windows (.exe/.msi) e l'APK release oppure usa il selettore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Su Windows carica l'installer, non il launcher app-image, cosi' app e store possono aggiornarsi senza disinstallazione manuale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isDesktopPlatform) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Se il drag da Esplora file non entra, usa il clic. Su Windows il drop puo' essere bloccato se Pampa Store e' avviato come amministratore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ApkValidationCard(result = apkValidation, isValidating = isValidating)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onAddVersion(
                                id,
                                versionChannel,
                                VersionInfo(
                                    version = versionNumber,
                                    releaseDate = todayAsString(),
                                    changelog = versionChangelog,
                                    releaseTag = versionTag.ifBlank {
                                        defaultReleaseTag(id, versionChannel, versionNumber)
                                    },
                                    apkAsset = if (apkFilePath != null) "$id-$versionNumber.apk" else "",
                                    exeAsset = ""
                                ),
                                apkFilePath,
                                exeFilePath
                            )
                            versionNumber = ""
                            versionTag = ""
                            versionChangelog = ""
                            apkFilePath = null
                            exeFilePath = null
                            apkValidation = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canPublish &&
                            !isSaving &&
                            versionNumber.isNotBlank() &&
                            !isValidating &&
                            (apkFilePath == null || apkValidation is ApkValidationResult.Valid) &&
                            (apkFilePath != null || exeFilePath != null),
                        shape = ContinuousCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                    ) {
                        Text("Pubblica release")
                    }
                }
            }
        }
    }
}

@Composable
private fun DropSelectButton(
    emptyLabel: String,
    readyLabel: String,
    selectedPath: String?,
    extensions: List<String>,
    enabled: Boolean,
    onSelectClick: () -> Unit,
    onFileDropped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformFileDropButton(
        emptyLabel = emptyLabel,
        readyLabel = readyLabel,
        selectedPath = selectedPath,
        extensions = extensions,
        enabled = enabled,
        onSelectClick = onSelectClick,
        onFileDropped = onFileDropped,
        modifier = modifier
    )
}

@Composable
private fun VersionLine(
    label: String,
    version: VersionInfo,
    color: Color
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = color.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("$label v${version.version}", fontWeight = FontWeight.Bold, color = color)
            Text(version.releaseDate, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (version.changelog.isNotBlank()) {
                Text(version.changelog, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WarningCard(message: String) {
    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AdminTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = minLines,
        shape = ContinuousCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun ApkValidationCard(
    result: ApkValidationResult?,
    isValidating: Boolean
) {
    if (result == null && !isValidating) return

    val containerColor = when (result) {
        is ApkValidationResult.Valid -> AccentGreen.copy(alpha = 0.12f)
        is ApkValidationResult.Invalid -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        is ApkValidationResult.Unsupported -> AccentOrange.copy(alpha = 0.12f)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        shape = ContinuousCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isValidating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Verifica APK in corso")
                }
            }

            when (result) {
                is ApkValidationResult.Valid -> {
                    Text("APK valido e firmato correttamente.", fontWeight = FontWeight.Bold, color = AccentGreen)
                    ApkMetadataSummary(result.metadata)
                }

                is ApkValidationResult.Invalid -> {
                    Text("APK non pubblicabile.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    result.issues.forEach { issue ->
                        Text(issue, color = MaterialTheme.colorScheme.error)
                    }
                    result.metadata?.let {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ApkMetadataSummary(it)
                    }
                }

                is ApkValidationResult.Unsupported -> {
                    Text(result.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                null -> Unit
            }
        }
    }
}

@Composable
private fun ApkMetadataSummary(metadata: ApkValidationMetadata) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ApkMetadataRow(label = "Package", value = metadata.packageName.ifBlank { "-" })
        ApkMetadataRow(
            label = "Versione",
            value = listOf(metadata.versionName.ifBlank { "-" }, metadata.versionCode.ifBlank { "-" }).joinToString(" / ")
        )
        ApkMetadataRow(
            label = "SDK",
            value = "min ${metadata.minSdk?.toString() ?: "-"} / target ${metadata.targetSdk?.toString() ?: "-"}"
        )
        ApkMetadataRow(
            label = "Firma",
            value = metadata.signerDn.ifBlank { "-" }
        )
        ApkMetadataRow(
            label = "SHA-256",
            value = metadata.signerSha256.ifBlank { "-" }
        )
        ApkMetadataRow(
            label = "ABI",
            value = metadata.supportedAbis.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Nessuna ABI rilevata"
        )
        ApkMetadataRow(
            label = "Dimensione APK",
            value = formatBytes(metadata.compressedSizeBytes)
        )
        Text(
            text = "La dimensione installata e' maggiore del file APK compresso.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (metadata.installHint.isNotBlank()) {
            Text(
                text = metadata.installHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApkMetadataRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "-"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    val formatted = if (value >= 10 || unitIndex == 0) {
        value.toInt().toString()
    } else {
        (kotlin.math.round(value * 10.0) / 10.0).toString()
    }
    return "$formatted ${units[unitIndex]}"
}

private fun defaultReleaseTag(appId: String, channel: Channel, version: String): String {
    val normalizedAppId = appId.trim().ifBlank { STORE_APP_ID }
    val normalizedVersion = version.trim().ifBlank { "0.0.0" }
    return "${channel.name.lowercase()}-$normalizedAppId-v$normalizedVersion"
}

private fun todayAsString(): String {
    val milliseconds = com.pampa.store.data.local.currentTimeMillis()
    val days = (milliseconds / 86400000).toInt()
    var year = 1970
    var dayIndex = days
    while (true) {
        val daysInYear = if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 366 else 365
        if (dayIndex < daysInYear) break
        dayIndex -= daysInYear
        year++
    }
    val months = intArrayOf(
        31,
        if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28,
        31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    )
    var month = 1
    for (daysInMonth in months) {
        if (dayIndex < daysInMonth) break
        dayIndex -= daysInMonth
        month++
    }
    return "$year-${month.toString().padStart(2, '0')}-${(dayIndex + 1).toString().padStart(2, '0')}"
}
