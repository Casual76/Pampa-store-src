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
import com.pampa.store.data.QueueEntry
import com.pampa.store.data.RepositoryRef
import com.pampa.store.data.STORE_APP_ID
import com.pampa.store.data.StoreApp
import com.pampa.store.data.StoreEvent
import com.pampa.store.data.StoreEventTone
import com.pampa.store.data.StorePersistence
import com.pampa.store.data.StorePreferences
import com.pampa.store.data.StoreUiState
import com.pampa.store.data.StoreUpdate
import com.pampa.store.data.VersionInfo
import com.pampa.store.data.clearPending
import com.pampa.store.data.enqueue
import com.pampa.store.data.enumOrDefault
import com.pampa.store.data.releaseAssetUrl
import com.pampa.store.data.resolveStoreApps
import com.pampa.store.data.retryFailed
import com.pampa.store.data.versionFor
import com.pampa.store.data.withState
import com.pampa.store.platform.AndroidAppService
import com.pampa.store.platform.AppInstaller
import com.pampa.store.platform.StoreNotifications
import com.pampa.store.util.isVersionNewer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val notifications = StoreNotifications(application)

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    /**
     * I fatti che meritano un avviso in cima allo schermo.
     *
     * Un flusso e non un campo dello stato: un avviso e' un *evento*, e messo dentro lo stato
     * sopravvive alla propria utilita' — riappare a ogni ricomposizione finche' qualcuno non si
     * ricorda di azzerarlo, e quel qualcuno prima o poi se ne dimentica.
     */
    private val _events = MutableSharedFlow<StoreEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<StoreEvent> = _events.asSharedFlow()

    /**
     * La coda gira in un job solo, e questo riferimento è come si sa se sta già girando.
     *
     * Non è un dettaglio di implementazione: due job che pescano dalla stessa coda consegnerebbero
     * due APK al PackageInstaller nello stesso istante, e il secondo prompt di sistema copre il
     * primo. Chi accoda chiama [pumpQueue], che parte solo se non c'è già qualcuno a pompare.
     */
    private var queueJob: Job? = null

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
        refreshNotificationPermission()
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

    private fun resolveInstalledApps(entries: List<AppEntry>): List<StoreApp> =
        resolveStoreApps(entries, appService::installedVersion)

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
            // Quello che è stato appena installato non è più un aggiornamento in attesa, e la
            // notifica che lo annunciava non ha più niente da dire.
            notifications.syncWithUpdates(_uiState.value.updatableApps)
        }
    }

    fun refreshPrerequisites() {
        _uiState.update { it.copy(prerequisites = appService.installPrerequisites()) }
    }

    fun refreshNotificationPermission() {
        _uiState.update { it.copy(notificationsEnabled = notifications.canPost()) }
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

    fun openNotificationSettings() = notifications.openSettings()

    fun openApp(packageName: String) {
        if (!appService.launch(packageName)) {
            _uiState.update { it.copy(warning = "L’app non espone un’attività avviabile.") }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Installazioni. Tutto passa dalla coda, anche quando la coda ha una voce sola.
    // ---------------------------------------------------------------------------------------

    /** Installa o aggiorna una singola app. `channel` null significa "quello da cui è installata". */
    fun installApp(appId: String, channel: Channel?) {
        val app = app(appId) ?: return
        val target = channel
            ?: app.installedChannel
            ?: enumOrDefault(_uiState.value.preferences.defaultChannel, Channel.STABLE)
        val version = app.entry.versionFor(target) ?: run {
            setInstallState(appId, DownloadState.Error("Nessuna versione disponibile."))
            return
        }
        enqueueAll(
            listOf(
                QueueEntry(
                    appId = appId,
                    name = app.entry.name,
                    channel = target,
                    targetVersion = version.version,
                ),
            ),
        )
    }

    /** Aggiorna una sola app sul canale da cui è installata. */
    fun updateOne(appId: String) = installApp(appId, channel = null)

    /**
     * Accoda tutto quello che è aggiornabile.
     *
     * Lo Store va per ultimo di proposito: installare se stessi termina il processo, e qualunque
     * cosa fosse ancora in coda dietro non partirebbe mai.
     */
    fun updateAll() {
        val state = _uiState.value
        val apps = state.updatableApps.mapNotNull { app ->
            val channel = app.installedChannel ?: return@mapNotNull null
            val version = app.entry.versionFor(channel) ?: return@mapNotNull null
            QueueEntry(
                appId = app.entry.id,
                name = app.entry.name,
                channel = channel,
                targetVersion = version.version,
            )
        }
        val store = state.storeUpdate?.let { update ->
            QueueEntry(
                appId = STORE_APP_ID,
                name = "Pampa Store",
                channel = enumOrDefault(state.preferences.storeUpdateChannel, Channel.STABLE),
                targetVersion = update.version.version,
            )
        }
        enqueueAll(apps + listOfNotNull(store))
    }

    fun installStoreUpdate() {
        val state = _uiState.value
        val update = state.storeUpdate ?: return
        enqueueAll(
            listOf(
                QueueEntry(
                    appId = STORE_APP_ID,
                    name = "Pampa Store",
                    channel = enumOrDefault(state.preferences.storeUpdateChannel, Channel.STABLE),
                    targetVersion = update.version.version,
                ),
            ),
        )
    }

    fun retryFailed() {
        _uiState.update { it.copy(updateQueue = it.updateQueue.retryFailed()) }
        pumpQueue()
    }

    fun clearQueue() {
        _uiState.update { state ->
            val dropped = state.updateQueue.filterNot(QueueEntry::isRunning).map(QueueEntry::appId)
            state.copy(
                updateQueue = state.updateQueue.clearPending(),
                apps = state.apps.map { app ->
                    if (app.entry.id in dropped) app.copy(downloadState = DownloadState.Idle) else app
                },
            )
        }
    }

    private fun enqueueAll(candidates: List<QueueEntry>) {
        if (candidates.isEmpty()) return
        _uiState.update { state ->
            val queue = state.updateQueue.enqueue(candidates)
            val queued = queue.filter(QueueEntry::isWaiting).map(QueueEntry::appId).toSet()
            state.copy(
                updateQueue = queue,
                apps = state.apps.map { app ->
                    if (app.entry.id in queued) app.copy(downloadState = DownloadState.Queued) else app
                },
            )
        }
        pumpQueue()
    }

    private fun pumpQueue() {
        if (queueJob?.isActive == true) return
        queueJob = viewModelScope.launch {
            while (true) {
                val next = _uiState.value.updateQueue.firstOrNull(QueueEntry::isWaiting) ?: break
                runQueueEntry(next)
            }
        }
    }

    private suspend fun runQueueEntry(entry: QueueEntry) {
        val state = _uiState.value
        if (entry.appId == STORE_APP_ID) {
            val update = state.storeUpdate
            if (update == null) {
                setInstallState(entry.appId, DownloadState.Error("L’aggiornamento non è più disponibile."))
                return
            }
            runInstall(entry.appId, "com.pampa.store", update.repository, update.version)
            return
        }
        val app = state.apps.firstOrNull { it.entry.id == entry.appId }
        val version = app?.entry?.versionFor(entry.channel)
        if (app == null || version == null) {
            setInstallState(entry.appId, DownloadState.Error("L’app non è più nel catalogo."))
            return
        }
        runInstall(entry.appId, app.entry.packageName, app.entry.repository, version)
    }

    /**
     * Scarica, verifica e installa, e **attende** l'esito.
     *
     * Attendere è tutto il punto: la coda ha bisogno di sapere quando la voce corrente è finita per
     * poter cominciare la prossima, e un'installazione lanciata e dimenticata non lo dice mai.
     */
    private suspend fun runInstall(
        stateId: String,
        expectedPackageName: String,
        repository: RepositoryRef,
        version: VersionInfo,
    ) {
        if (!appService.installPrerequisites().canInstallPackages) {
            refreshPrerequisites()
            setInstallState(
                stateId,
                DownloadState.Error("Autorizza l’installazione da Pampa Store e riprova."),
            )
            return
        }
        if (version.apkAsset.isBlank() || version.releaseTag.isBlank()) {
            setInstallState(stateId, DownloadState.Error("Questa release non contiene un APK."))
            return
        }

        val safeName = version.apkAsset.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(appService.downloadsDirectory(), safeName)
        runCatching {
            withContext(Dispatchers.IO) {
                catalogClient.download(repository.releaseAssetUrl(version), destination) { done, total ->
                    val progress = if (total > 0) done.toFloat() / total else 0f
                    setInstallState(stateId, DownloadState.Downloading(progress, done, total))
                }
                setInstallState(stateId, DownloadState.Verifying("Controllo del pacchetto…"))
                appService.validateApk(destination, expectedPackageName).getOrThrow()
            }
            installer.install(destination, expectedPackageName).collect { event ->
                when (event) {
                    InstallEvent.Preparing ->
                        setInstallState(stateId, DownloadState.Verifying("Preparazione…"))
                    is InstallEvent.AwaitingUserAction ->
                        setInstallState(stateId, DownloadState.AwaitingUser(event.message))
                    is InstallEvent.Installing ->
                        setInstallState(stateId, DownloadState.Installing(event.message))
                    InstallEvent.Success -> {
                        setInstallState(stateId, DownloadState.Installed(version.version))
                        announce(
                            id = "installed-$stateId-${version.version}",
                            title = "Installata la ${version.version}",
                            message = queueName(stateId),
                            tone = StoreEventTone.Success,
                        )
                        refreshInstalledApps()
                    }
                    is InstallEvent.Failure -> {
                        setInstallState(stateId, DownloadState.Error(event.message))
                        announce(
                            id = "failed-$stateId-${version.version}",
                            title = "Installazione non riuscita",
                            message = event.message,
                            tone = StoreEventTone.Error,
                        )
                    }
                }
            }
        }.onFailure { error ->
            setInstallState(
                stateId,
                DownloadState.Error(error.message ?: "Download o installazione non riusciti."),
            )
        }
    }

    /**
     * Un solo posto in cui lo stato di un'installazione si scrive.
     *
     * Riga dell'app e voce della coda mostrano lo stesso fatto, e tenerli allineati da due chiamanti
     * diversi è il modo in cui la coda finisce per dire "in attesa" accanto a una barra di
     * avanzamento che si sta muovendo.
     */
    private fun setInstallState(appId: String, downloadState: DownloadState) {
        _uiState.update { state ->
            state.copy(
                updateQueue = state.updateQueue.withState(appId, downloadState),
                apps = state.apps.map { app ->
                    if (app.entry.id == appId) app.copy(downloadState = downloadState) else app
                },
                warning = if (appId == STORE_APP_ID && downloadState is DownloadState.Error) {
                    downloadState.message
                } else {
                    state.warning
                },
            )
        }
    }

    private fun announce(id: String, title: String, message: String, tone: StoreEventTone) {
        _events.tryEmit(StoreEvent(id = id, title = title, message = message, tone = tone))
    }

    /** Il nome che la coda conosce per questo id, o l'id stesso se la coda non ne sa niente. */
    private fun queueName(appId: String): String =
        _uiState.value.updateQueue.firstOrNull { it.appId == appId }?.name
            ?: _uiState.value.apps.firstOrNull { it.entry.id == appId }?.entry?.name
            ?: appId

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
