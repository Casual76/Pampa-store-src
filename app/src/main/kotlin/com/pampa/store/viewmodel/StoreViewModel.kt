package com.pampa.store.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pampa.store.BuildConfig
import com.pampa.store.data.AccentPresetId
import com.pampa.store.data.AccentSource
import com.pampa.store.data.AppEntry
import com.pampa.store.data.AppThemeMode
import com.pampa.store.data.BuildVersion
import com.pampa.store.data.CachedCatalog
import com.pampa.store.data.CatalogClient
import com.pampa.store.data.Channel
import com.pampa.store.data.DownloadState
import com.pampa.store.data.InstallEvent
import com.pampa.store.data.OwnershipFilter
import com.pampa.store.data.RepositoryRef
import com.pampa.store.data.StoreApp
import com.pampa.store.data.StorePersistence
import com.pampa.store.data.StorePreferences
import com.pampa.store.data.StoreUiState
import com.pampa.store.data.StoreUpdate
import com.pampa.store.data.enumOrDefault
import com.pampa.store.data.releaseAssetUrl
import com.pampa.store.data.versionFor
import com.pampa.store.platform.AndroidAppService
import com.pampa.store.platform.AppInstaller
import com.pampa.store.util.isVersionNewer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val persistence = StorePersistence(application)
    private val catalogClient = CatalogClient()
    private val appService = AndroidAppService(application)
    private val installer = AppInstaller(application)

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    init {
        BuildVersion.name = BuildConfig.VERSION_NAME
        viewModelScope.launch {
            persistence.preferences.collect { preferences ->
                _uiState.update { it.copy(preferences = preferences) }
                checkStoreUpdate()
            }
        }
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        val cached = withContext(Dispatchers.IO) { persistence.readCachedCatalog() }
        if (cached != null && cached.apps.isNotEmpty()) {
            applyCatalog(cached, usingCache = true)
            _uiState.update { it.copy(isLoading = false) }
        }
        refreshCatalog(initial = cached == null)
    }

    fun refreshCatalog(initial: Boolean = false) {
        viewModelScope.launch { refreshCatalogInternal(initial) }
    }

    private suspend fun refreshCatalogInternal(initial: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = initial && it.apps.isEmpty(),
                isRefreshing = !initial || it.apps.isNotEmpty(),
                error = null,
            )
        }
        runCatching { catalogClient.fetchCatalog() }
            .onSuccess { result ->
                val cached = CachedCatalog(
                    apps = result.apps,
                    storeReleases = result.storeReleases,
                    warnings = result.warnings,
                    savedAt = System.currentTimeMillis(),
                )
                withContext(Dispatchers.IO) { persistence.writeCachedCatalog(cached) }
                applyCatalog(cached, usingCache = false)
            }
            .onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        error = if (state.apps.isEmpty()) {
                            error.message ?: "Impossibile caricare il catalogo."
                        } else {
                            null
                        },
                        warning = if (state.apps.isNotEmpty()) {
                            "Stai vedendo l’ultimo catalogo disponibile. Tocca per riprovare."
                        } else {
                            null
                        },
                        isUsingCache = state.apps.isNotEmpty(),
                    )
                }
            }
        _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
    }

    private suspend fun applyCatalog(catalog: CachedCatalog, usingCache: Boolean) {
        val resolved = withContext(Dispatchers.IO) { resolveInstalledApps(catalog.apps) }
        _uiState.update { state ->
            state.copy(
                apps = resolved,
                storeReleases = catalog.storeReleases,
                isUsingCache = usingCache,
                lastUpdatedAt = catalog.savedAt,
                warning = catalog.warnings.takeIf(List<String>::isNotEmpty)?.joinToString("\n"),
                error = null,
            )
        }
        checkStoreUpdate()
    }

    private fun resolveInstalledApps(entries: List<AppEntry>): List<StoreApp> = entries
        .filterNot { it.id == com.pampa.store.data.STORE_APP_ID }
        .map { entry ->
            val installedVersion = appService.installedVersion(entry.packageName)
            val installedChannel = when (installedVersion) {
                entry.beta?.version -> Channel.BETA
                null -> null
                else -> Channel.STABLE
            }
            val latest = installedChannel?.let(entry::versionFor)
            StoreApp(
                entry = entry,
                installedVersion = installedVersion,
                installedChannel = installedChannel,
                hasUpdate = installedVersion != null && latest != null &&
                    isVersionNewer(latest.version, installedVersion),
            )
        }
        .sortedBy { it.entry.name.lowercase() }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val current = _uiState.value.apps
            if (current.isEmpty()) {
                refreshPrerequisites()
                return@launch
            }
            val resolved = withContext(Dispatchers.IO) {
                resolveInstalledApps(current.map(StoreApp::entry))
            }
            _uiState.update { state ->
                state.copy(
                    apps = resolved.map { refreshed ->
                        refreshed.copy(
                            downloadState = state.apps.firstOrNull {
                                it.entry.id == refreshed.entry.id
                            }?.downloadState ?: DownloadState.Idle,
                        )
                    },
                    prerequisites = appService.installPrerequisites(),
                )
            }
            checkStoreUpdate()
        }
    }

    fun refreshPrerequisites() {
        _uiState.update { it.copy(prerequisites = appService.installPrerequisites()) }
    }

    fun app(appId: String): StoreApp? = _uiState.value.apps.firstOrNull { it.entry.id == appId }

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun setCategory(category: String?) = _uiState.update { it.copy(selectedCategory = category) }

    fun setOwnershipFilter(filter: OwnershipFilter) =
        _uiState.update { it.copy(ownershipFilter = filter) }

    fun setThemeMode(mode: AppThemeMode) = updatePreferences { it.copy(themeMode = mode.name) }

    fun setAccentSource(source: AccentSource) =
        updatePreferences { it.copy(accentSource = source.name) }

    fun setAccentPreset(preset: AccentPresetId) = updatePreferences {
        it.copy(accentSource = AccentSource.PRESET.name, accentPreset = preset.storageName)
    }

    fun setDefaultChannel(channel: Channel) =
        updatePreferences { it.copy(defaultChannel = channel.name) }

    fun setStoreUpdateChannel(channel: Channel) = updatePreferences {
        it.copy(storeUpdateChannel = channel.name)
    }

    fun setAutoCheckStoreUpdates(enabled: Boolean) = updatePreferences {
        it.copy(autoCheckStoreUpdates = enabled)
    }

    private fun updatePreferences(transform: (StorePreferences) -> StorePreferences) {
        viewModelScope.launch { persistence.updatePreferences(transform) }
    }

    fun openInstallSettings() = appService.openInstallSettings()

    fun openApp(packageName: String) {
        if (!appService.launch(packageName)) {
            _uiState.update { it.copy(warning = "L’app non espone un’attività avviabile.") }
        }
    }

    fun installApp(appId: String, channel: Channel) {
        val app = app(appId) ?: return
        val version = app.entry.versionFor(channel) ?: run {
            updateDownloadState(appId, DownloadState.Error("Nessuna versione disponibile."))
            return
        }
        installRelease(
            stateId = appId,
            expectedPackageName = app.entry.packageName,
            repository = app.entry.repository,
            version = version,
        )
    }

    fun installStoreUpdate() {
        val update = _uiState.value.storeUpdate ?: return
        installRelease(
            stateId = com.pampa.store.data.STORE_APP_ID,
            expectedPackageName = "com.pampa.store",
            repository = update.repository,
            version = update.version,
        )
    }

    private fun installRelease(
        stateId: String,
        expectedPackageName: String,
        repository: RepositoryRef,
        version: com.pampa.store.data.VersionInfo,
    ) {
        if (!appService.installPrerequisites().canInstallPackages) {
            updateDownloadState(
                stateId,
                DownloadState.Error("Autorizza l’installazione da Pampa Store e riprova."),
            )
            return
        }
        if (version.apkAsset.isBlank() || version.releaseTag.isBlank()) {
            updateDownloadState(stateId, DownloadState.Error("Questa release non contiene un APK."))
            return
        }

        viewModelScope.launch {
            val safeName = version.apkAsset.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destination = File(appService.downloadsDirectory(), safeName)
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogClient.download(repository.releaseAssetUrl(version), destination) { done, total ->
                        val progress = if (total > 0) done.toFloat() / total else 0f
                        updateDownloadState(stateId, DownloadState.Downloading(progress, done, total))
                    }
                    updateDownloadState(stateId, DownloadState.Verifying("Controllo del pacchetto…"))
                    appService.validateApk(destination, expectedPackageName).getOrThrow()
                }
                installer.install(destination, expectedPackageName).collect { event ->
                    when (event) {
                        InstallEvent.Preparing ->
                            updateDownloadState(stateId, DownloadState.Verifying("Preparazione…"))
                        is InstallEvent.AwaitingUserAction ->
                            updateDownloadState(stateId, DownloadState.AwaitingUser(event.message))
                        is InstallEvent.Installing ->
                            updateDownloadState(stateId, DownloadState.Installing(event.message))
                        InstallEvent.Success -> {
                            updateDownloadState(stateId, DownloadState.Installed(version.version))
                            refreshInstalledApps()
                        }
                        is InstallEvent.Failure ->
                            updateDownloadState(stateId, DownloadState.Error(event.message))
                    }
                }
            }.onFailure { error ->
                updateDownloadState(
                    stateId,
                    DownloadState.Error(error.message ?: "Download o installazione non riusciti."),
                )
            }
        }
    }

    private fun updateDownloadState(appId: String, downloadState: DownloadState) {
        _uiState.update { state ->
            if (appId == com.pampa.store.data.STORE_APP_ID) {
                state.copy(warning = (downloadState as? DownloadState.Error)?.message)
            } else {
                state.copy(
                    apps = state.apps.map { app ->
                        if (app.entry.id == appId) app.copy(downloadState = downloadState) else app
                    },
                )
            }
        }
    }

    private fun checkStoreUpdate() {
        val state = _uiState.value
        if (!state.preferences.autoCheckStoreUpdates) {
            _uiState.update { it.copy(storeUpdate = null) }
            return
        }
        val channel = enumOrDefault(state.preferences.storeUpdateChannel, Channel.STABLE)
        val version = state.storeReleases.versionFor(channel)
        val update = version?.takeIf { isVersionNewer(it.version, BuildConfig.VERSION_NAME) }?.let {
            StoreUpdate(
                repository = state.storeReleases.repository,
                version = it,
            )
        }
        _uiState.update { it.copy(storeUpdate = update) }
    }

    fun clearMessage() = _uiState.update { it.copy(warning = null, error = null) }

    override fun onCleared() {
        catalogClient.close()
        super.onCleared()
    }
}
