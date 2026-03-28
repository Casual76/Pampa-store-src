package com.pampa.store.viewmodel

import com.pampa.store.automation.NoOpReleaseAutomationService
import com.pampa.store.automation.ReleaseAutomationEvent
import com.pampa.store.automation.ReleaseAutomationService
import com.pampa.store.data.api.AdminPublishingService
import com.pampa.store.data.api.CatalogService
import com.pampa.store.data.api.DisabledAdminPublishingService
import com.pampa.store.data.api.GitHubCatalogService
import com.pampa.store.data.api.RepositoryBootstrapMetadata
import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.AppManifest
import com.pampa.store.data.model.ApkValidationMetadata
import com.pampa.store.data.model.ApkValidationResult
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DEFAULT_GITHUB_MANIFEST_PATH
import com.pampa.store.data.model.DEFAULT_STORE_OWNER
import com.pampa.store.data.model.DEFAULT_STORE_REPO
import com.pampa.store.data.model.DetectedArtifact
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.data.model.PendingReleaseDraft
import com.pampa.store.data.model.PendingReleaseDraftState
import com.pampa.store.data.model.ReleaseArtifactKind
import com.pampa.store.data.model.ReleaseAutomationProfile
import com.pampa.store.data.model.ReleaseAutomationTarget
import com.pampa.store.data.model.STORE_APP_ID
import com.pampa.store.data.model.StoreIndexEntry
import com.pampa.store.data.model.StoreIndexManifest
import com.pampa.store.data.model.StoreManifest
import com.pampa.store.data.model.VersionInfo
import com.pampa.store.data.model.hasSignature
import com.pampa.store.data.model.isConfigured
import com.pampa.store.data.model.normalized
import com.pampa.store.data.model.toRepositoryRef
import com.pampa.store.data.model.toStoreIndexEntry
import com.pampa.store.platform.ApkValidationService
import com.pampa.store.platform.createApkValidationService
import com.pampa.store.platform.readFileBytes
import com.pampa.store.util.compareVersions
import com.pampa.store.util.isVersionNewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AdminViewModel(
    private val localStorage: LocalStorage,
    private val storeRepository: GitHubRepositoryRef = GitHubRepositoryRef(
        repoOwner = DEFAULT_STORE_OWNER,
        repoName = DEFAULT_STORE_REPO,
        manifestPath = DEFAULT_GITHUB_MANIFEST_PATH
    ),
    private val catalogService: CatalogService = GitHubCatalogService(storeRepository),
    private val publishingService: AdminPublishingService = DisabledAdminPublishingService(
        "Publishing non configurato per questa piattaforma."
    ),
    private val apkValidationService: ApkValidationService = createApkValidationService(),
    private val releaseAutomationService: ReleaseAutomationService = NoOpReleaseAutomationService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _manifest = MutableStateFlow(StoreManifest())
    val manifest: StateFlow<StoreManifest> = _manifest.asStateFlow()

    private val _storeIndex = MutableStateFlow(StoreIndexManifest())
    val storeIndex: StateFlow<StoreIndexManifest> = _storeIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _editingApp = MutableStateFlow<AppEntry?>(null)
    val editingApp: StateFlow<AppEntry?> = _editingApp.asStateFlow()

    private val _showEditor = MutableStateFlow(false)
    val showEditor: StateFlow<Boolean> = _showEditor.asStateFlow()

    private val _publishingEnabled = MutableStateFlow(publishingService.isConfigured)
    val publishingEnabled: StateFlow<Boolean> = _publishingEnabled.asStateFlow()

    private val _publishingDisabledReason = MutableStateFlow(publishingService.disabledReason)
    val publishingDisabledReason: StateFlow<String?> = _publishingDisabledReason.asStateFlow()

    private val _automationProfiles = MutableStateFlow<List<ReleaseAutomationProfile>>(emptyList())
    val automationProfiles: StateFlow<List<ReleaseAutomationProfile>> = _automationProfiles.asStateFlow()

    private val _pendingReleaseDrafts = MutableStateFlow<List<PendingReleaseDraft>>(emptyList())
    val pendingReleaseDrafts: StateFlow<List<PendingReleaseDraft>> = _pendingReleaseDrafts.asStateFlow()

    private val _activePromptDraft = MutableStateFlow<PendingReleaseDraft?>(null)
    val activePromptDraft: StateFlow<PendingReleaseDraft?> = _activePromptDraft.asStateFlow()

    private var initialized = false
    private var preferencesObserverStarted = false
    private var automationEventsObserverStarted = false
    private var automationStarted = false

    fun init() {
        if (initialized) return
        initialized = true

        startPreferencesObserver()
        startAutomationEventsObserver()

        scope.launch {
            localStorage.init()
            if (publishingService.isConfigured) {
                ensureAutomationStarted()
                releaseAutomationService.updateProfiles(_automationProfiles.value)
            }
            loadManifest()
        }
    }

    private fun startPreferencesObserver() {
        if (preferencesObserverStarted) return
        preferencesObserverStarted = true

        scope.launch {
            localStorage.preferences.collect { prefs ->
                _automationProfiles.value = prefs.releaseAutomationProfiles.sortedBy { it.name.lowercase() }
                _pendingReleaseDrafts.value = sortDrafts(prefs.pendingReleaseDrafts)
                _activePromptDraft.value = _pendingReleaseDrafts.value.firstOrNull { it.state == PendingReleaseDraftState.READY }

                if (publishingService.isConfigured) {
                    ensureAutomationStarted()
                    releaseAutomationService.updateProfiles(_automationProfiles.value)
                } else {
                    releaseAutomationService.updateProfiles(emptyList())
                }
            }
        }
    }

    private fun startAutomationEventsObserver() {
        if (automationEventsObserverStarted) return
        automationEventsObserverStarted = true

        scope.launch {
            releaseAutomationService.events.collect { event ->
                when (event) {
                    is ReleaseAutomationEvent.ArtifactDetected -> handleDetectedArtifact(event.profileId, event.artifact)
                    is ReleaseAutomationEvent.ProfileError -> handleAutomationProfileError(event.profileId, event.message)
                }
            }
        }
    }

    private fun ensureAutomationStarted() {
        if (automationStarted) return
        automationStarted = true
        releaseAutomationService.start()
    }

    private fun loadManifest() {
        scope.launch {
            _isLoading.value = true
            val publicManifestResult = catalogService.getManifest()
            val storeIndexResult = if (publishingService.isConfigured) {
                publishingService.getStoreIndex()
            } else {
                Result.success(StoreIndexManifest())
            }

            val publicManifest = publicManifestResult.getOrElse {
                _message.value = "Impossibile caricare il catalogo: ${it.message}"
                StoreManifest()
            }
            val storeIndex = storeIndexResult.getOrElse {
                _message.value = it.message ?: "Impossibile caricare l'indice store."
                StoreIndexManifest()
            }

            _storeIndex.value = storeIndex
            _manifest.value = mergeStoreData(
                storeIndex = storeIndex,
                publicManifest = publicManifest
            )
            _isLoading.value = false
        }
    }

    fun startNewApp() {
        if (!requirePublishingEnabled()) return
        _editingApp.value = AppEntry(
            id = "",
            name = "",
            description = "",
            category = "General",
            developer = "Pampa",
            repository = GitHubRepositoryRef(
                repoOwner = storeRepository.repoOwner,
                repoName = "",
                manifestPath = DEFAULT_GITHUB_MANIFEST_PATH
            )
        )
        _showEditor.value = true
    }

    fun editApp(app: AppEntry) {
        if (!requirePublishingEnabled()) return
        _editingApp.value = app
        _showEditor.value = true
    }

    fun closeEditor() {
        _editingApp.value = null
        _showEditor.value = false
    }

    fun saveApp(app: AppEntry) {
        if (!requirePublishingEnabled()) return
        scope.launch {
            val normalizedApp = normalizeEditableApp(app) ?: return@launch
            _isSaving.value = true
            val repositoryReady = ensureRepositoryReady(normalizedApp)
            if (!repositoryReady) {
                _isSaving.value = false
                return@launch
            }
            publishingService.updateAppManifest(
                repository = normalizedApp.repository,
                manifest = AppManifest(normalizedApp)
            ).fold(
                onSuccess = {
                    val updatedIndex = upsertStoreIndexEntry(normalizedApp.toStoreIndexEntry())
                    publishingService.updateStoreIndex(updatedIndex).fold(
                        onSuccess = {
                            _storeIndex.value = updatedIndex
                            _manifest.value = mergeStoreData(
                                storeIndex = updatedIndex,
                                publicManifest = _manifest.value.copy(
                                    apps = upsertManifestApp(normalizedApp)
                                )
                            )
                            _message.value = "Scheda app salvata."
                            closeEditor()
                        },
                        onFailure = { _message.value = it.message ?: "Aggiornamento indice store non riuscito." }
                    )
                },
                onFailure = { _message.value = it.message ?: "Salvataggio manifest app non riuscito." }
            )
            _isSaving.value = false
        }
    }

    fun deleteApp(appId: String) {
        if (!requirePublishingEnabled()) return
        scope.launch {
            _isSaving.value = true
            val updatedIndex = _storeIndex.value.copy(
                apps = _storeIndex.value.apps.filter { it.id != appId }
            )
            publishingService.updateStoreIndex(updatedIndex).fold(
                onSuccess = {
                    _storeIndex.value = updatedIndex
                    _manifest.value = _manifest.value.copy(
                        apps = _manifest.value.apps.filter { it.id != appId }
                    )
                    _message.value = "App rimossa dallo store."
                },
                onFailure = { _message.value = it.message ?: "Eliminazione non riuscita." }
            )
            _isSaving.value = false
        }
    }

    suspend fun validateApk(path: String): ApkValidationResult {
        return apkValidationService.validate(path)
    }

    fun addVersion(appId: String, channel: Channel, versionInfo: VersionInfo, apkPath: String?, exePath: String?) {
        if (!requirePublishingEnabled()) return
        scope.launch {
            _isSaving.value = true
            publishAppVersionInternal(
                appId = appId,
                channel = channel,
                versionInfo = versionInfo,
                apkPath = apkPath,
                exePath = exePath
            ).fold(
                onSuccess = { _message.value = it },
                onFailure = { _message.value = it.message ?: "Pubblicazione release fallita." }
            )
            _isSaving.value = false
        }
    }

    fun promoteAppBetaToStable(appId: String) {
        if (!requirePublishingEnabled()) return
        scope.launch {
            _isSaving.value = true
            val app = _manifest.value.apps.firstOrNull { it.id == appId }
            if (app == null) {
                _message.value = "App non trovata nel catalogo corrente."
                _isSaving.value = false
                return@launch
            }

            val betaVersion = app.beta
            if (betaVersion == null) {
                _message.value = "Nessuna versione beta da promuovere."
                _isSaving.value = false
                return@launch
            }

            val updatedHistory = app.stable?.let { listOf(it) + app.history } ?: app.history
            val updatedApp = app.copy(
                stable = betaVersion,
                history = updatedHistory
            )

            persistAppAndIndex(
                updatedApp = updatedApp,
                successMessage = "Versione beta ${betaVersion.version} promossa a stable."
            )
            _isSaving.value = false
        }
    }

    fun publishStoreUpdate(
        version: String,
        channel: Channel,
        changelog: String,
        exePath: String?,
        apkPath: String?
    ) {
        if (!requirePublishingEnabled()) return
        scope.launch {
            _isSaving.value = true
            publishStoreUpdateInternal(
                version = version,
                channel = channel,
                changelog = changelog,
                exePath = exePath,
                apkPath = apkPath
            ).fold(
                onSuccess = { _message.value = it },
                onFailure = { _message.value = it.message ?: "Pubblicazione aggiornamento store fallita." }
            )
            _isSaving.value = false
        }
    }

    fun promoteStoreBetaToStable() {
        if (!requirePublishingEnabled()) return
        scope.launch {
            _isSaving.value = true
            val betaVersion = _storeIndex.value.storeReleases.beta
            if (betaVersion == null) {
                _message.value = "Nessuna versione beta dello store da promuovere."
                _isSaving.value = false
                return@launch
            }

            val updatedIndex = _storeIndex.value.copy(
                storeReleases = _storeIndex.value.storeReleases.copy(stable = betaVersion)
            )
            publishingService.updateStoreIndex(updatedIndex).fold(
                onSuccess = {
                    _storeIndex.value = updatedIndex
                    _message.value = "Versione beta ${betaVersion.version} dello store promossa a stable."
                },
                onFailure = {
                    _message.value = it.message ?: "Promozione store beta -> stable fallita."
                }
            )
            _isSaving.value = false
        }
    }

    fun saveAutomationProfile(profile: ReleaseAutomationProfile) {
        scope.launch {
            val normalizedProfile = normalizeAutomationProfile(profile) ?: return@launch
            val updatedProfiles = _automationProfiles.value
                .filterNot { it.id == normalizedProfile.id }
                .plus(normalizedProfile)

            localStorage.saveReleaseAutomationProfiles(updatedProfiles)
            _message.value = "Profilo '${normalizedProfile.name}' salvato."
        }
    }

    fun deleteAutomationProfile(profileId: String) {
        scope.launch {
            localStorage.saveReleaseAutomationProfiles(_automationProfiles.value.filterNot { it.id == profileId })
            localStorage.savePendingReleaseDrafts(_pendingReleaseDrafts.value.filterNot { it.profileId == profileId })
            _message.value = "Profilo automazione rimosso."
        }
    }

    fun setAutomationProfileEnabled(profileId: String, enabled: Boolean) {
        scope.launch {
            val updatedProfiles = _automationProfiles.value.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(
                        enabled = enabled,
                        lastError = if (enabled) profile.lastError else "",
                        updatedAt = com.pampa.store.data.local.currentTimeMillis()
                    )
                } else {
                    profile
                }
            }
            localStorage.saveReleaseAutomationProfiles(updatedProfiles)
        }
    }

    fun deferAutomationDraft(draftId: String) {
        scope.launch {
            updateDraftState(
                draftId = draftId,
                state = PendingReleaseDraftState.DEFERRED,
                errorMessage = ""
            )
        }
    }

    fun retryAutomationDraft(draftId: String) {
        scope.launch {
            updateDraftState(
                draftId = draftId,
                state = PendingReleaseDraftState.READY,
                errorMessage = ""
            )
        }
    }

    fun dismissAutomationDraft(draftId: String) {
        scope.launch {
            val draft = _pendingReleaseDrafts.value.firstOrNull { it.id == draftId } ?: return@launch
            val remainingDrafts = _pendingReleaseDrafts.value.filterNot { it.id == draftId }
            localStorage.savePendingReleaseDrafts(remainingDrafts)
            localStorage.markReleaseArtifactsHandled(draft.signatures())
            _message.value = "Bozza ${draft.profileName} ${draft.version} scartata."
        }
    }

    fun publishAutomationDraft(draftId: String, channel: Channel) {
        if (!requirePublishingEnabled()) return
        scope.launch {
            val draft = _pendingReleaseDrafts.value.firstOrNull { it.id == draftId } ?: return@launch
            _isSaving.value = true

            val publishResult = when (val target = draft.target) {
                ReleaseAutomationTarget.Store -> publishStoreUpdateInternal(
                    version = draft.version,
                    channel = channel,
                    changelog = draft.changelog,
                    exePath = draft.windowsArtifact?.path,
                    apkPath = draft.apkArtifact?.path
                )

                is ReleaseAutomationTarget.App -> publishAppVersionInternal(
                    appId = target.appId,
                    channel = channel,
                    versionInfo = VersionInfo(
                        version = draft.version,
                        releaseDate = todayAsString(),
                        changelog = draft.changelog,
                        releaseTag = "",
                        apkAsset = "",
                        exeAsset = ""
                    ),
                    apkPath = draft.apkArtifact?.path,
                    exePath = draft.windowsArtifact?.path
                )
            }

            publishResult.fold(
                onSuccess = { successMessage ->
                    localStorage.savePendingReleaseDrafts(_pendingReleaseDrafts.value.filterNot { it.id == draftId })
                    localStorage.markReleaseArtifactsHandled(draft.signatures())
                    _message.value = successMessage
                },
                onFailure = { error ->
                    updateDraftState(
                        draftId = draft.id,
                        state = PendingReleaseDraftState.FAILED,
                        errorMessage = error.message ?: "Pubblicazione automatica fallita."
                    )
                    _message.value = error.message ?: "Pubblicazione automatica fallita."
                }
            )

            _isSaving.value = false
        }
    }

    private suspend fun publishAppVersionInternal(
        appId: String,
        channel: Channel,
        versionInfo: VersionInfo,
        apkPath: String?,
        exePath: String?
    ): Result<String> = runCatching {
        val apkMetadata = requirePublishableApk(apkPath).getOrThrow()
        val app = findExistingApp(appId)
            ?: throw IllegalStateException("Salva prima l'app e poi pubblica la versione.")

        if (!app.repository.isConfigured()) {
            throw IllegalStateException("Configura owner e nome della repo dell'app prima di pubblicare.")
        }

        ensureRepositoryReadyOrThrow(app)

        val normalizedVersion = versionInfo.version.trim()
        if (normalizedVersion.isBlank()) {
            throw IllegalStateException("Inserisci una versione valida.")
        }

        val currentComparableVersion = resolveCurrentComparableAppVersion(app, channel)
        if (!currentComparableVersion.isNullOrBlank() && !isVersionNewer(normalizedVersion, currentComparableVersion)) {
            throw IllegalStateException(
                "Versione $normalizedVersion non pubblicabile nel canale ${channel.displayName}. " +
                    "La versione corrente e' $currentComparableVersion. Usa una versione maggiore."
            )
        }

        val resolvedTag = versionInfo.releaseTag.trim().ifBlank {
            defaultReleaseTag(appId = appId, channel = channel, version = normalizedVersion)
        }
        val resolvedVersionInfo = versionInfo.copy(
            version = normalizedVersion,
            releaseDate = versionInfo.releaseDate.ifBlank { todayAsString() },
            releaseTag = resolvedTag,
            apkAsset = if (apkPath != null) "$appId-$normalizedVersion.apk" else "",
            exeAsset = buildWindowsAssetName(appId, normalizedVersion, exePath)
        )

        val release = publishingService.createRelease(
            repository = app.repository,
            tag = resolvedVersionInfo.releaseTag,
            name = when (channel) {
                Channel.STABLE -> "${app.name} ${resolvedVersionInfo.version}"
                Channel.BETA -> "${app.name} Beta ${resolvedVersionInfo.version}"
            },
            body = resolvedVersionInfo.changelog,
            prerelease = channel == Channel.BETA
        ).getOrElse { throw it }

        uploadAssets(
            releaseUploadUrl = release.upload_url,
            apkPath = apkPath,
            apkAssetName = resolvedVersionInfo.apkAsset,
            exePath = exePath,
            exeAssetName = resolvedVersionInfo.exeAsset
        ).getOrElse { throw it }

        val previousVersion = when (channel) {
            Channel.STABLE -> app.stable
            Channel.BETA -> app.beta
        }
        val updatedHistory = if (previousVersion != null) {
            listOf(previousVersion) + app.history
        } else {
            app.history
        }
        val resolvedPackageName = apkMetadata?.packageName?.ifBlank { app.packageName } ?: app.packageName
        val updatedApp = when (channel) {
            Channel.STABLE -> app.copy(
                stable = resolvedVersionInfo,
                beta = resolvedVersionInfo,
                history = updatedHistory,
                packageName = resolvedPackageName
            )

            Channel.BETA -> app.copy(
                beta = resolvedVersionInfo,
                history = updatedHistory,
                packageName = resolvedPackageName
            )
        }

        persistAppAndIndexOrThrow(updatedApp)

        when (channel) {
            Channel.STABLE -> "Versione stable ${resolvedVersionInfo.version} pubblicata (mirror su beta)."
            Channel.BETA -> "Versione beta ${resolvedVersionInfo.version} pubblicata."
        }
    }

    private suspend fun publishStoreUpdateInternal(
        version: String,
        channel: Channel,
        changelog: String,
        exePath: String?,
        apkPath: String?
    ): Result<String> = runCatching {
        val normalizedVersion = version.trim()
        if (normalizedVersion.isBlank()) {
            throw IllegalStateException("Inserisci una versione valida per lo store.")
        }

        requirePublishableApk(apkPath).getOrThrow()

        val currentComparableVersion = resolveCurrentComparableStoreVersion(channel).getOrElse { throw it }
        if (!currentComparableVersion.isNullOrBlank() && !isVersionNewer(normalizedVersion, currentComparableVersion)) {
            throw IllegalStateException(
                "Versione $normalizedVersion non pubblicabile nel canale ${channel.displayName}. " +
                    "La versione corrente e' $currentComparableVersion. Usa una versione maggiore."
            )
        }

        val tag = defaultReleaseTag(
            appId = STORE_APP_ID,
            channel = channel,
            version = normalizedVersion
        )
        val apkAsset = if (apkPath != null) "$STORE_APP_ID-$normalizedVersion.apk" else ""
        val exeAsset = buildWindowsAssetName(STORE_APP_ID, normalizedVersion, exePath)
        val releaseVersionInfo = VersionInfo(
            version = normalizedVersion,
            releaseDate = todayAsString(),
            changelog = changelog,
            releaseTag = tag,
            apkAsset = apkAsset,
            exeAsset = exeAsset
        )

        val release = publishingService.createStoreRelease(
            tag = tag,
            name = when (channel) {
                Channel.STABLE -> "Pampa Store $normalizedVersion"
                Channel.BETA -> "Pampa Store Beta $normalizedVersion"
            },
            body = changelog,
            prerelease = channel == Channel.BETA
        ).getOrElse { throw it }

        uploadAssets(
            releaseUploadUrl = release.upload_url,
            apkPath = apkPath,
            apkAssetName = apkAsset,
            exePath = exePath,
            exeAssetName = exeAsset
        ).getOrElse { throw it }

        val updatedStoreReleases = when (channel) {
            Channel.STABLE -> _storeIndex.value.storeReleases.copy(
                stable = releaseVersionInfo,
                beta = releaseVersionInfo
            )

            Channel.BETA -> _storeIndex.value.storeReleases.copy(
                beta = releaseVersionInfo
            )
        }
        val updatedIndex = _storeIndex.value.copy(storeReleases = updatedStoreReleases)
        publishingService.updateStoreIndex(updatedIndex).getOrElse { throw it }
        _storeIndex.value = updatedIndex

        when (channel) {
            Channel.STABLE -> "Aggiornamento stable di Pampa Store pubblicato (mirror su beta)."
            Channel.BETA -> "Aggiornamento beta di Pampa Store pubblicato."
        }
    }

    private suspend fun handleDetectedArtifact(profileId: String, artifact: DetectedArtifact) {
        val currentPrefs = localStorage.preferences.value
        val profile = currentPrefs.releaseAutomationProfiles.firstOrNull { it.id == profileId && it.enabled } ?: return
        if (localStorage.isReleaseArtifactHandled(artifact.signature)) return

        val existingDrafts = currentPrefs.pendingReleaseDrafts
        val duplicateDraft = existingDrafts.firstOrNull { it.hasSignature(artifact.signature) }
        if (duplicateDraft != null) return

        val draft = existingDrafts.firstOrNull { it.profileId == profileId && it.version == artifact.version }
        val now = com.pampa.store.data.local.currentTimeMillis()
        val updatedDraft = if (draft == null) {
            PendingReleaseDraft(
                id = generateAutomationDraftId(profile, artifact.version),
                profileId = profile.id,
                profileName = profile.name,
                target = profile.target,
                version = artifact.version,
                changelog = buildDefaultAutomationChangelog(
                    profile = profile,
                    version = artifact.version,
                    apkArtifact = artifact.takeIf { it.kind == ReleaseArtifactKind.APK },
                    windowsArtifact = artifact.takeIf { it.kind == ReleaseArtifactKind.WINDOWS }
                ),
                apkArtifact = artifact.takeIf { it.kind == ReleaseArtifactKind.APK },
                windowsArtifact = artifact.takeIf { it.kind == ReleaseArtifactKind.WINDOWS },
                state = PendingReleaseDraftState.READY,
                createdAt = now,
                updatedAt = now
            )
        } else {
            val mergedDraft = when (artifact.kind) {
                ReleaseArtifactKind.APK -> draft.copy(apkArtifact = artifact)
                ReleaseArtifactKind.WINDOWS -> draft.copy(windowsArtifact = artifact)
            }
            mergedDraft.copy(
                changelog = buildDefaultAutomationChangelog(
                    profile = profile,
                    version = artifact.version,
                    apkArtifact = mergedDraft.apkArtifact,
                    windowsArtifact = mergedDraft.windowsArtifact
                ),
                state = PendingReleaseDraftState.READY,
                errorMessage = "",
                updatedAt = now
            )
        }

        val updatedDrafts = existingDrafts
            .filterNot { it.id == updatedDraft.id }
            .plus(updatedDraft)
        localStorage.savePendingReleaseDrafts(updatedDrafts)

        val updatedProfiles = currentPrefs.releaseAutomationProfiles.map { currentProfile ->
            if (currentProfile.id == profileId) {
                currentProfile.copy(
                    lastError = "",
                    lastDetectedVersion = artifact.version,
                    lastDetectedAt = now,
                    updatedAt = now
                )
            } else {
                currentProfile
            }
        }
        localStorage.saveReleaseAutomationProfiles(updatedProfiles)
        _message.value = "Bozza release rilevata: ${profile.name} ${artifact.version}."
    }

    private suspend fun handleAutomationProfileError(profileId: String, message: String?) {
        val normalizedMessage = message?.trim().orEmpty()
        val updatedProfiles = localStorage.preferences.value.releaseAutomationProfiles.map { profile ->
            if (profile.id == profileId) {
                profile.copy(lastError = normalizedMessage)
            } else {
                profile
            }
        }
        if (updatedProfiles != localStorage.preferences.value.releaseAutomationProfiles) {
            localStorage.saveReleaseAutomationProfiles(updatedProfiles)
        }
    }

    private suspend fun updateDraftState(
        draftId: String,
        state: PendingReleaseDraftState,
        errorMessage: String
    ) {
        val now = com.pampa.store.data.local.currentTimeMillis()
        val updatedDrafts = localStorage.preferences.value.pendingReleaseDrafts.map { draft ->
            if (draft.id == draftId) {
                draft.copy(
                    state = state,
                    errorMessage = errorMessage,
                    updatedAt = now
                )
            } else {
                draft
            }
        }
        localStorage.savePendingReleaseDrafts(updatedDrafts)
    }

    private suspend fun persistAppAndIndex(updatedApp: AppEntry, successMessage: String) {
        publishingService.updateAppManifest(
            repository = updatedApp.repository,
            manifest = AppManifest(updatedApp)
        ).fold(
            onSuccess = {
                val updatedIndex = upsertStoreIndexEntry(updatedApp.toStoreIndexEntry())
                publishingService.updateStoreIndex(updatedIndex).fold(
                    onSuccess = {
                        _storeIndex.value = updatedIndex
                        _manifest.value = mergeStoreData(
                            storeIndex = updatedIndex,
                            publicManifest = _manifest.value.copy(
                                apps = upsertManifestApp(updatedApp)
                            )
                        )
                        _message.value = successMessage
                    },
                    onFailure = {
                        _message.value = it.message ?: "Aggiornamento indice store fallito."
                    }
                )
            },
            onFailure = {
                _message.value = it.message ?: "Aggiornamento manifest app fallito."
            }
        )
    }

    private suspend fun persistAppAndIndexOrThrow(updatedApp: AppEntry) {
        publishingService.updateAppManifest(
            repository = updatedApp.repository,
            manifest = AppManifest(updatedApp)
        ).getOrElse { throw it }

        val updatedIndex = upsertStoreIndexEntry(updatedApp.toStoreIndexEntry())
        publishingService.updateStoreIndex(updatedIndex).getOrElse { throw it }

        _storeIndex.value = updatedIndex
        _manifest.value = mergeStoreData(
            storeIndex = updatedIndex,
            publicManifest = _manifest.value.copy(
                apps = upsertManifestApp(updatedApp)
            )
        )
    }

    private suspend fun resolveCurrentComparableStoreVersion(channel: Channel): Result<String?> = runCatching {
        val stableVersion = _storeIndex.value.storeReleases.stable?.version?.trim().orEmpty()
        val betaVersion = _storeIndex.value.storeReleases.beta?.version?.trim().orEmpty()

        when (channel) {
            Channel.STABLE -> {
                val releaseApiVersion = if (stableVersion.isBlank() && betaVersion.isBlank()) {
                    publishingService.getLatestStoreRelease()
                        .getOrElse { throw it }
                        ?.let { extractStoreVersion(it.tag_name, it.name) }
                        .orEmpty()
                } else {
                    ""
                }
                maxKnownVersion(stableVersion, betaVersion, releaseApiVersion)
            }

            Channel.BETA -> maxKnownVersion(betaVersion, stableVersion)
        }
    }

    private fun resolveCurrentComparableAppVersion(app: AppEntry, channel: Channel): String? {
        return when (channel) {
            Channel.STABLE -> maxKnownVersion(
                app.stable?.version.orEmpty(),
                app.beta?.version.orEmpty()
            )

            Channel.BETA -> maxKnownVersion(
                app.beta?.version.orEmpty(),
                app.stable?.version.orEmpty()
            )
        }
    }

    private suspend fun findExistingApp(appId: String): AppEntry? {
        val current = _manifest.value.apps.find { it.id == appId }
        if (current != null) return current

        return catalogService.getManifest().getOrNull()
            ?.also { _manifest.value = it }
            ?.apps
            ?.find { it.id == appId }
    }

    private suspend fun requirePublishableApk(apkPath: String?): Result<ApkValidationMetadata?> = runCatching {
        if (apkPath == null) {
            return@runCatching null
        }

        when (val result = apkValidationService.validate(apkPath)) {
            is ApkValidationResult.Valid -> result.metadata
            is ApkValidationResult.Invalid -> {
                throw IllegalStateException(result.issues.firstOrNull() ?: "APK non valido.")
            }

            is ApkValidationResult.Unsupported -> {
                throw IllegalStateException(result.message)
            }
        }
    }

    private suspend fun uploadAssets(
        releaseUploadUrl: String,
        apkPath: String?,
        apkAssetName: String,
        exePath: String?,
        exeAssetName: String
    ): Result<Unit> = runCatching {
        if (apkPath != null && apkAssetName.isNotBlank()) {
            publishingService.uploadAsset(
                uploadUrl = releaseUploadUrl,
                fileBytes = readFileBytes(apkPath),
                fileName = apkAssetName,
                contentType = "application/vnd.android.package-archive"
            ).getOrThrow()
        }
        if (exePath != null && exeAssetName.isNotBlank()) {
            publishingService.uploadAsset(
                uploadUrl = releaseUploadUrl,
                fileBytes = readFileBytes(exePath),
                fileName = exeAssetName,
                contentType = windowsAssetContentType(exePath)
            ).getOrThrow()
        }
    }

    private fun buildWindowsAssetName(appId: String, version: String, windowsPath: String?): String {
        if (windowsPath.isNullOrBlank()) return ""
        val extension = windowsPath.substringAfterLast('.', "").lowercase()
            .takeIf { it == "exe" || it == "msi" }
            ?: "exe"
        return "$appId-$version-installer.$extension"
    }

    private fun windowsAssetContentType(windowsPath: String): String {
        return when (windowsPath.substringAfterLast('.', "").lowercase()) {
            "msi" -> "application/x-msi"
            else -> "application/x-msdownload"
        }
    }

    private suspend fun ensureRepositoryReady(app: AppEntry): Boolean {
        return runCatching {
            ensureRepositoryReadyOrThrow(app)
            true
        }.getOrElse {
            _message.value = it.message ?: "Repository non pronta per la pubblicazione."
            false
        }
    }

    private suspend fun ensureRepositoryReadyOrThrow(app: AppEntry) {
        val metadata = RepositoryBootstrapMetadata(
            appId = app.id.trim(),
            appName = app.name.trim(),
            description = app.description.trim(),
            category = app.category.trim().ifBlank { "General" },
            developer = app.developer.trim().ifBlank { "Pampa" }
        )

        val result = publishingService.ensureRepository(app.repository, metadata).getOrElse { throw it }
        if (result.failed) {
            throw IllegalStateException(
                result.reason.ifBlank {
                    "Repository ${app.repository.repoOwner}/${app.repository.repoName} non pronta per la pubblicazione."
                }
            )
        }
    }

    private fun normalizeEditableApp(app: AppEntry): AppEntry? {
        val normalizedId = app.id.trim()
        val normalizedRepository = app.repository.normalized()
        if (normalizedId.isBlank()) {
            _message.value = "Inserisci un ID app."
            return null
        }
        if (normalizedId == STORE_APP_ID) {
            _message.value = "Pampa Store non fa parte del catalogo app. Aggiorna lo store dal riquadro dedicato."
            return null
        }
        if (!normalizedRepository.isConfigured()) {
            _message.value = "Compila owner e nome della repository dell'app."
            return null
        }
        return app.copy(
            id = normalizedId,
            name = app.name.trim(),
            description = app.description.trim(),
            category = app.category.trim().ifBlank { "General" },
            developer = app.developer.trim().ifBlank { "Pampa" },
            repository = normalizedRepository
        )
    }

    private fun normalizeAutomationProfile(profile: ReleaseAutomationProfile): ReleaseAutomationProfile? {
        val normalizedName = profile.name.trim()
        if (normalizedName.isBlank()) {
            _message.value = "Assegna un nome al profilo automazione."
            return null
        }
        val apkDirectory = profile.apkDirectory.trim()
        val windowsDirectory = profile.windowsDirectory.trim()
        if (apkDirectory.isBlank() && windowsDirectory.isBlank()) {
            _message.value = "Configura almeno una cartella tra APK e Windows."
            return null
        }

        val normalizedTarget = when (val target = profile.target) {
            ReleaseAutomationTarget.Store -> ReleaseAutomationTarget.Store
            is ReleaseAutomationTarget.App -> {
                val appId = target.appId.trim()
                if (appId.isBlank()) {
                    _message.value = "Seleziona un ID app valido per il profilo."
                    return null
                }
                if (_manifest.value.apps.none { it.id == appId }) {
                    _message.value = "L'app '$appId' non esiste nel catalogo corrente."
                    return null
                }
                ReleaseAutomationTarget.App(appId)
            }
        }

        val existing = _automationProfiles.value.firstOrNull { it.id == profile.id }
        val now = com.pampa.store.data.local.currentTimeMillis()
        return profile.copy(
            id = profile.id.ifBlank { generateAutomationProfileId(normalizedName) },
            name = normalizedName,
            target = normalizedTarget,
            apkDirectory = apkDirectory,
            windowsDirectory = windowsDirectory,
            lastError = existing?.lastError.orEmpty(),
            lastDetectedVersion = existing?.lastDetectedVersion.orEmpty(),
            lastDetectedAt = existing?.lastDetectedAt ?: 0L,
            updatedAt = now
        )
    }

    private fun mergeStoreData(
        storeIndex: StoreIndexManifest,
        publicManifest: StoreManifest
    ): StoreManifest {
        val publicById = publicManifest.apps
            .filterNot { it.id == STORE_APP_ID }
            .associateBy { it.id }

        val mergedApps = if (storeIndex.apps.isEmpty()) {
            publicManifest.apps.filterNot { it.id == STORE_APP_ID }
        } else {
            storeIndex.apps
                .filterNot { it.id == STORE_APP_ID }
                .map { entry ->
                    publicById[entry.id]?.copy(repository = entry.toRepositoryRef()) ?: placeholderApp(entry)
                }
        }

        return StoreManifest(
            apps = mergedApps.sortedBy { it.name.lowercase() },
            warnings = publicManifest.warnings
        )
    }

    private fun placeholderApp(entry: StoreIndexEntry): AppEntry {
        return AppEntry(
            id = entry.id,
            name = entry.id,
            description = "Manifest app non disponibile nella repository ${entry.repoOwner}/${entry.repoName}.",
            category = "General",
            developer = "Pampa",
            repository = entry.toRepositoryRef()
        )
    }

    private fun upsertStoreIndexEntry(entry: StoreIndexEntry): StoreIndexManifest {
        val existingIndex = _storeIndex.value.apps.indexOfFirst { it.id == entry.id }
        val updatedApps = if (existingIndex >= 0) {
            _storeIndex.value.apps.toMutableList().apply { this[existingIndex] = entry }
        } else {
            _storeIndex.value.apps + entry
        }
        return StoreIndexManifest(
            apps = updatedApps.sortedBy { it.id.lowercase() },
            storeReleases = _storeIndex.value.storeReleases
        )
    }

    private fun upsertManifestApp(app: AppEntry): List<AppEntry> {
        val existingIndex = _manifest.value.apps.indexOfFirst { it.id == app.id }
        return if (existingIndex >= 0) {
            _manifest.value.apps.toMutableList().apply { this[existingIndex] = app }
        } else {
            _manifest.value.apps + app
        }
    }

    private fun requirePublishingEnabled(): Boolean {
        if (publishingService.isConfigured) {
            return true
        }
        _message.value = publishingService.disabledReason ?: "Publishing non disponibile."
        return false
    }

    fun clearMessage() {
        _message.value = null
    }

    fun refresh() {
        loadManifest()
    }

    fun dispose() {
        catalogService.close()
        publishingService.close()
        releaseAutomationService.stop()
        scope.cancel()
    }
}

private fun extractStoreVersion(tagName: String, releaseName: String): String {
    val normalizedTag = tagName.trim()
    val withoutPrefix = normalizedTag
        .removePrefix("stable-")
        .removePrefix("beta-")

    return when {
        withoutPrefix.startsWith("$STORE_APP_ID-v") -> withoutPrefix.removePrefix("$STORE_APP_ID-v")
        withoutPrefix.startsWith("v") -> withoutPrefix.removePrefix("v")
        normalizedTag.startsWith("$STORE_APP_ID-v") -> normalizedTag.removePrefix("$STORE_APP_ID-v")
        normalizedTag.startsWith("v") -> normalizedTag.removePrefix("v")
        releaseName.startsWith("Pampa Store ", ignoreCase = true) -> releaseName.removePrefix("Pampa Store ").trim()
        else -> withoutPrefix
    }
}

private fun defaultReleaseTag(appId: String, channel: Channel, version: String): String {
    val normalizedAppId = appId.trim().ifBlank { STORE_APP_ID }
    val normalizedVersion = version.trim()
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

private fun sortDrafts(drafts: List<PendingReleaseDraft>): List<PendingReleaseDraft> {
    return drafts.sortedWith(
        compareBy<PendingReleaseDraft> { draftStateOrder(it.state) }
            .thenByDescending { it.updatedAt }
            .thenBy { it.profileName.lowercase() }
    )
}

private fun draftStateOrder(state: PendingReleaseDraftState): Int {
    return when (state) {
        PendingReleaseDraftState.READY -> 0
        PendingReleaseDraftState.FAILED -> 1
        PendingReleaseDraftState.DEFERRED -> 2
    }
}

private fun generateAutomationProfileId(name: String): String {
    val base = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "profilo" }
    return "$base-${com.pampa.store.data.local.currentTimeMillis()}"
}

private fun generateAutomationDraftId(profile: ReleaseAutomationProfile, version: String): String {
    return "${profile.id}-${version.trim()}-${com.pampa.store.data.local.currentTimeMillis()}"
}

private fun buildDefaultAutomationChangelog(
    profile: ReleaseAutomationProfile,
    version: String,
    apkArtifact: DetectedArtifact?,
    windowsArtifact: DetectedArtifact?
): String {
    val assets = buildList {
        apkArtifact?.fileName?.takeIf { it.isNotBlank() }?.let(::add)
        windowsArtifact?.fileName?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(", ")

    return buildString {
        append("Bozza generata automaticamente dal profilo ")
        append(profile.name)
        append(" per la versione ")
        append(version)
        append('.')
        if (assets.isNotBlank()) {
            append(' ')
            append("Asset rilevati: ")
            append(assets)
            append('.')
        }
    }
}

private fun PendingReleaseDraft.signatures(): List<String> {
    return buildList {
        apkArtifact?.signature?.takeIf { it.isNotBlank() }?.let(::add)
        windowsArtifact?.signature?.takeIf { it.isNotBlank() }?.let(::add)
    }
}

private fun maxKnownVersion(vararg versions: String): String? {
    return versions
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .maxWithOrNull { left, right -> compareVersions(left, right) }
}
