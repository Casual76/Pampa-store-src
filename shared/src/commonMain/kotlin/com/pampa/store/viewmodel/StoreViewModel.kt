package com.pampa.store.viewmodel

import com.pampa.store.domain.repository.AppRepository
import com.pampa.store.data.api.DownloadEvent
import com.pampa.store.data.api.GitHubCatalogService
import com.pampa.store.data.repository.DefaultAppRepository
import com.pampa.store.domain.repository.PreferencesRepository
import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.model.AppDisplayInfo
import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.AppPlatform
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DownloadState
import com.pampa.store.data.model.InstallEvent
import com.pampa.store.data.model.InstalledAppInfo
import com.pampa.store.data.model.OwnershipFilter
import com.pampa.store.data.model.ReleaseAssetSource
import com.pampa.store.data.model.STORE_APP_ID
import com.pampa.store.data.model.SeamlessUpdateState
import com.pampa.store.data.model.StoreUpdateState
import com.pampa.store.data.model.VersionInfo
import com.pampa.store.platform.InstallPrerequisites
import com.pampa.store.platform.InstallerService
import com.pampa.store.platform.PlatformAppService
import com.pampa.store.platform.getCurrentPlatform
import com.pampa.store.util.isVersionNewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoreViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val installerProvider: () -> InstallerService,
    private val platformAppService: PlatformAppService,
    private val appVersion: String = "dev",
    private val appRepositoryFactory: () -> AppRepository = { DefaultAppRepository(GitHubCatalogService()) },
    private val platformProvider: () -> AppPlatform = ::getCurrentPlatform,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private var appRepository: AppRepository? = null

    private val _apps = MutableStateFlow<List<AppDisplayInfo>>(emptyList())
    val apps: StateFlow<List<AppDisplayInfo>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _ownershipFilter = MutableStateFlow(OwnershipFilter.ALL)
    val ownershipFilter: StateFlow<OwnershipFilter> = _ownershipFilter.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    private val _storeUpdateState = MutableStateFlow(StoreUpdateState())
    val storeUpdateState: StateFlow<StoreUpdateState> = _storeUpdateState.asStateFlow()

    private val _storeUpdateDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val storeUpdateDownloadState: StateFlow<DownloadState> = _storeUpdateDownloadState.asStateFlow()

    private val _seamlessUpdateState = MutableStateFlow<SeamlessUpdateState>(SeamlessUpdateState.Idle)
    val seamlessUpdateState: StateFlow<SeamlessUpdateState> = _seamlessUpdateState.asStateFlow()

    private val _installPrerequisites = MutableStateFlow(InstallPrerequisites())
    val installPrerequisites: StateFlow<InstallPrerequisites> = _installPrerequisites.asStateFlow()

    val defaultChannel: StateFlow<Channel> = preferencesRepository.preferences
        .map { preferencesRepository.getDefaultChannel() }
        .stateIn(scope, SharingStarted.Eagerly, Channel.STABLE)

    val storeUpdateChannel: StateFlow<Channel> = preferencesRepository.preferences
        .map { preferencesRepository.getStoreUpdateChannel() }
        .stateIn(scope, SharingStarted.Eagerly, Channel.STABLE)

    val displayApps: StateFlow<List<AppDisplayInfo>> = combine(_apps, _downloadStates) { apps, downloadStates ->
        apps.map { app ->
            app.copy(downloadState = downloadStates[app.entry.id] ?: DownloadState.Idle)
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val filteredApps: StateFlow<List<AppDisplayInfo>> = combine(
        displayApps,
        _searchQuery,
        _selectedCategory,
        _ownershipFilter
    ) { apps, query, category, ownershipFilter ->
        apps.filter { app ->
            val normalizedQuery = query.trim()
            val matchesQuery = normalizedQuery.isEmpty() ||
                app.entry.name.contains(normalizedQuery, ignoreCase = true) ||
                app.entry.description.contains(normalizedQuery, ignoreCase = true) ||
                app.entry.developer.contains(normalizedQuery, ignoreCase = true)
            val matchesCategory = category == null || app.entry.category == category
            val matchesOwnership = when (ownershipFilter) {
                OwnershipFilter.ALL -> true
                OwnershipFilter.INSTALLED -> app.installedVersion != null
                OwnershipFilter.UPDATABLE -> app.hasUpdate
            }
            matchesQuery && matchesCategory && matchesOwnership
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun init() {
        scope.launch {
            preferencesRepository.init()
            initCatalogService()
            refreshInstallPrerequisites()
            refreshApps()
            if (preferencesRepository.preferences.value.storeUpdateAutoCheckEnabled) {
                checkStoreUpdate()
                // Seamless: auto-download if update found
                startSeamlessUpdateIfAvailable()
            }
        }
    }

    private fun initCatalogService() {
        appRepository?.close()
        appRepository = appRepositoryFactory()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }

    fun onOwnershipFilterSelected(filter: OwnershipFilter) {
        _ownershipFilter.value = filter
    }

    fun refreshInstallPrerequisites() {
        scope.launch {
            _installPrerequisites.value = platformAppService.getInstallPrerequisites()
        }
    }

    fun refreshApps() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            refreshInstallPrerequisites()

            val service = appRepository
            if (service == null) {
                _error.value = "Catalogo non disponibile."
                _isLoading.value = false
                return@launch
            }

            service.getStoreManifest().fold(
                onSuccess = { manifest ->
                    val warningMessage = manifest.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    val displayApps = manifest.apps
                        .filterNot { it.id == STORE_APP_ID }
                        .map { entry ->
                        val installed = reconcileInstalledInfo(entry)
                        val latestForInstalledChannel = resolveLatestVersionForInstalledChannel(entry, installed)
                        val hasUpdate = installed != null &&
                            latestForInstalledChannel != null &&
                            isVersionNewer(latestForInstalledChannel, installed.version)

                        AppDisplayInfo(
                            entry = entry,
                            installedVersion = installed?.version,
                            installedChannel = installed?.channel,
                            hasUpdate = hasUpdate
                        )
                    }

                    _apps.value = displayApps
                    _categories.value = displayApps.map { it.entry.category }.distinct().sorted()
                    _error.value = warningMessage
                },
                onFailure = { error ->
                    _error.value = error.message ?: "Impossibile caricare il catalogo."
                }
            )

            _isLoading.value = false
        }
    }

    fun checkStoreUpdate(channelOverride: Channel? = null) {
        scope.launch {
            val service = appRepository
            if (service == null) {
                _storeUpdateState.value = _storeUpdateState.value.copy(
                    isChecking = false,
                    error = "Catalogo non disponibile.",
                    lastCheckedAt = com.pampa.store.data.local.currentTimeMillis()
                )
                return@launch
            }

            val channel = channelOverride ?: preferencesRepository.getStoreUpdateChannel()
            _storeUpdateState.value = _storeUpdateState.value.copy(isChecking = true, error = null)
            service.getLatestStoreUpdate(channel).fold(
                onSuccess = { update ->
                    _storeUpdateState.value = StoreUpdateState(
                        isChecking = false,
                        availableUpdate = update?.takeIf { isVersionNewer(it.versionInfo.version, appVersion) },
                        lastCheckedAt = com.pampa.store.data.local.currentTimeMillis(),
                        error = null
                    )
                },
                onFailure = { error ->
                    _storeUpdateState.value = StoreUpdateState(
                        isChecking = false,
                        availableUpdate = null,
                        lastCheckedAt = com.pampa.store.data.local.currentTimeMillis(),
                        error = error.message ?: "Controllo aggiornamenti store non riuscito."
                    )
                }
            )
        }
    }

    fun findApp(appId: String): AppDisplayInfo? {
        return displayApps.value.firstOrNull { it.entry.id == appId }
    }

    fun openInstallSettings() {
        platformAppService.openInstallSettings()
    }

    fun launchInstalledApp(packageName: String): Boolean {
        return platformAppService.launchInstalledApp(packageName)
    }

    fun downloadAndInstall(appId: String, channel: Channel) {
        scope.launch {
            if (platformProvider() == AppPlatform.ANDROID && !_installPrerequisites.value.canInstallPackages) {
                updateDownloadState(
                    appId,
                    DownloadState.Error(
                        _installPrerequisites.value.message ?: "Abilita i permessi di installazione e riprova."
                    )
                )
                return@launch
            }

            val app = displayApps.value.find { it.entry.id == appId } ?: return@launch
            val versionInfo = when (channel) {
                Channel.STABLE -> app.entry.stable
                Channel.BETA -> app.entry.beta
            } ?: run {
                updateDownloadState(appId, DownloadState.Error("Versione ${channel.displayName} non disponibile."))
                return@launch
            }

            val assetSource = releaseSourceFor(app.entry, versionInfo)
            if (assetSource == null) {
                updateDownloadState(appId, DownloadState.Error("Nessun asset disponibile per questa piattaforma."))
                return@launch
            }

            val downloadDir = preferencesRepository.preferences.value.downloadPath.trim()
            if (downloadDir.isEmpty()) {
                updateDownloadState(appId, DownloadState.Error("Percorso download non configurato."))
                return@launch
            }

            val savePath = buildSavePath(downloadDir, assetSource.assetName)
            downloadAndInstallAsset(
                installId = appId,
                source = assetSource,
                savePath = savePath,
                updateState = { updateDownloadState(appId, it) },
                onInstallSuccess = { filePath ->
                    preferencesRepository.markInstalled(
                        appId = appId,
                        version = versionInfo.version,
                        channel = channel,
                        path = filePath
                    )
                    refreshApps()
                }
            )
        }
    }

    fun downloadAndInstallStoreUpdate() {
        scope.launch {
            if (platformProvider() == AppPlatform.ANDROID && !_installPrerequisites.value.canInstallPackages) {
                _storeUpdateDownloadState.value = DownloadState.Error(
                    _installPrerequisites.value.message ?: "Abilita i permessi di installazione e riprova."
                )
                return@launch
            }

            val update = _storeUpdateState.value.availableUpdate ?: run {
                _storeUpdateDownloadState.value = DownloadState.Error("Nessun aggiornamento store disponibile.")
                return@launch
            }
            val assetName = assetNameForPlatform(update.versionInfo)
            if (assetName.isBlank()) {
                _storeUpdateDownloadState.value = DownloadState.Error("La release dello store non include un asset per questa piattaforma.")
                return@launch
            }

            val downloadDir = preferencesRepository.preferences.value.downloadPath.trim()
            if (downloadDir.isEmpty()) {
                _storeUpdateDownloadState.value = DownloadState.Error("Percorso download non configurato.")
                return@launch
            }

            downloadAndInstallAsset(
                installId = STORE_APP_ID,
                source = ReleaseAssetSource(update.repository, update.versionInfo.releaseTag, assetName),
                savePath = buildSavePath(downloadDir, assetName),
                updateState = { _storeUpdateDownloadState.value = it },
                onInstallSuccess = { }
            )
        }
    }

    private fun startSeamlessUpdateIfAvailable() {
        scope.launch {
            // Wait for store update check to complete
            val maxWait = 30
            var waited = 0
            while (_storeUpdateState.value.isChecking && waited < maxWait) {
                kotlinx.coroutines.delay(500)
                waited++
            }

            val update = _storeUpdateState.value.availableUpdate ?: return@launch
            val assetName = assetNameForPlatform(update.versionInfo)
            if (assetName.isBlank()) return@launch

            val downloadDir = preferencesRepository.preferences.value.downloadPath.trim()
            if (downloadDir.isEmpty()) return@launch

            val savePath = buildSavePath(downloadDir, assetName)
            val source = ReleaseAssetSource(update.repository, update.versionInfo.releaseTag, assetName)
            val service = appRepository ?: return@launch

            _seamlessUpdateState.value = SeamlessUpdateState.Checking

            try {
                _seamlessUpdateState.value = SeamlessUpdateState.Downloading(0f)
                service.downloadAsset(
                    source = source,
                    savePath = savePath,
                    onProgress = { }
                ).collect { event ->
                    when (event) {
                        is DownloadEvent.Started -> _seamlessUpdateState.value = SeamlessUpdateState.Downloading(0f)
                        is DownloadEvent.Progress -> _seamlessUpdateState.value = SeamlessUpdateState.Downloading(event.progress)
                        is DownloadEvent.Completed -> {
                            _seamlessUpdateState.value = SeamlessUpdateState.Ready(
                                version = update.versionInfo.version,
                                changelog = update.versionInfo.changelog,
                                filePath = event.filePath
                            )
                        }
                        is DownloadEvent.Error -> {
                            _seamlessUpdateState.value = SeamlessUpdateState.Error(event.message)
                        }
                    }
                }
            } catch (e: Exception) {
                _seamlessUpdateState.value = SeamlessUpdateState.Error(
                    e.message ?: "Download aggiornamento non riuscito."
                )
            }
        }
    }

    fun installPendingUpdate() {
        val readyState = _seamlessUpdateState.value as? SeamlessUpdateState.Ready ?: return
        scope.launch {
            _seamlessUpdateState.value = SeamlessUpdateState.Installing("Installazione in corso...")
            try {
                installerProvider().install(readyState.filePath, readyState.appId).collect { event ->
                    when (event) {
                        is InstallEvent.Preparing -> {
                            _seamlessUpdateState.value = SeamlessUpdateState.Installing("Preparazione installazione...")
                        }
                        is InstallEvent.AwaitingUserAction -> {
                            _seamlessUpdateState.value = SeamlessUpdateState.Installing(event.message)
                        }
                        is InstallEvent.Installing -> {
                            _seamlessUpdateState.value = SeamlessUpdateState.Installing(event.message)
                        }
                        is InstallEvent.Success -> {
                            _seamlessUpdateState.value = SeamlessUpdateState.Idle
                        }
                        is InstallEvent.Failure -> {
                            _seamlessUpdateState.value = SeamlessUpdateState.Error(event.message)
                        }
                    }
                }
            } catch (e: Exception) {
                _seamlessUpdateState.value = SeamlessUpdateState.Error(
                    e.message ?: "Installazione non riuscita."
                )
            }
        }
    }

    fun dismissUpdate() {
        _seamlessUpdateState.value = SeamlessUpdateState.Dismissed
    }

    private suspend fun reconcileInstalledInfo(entry: AppEntry): InstalledAppInfo? {
        val storedInfo = preferencesRepository.getInstalledInfo(entry.id)
        if (platformProvider() != AppPlatform.ANDROID) {
            return storedInfo
        }
        if (entry.packageName.isBlank()) {
            return storedInfo
        }

        val resolvedVersion = platformAppService.resolveInstalledVersion(entry.packageName)
        if (resolvedVersion == null) {
            if (storedInfo != null) {
                preferencesRepository.markUninstalled(entry.id)
            }
            return null
        }

        val resolvedChannel = inferChannel(entry, resolvedVersion, storedInfo)
        if (storedInfo == null || storedInfo.version != resolvedVersion || storedInfo.channel != resolvedChannel) {
            preferencesRepository.markInstalled(
                appId = entry.id,
                version = resolvedVersion,
                channel = resolvedChannel,
                path = storedInfo?.installPath.orEmpty()
            )
        }

        return preferencesRepository.getInstalledInfo(entry.id)
    }

    private fun inferChannel(entry: AppEntry, version: String, storedInfo: InstalledAppInfo?): Channel {
        return when (version) {
            entry.beta?.version -> Channel.BETA
            entry.stable?.version -> Channel.STABLE
            else -> storedInfo?.channel ?: preferencesRepository.getDefaultChannel()
        }
    }

    private fun resolveLatestVersionForInstalledChannel(
        entry: AppEntry,
        installed: InstalledAppInfo?
    ): String? {
        return when (installed?.channel) {
            Channel.STABLE -> entry.stable?.version ?: entry.beta?.version
            Channel.BETA -> entry.beta?.version ?: entry.stable?.version
            null -> entry.stable?.version ?: entry.beta?.version
        }
    }

    private suspend fun downloadAndInstallAsset(
        installId: String,
        source: ReleaseAssetSource,
        savePath: String,
        updateState: (DownloadState) -> Unit,
        onInstallSuccess: suspend (String) -> Unit
    ) {
        val service = appRepository ?: run {
            updateState(DownloadState.Error("Catalogo non disponibile."))
            return
        }

        try {
            service.downloadAsset(
                source = source,
                savePath = savePath,
                onProgress = { }
            ).collect { event ->
                when (event) {
                    is DownloadEvent.Started -> updateState(DownloadState.Downloading(0f, 0, 0))
                    is DownloadEvent.Progress -> updateState(
                        DownloadState.Downloading(
                            progress = event.progress,
                            downloadedBytes = event.downloadedBytes,
                            totalBytes = event.totalBytes
                        )
                    )

                    is DownloadEvent.Completed -> {
                        updateState(DownloadState.Verifying("Preparazione installazione..."))
                        installerProvider().install(event.filePath, installId).collect { installEvent ->
                            when (installEvent) {
                                InstallEvent.Preparing -> {
                                    updateState(DownloadState.Verifying("Preparazione installazione..."))
                                }

                                is InstallEvent.AwaitingUserAction -> {
                                    updateState(DownloadState.AwaitingUserAction(installEvent.message))
                                }

                                is InstallEvent.Installing -> {
                                    updateState(DownloadState.Installing(installEvent.message))
                                }

                                InstallEvent.Success -> {
                                    onInstallSuccess(event.filePath)
                                    updateState(DownloadState.Installed(event.filePath))
                                }

                                is InstallEvent.Failure -> {
                                    updateState(DownloadState.Error(installEvent.message))
                                }
                            }
                        }
                    }

                    is DownloadEvent.Error -> updateState(DownloadState.Error(event.message, event.details))
                }
            }
        } catch (error: Exception) {
            updateState(DownloadState.Error(error.message ?: "Download non riuscito."))
        }
    }

    private fun releaseSourceFor(entry: AppEntry, versionInfo: VersionInfo): ReleaseAssetSource? {
        val assetName = assetNameForPlatform(versionInfo)
        if (assetName.isBlank()) return null
        return ReleaseAssetSource(
            repository = entry.repository,
            releaseTag = versionInfo.releaseTag,
            assetName = assetName
        )
    }

    private fun assetNameForPlatform(versionInfo: VersionInfo): String {
        return when (platformProvider()) {
            AppPlatform.ANDROID -> versionInfo.apkAsset
            AppPlatform.WINDOWS -> versionInfo.exeAsset
        }
    }

    private fun updateDownloadState(appId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            this[appId] = state
        }
    }

    private fun buildSavePath(downloadDirectory: String, assetName: String): String {
        val separator = when {
            downloadDirectory.endsWith("/") || downloadDirectory.endsWith("\\") -> ""
            downloadDirectory.contains("\\") && !downloadDirectory.contains("/") -> "\\"
            else -> "/"
        }
        return downloadDirectory + separator + assetName
    }

    fun clearError() {
        _error.value = null
    }

    fun onConfigUpdated() {
        refreshApps()
        if (preferencesRepository.preferences.value.storeUpdateAutoCheckEnabled) {
            checkStoreUpdate()
        }
    }

    fun dispose() {
        appRepository?.close()
        scope.cancel()
    }
}
