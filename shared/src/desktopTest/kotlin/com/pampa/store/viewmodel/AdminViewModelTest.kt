package com.pampa.store.viewmodel

import com.pampa.store.automation.ReleaseAutomationEvent
import com.pampa.store.automation.ReleaseAutomationService
import com.pampa.store.data.api.AdminPublishingService
import com.pampa.store.data.api.CatalogService
import com.pampa.store.data.api.DownloadEvent
import com.pampa.store.data.api.EnsureRepositoryResult
import com.pampa.store.data.api.GitHubRelease
import com.pampa.store.data.api.RepositoryBootstrapMetadata
import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.local.PreferencesStorage
import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.AppManifest
import com.pampa.store.data.model.ApkValidationResult
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DetectedArtifact
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.data.model.ReleaseArtifactKind
import com.pampa.store.data.model.ReleaseAssetSource
import com.pampa.store.data.model.ReleaseAutomationProfile
import com.pampa.store.data.model.ReleaseAutomationTarget
import com.pampa.store.data.model.StoreIndexManifest
import com.pampa.store.data.model.StoreManifest
import com.pampa.store.data.model.StoreUpdateInfo
import com.pampa.store.data.model.StoreReleaseChannels
import com.pampa.store.data.model.VersionInfo
import com.pampa.store.data.repository.DefaultAppRepository
import com.pampa.store.data.repository.DefaultPreferencesRepository
import com.pampa.store.data.repository.DefaultPublishingRepository
import com.pampa.store.platform.ApkValidationService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {
    private val originalUserHome = System.getProperty("user.home")

    @AfterTest
    fun restoreUserHome() {
        System.setProperty("user.home", originalUserHome)
    }

    @Test
    fun publishStoreUpdateBlocksVersionRegressionAgainstLatestRelease() = runTest {
        val publishingService = AdminTestPublishingService(
            latestStoreRelease = GitHubRelease(
                id = 1,
                tag_name = "pampa-store-v1.0.8",
                name = "Pampa Store 1.0.8"
            )
        )
        val viewModel = createViewModel(
            publishingService = publishingService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.publishStoreUpdate(
            version = "1.0.7",
            channel = Channel.STABLE,
            changelog = "fix",
            exePath = null,
            apkPath = null
        )
        advanceUntilIdle()

        assertEquals(0, publishingService.createStoreReleaseCalls)
        assertTrue(viewModel.message.value?.contains("non pubblicabile") == true)
        viewModel.dispose()
    }

    @Test
    fun publishStoreUpdateAllowsVersionGreaterThanLatestRelease() = runTest {
        val publishingService = AdminTestPublishingService(
            latestStoreRelease = GitHubRelease(
                id = 1,
                tag_name = "pampa-store-v1.0.8",
                name = "Pampa Store 1.0.8"
            )
        )
        val viewModel = createViewModel(
            publishingService = publishingService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.publishStoreUpdate(
            version = "1.0.9",
            channel = Channel.STABLE,
            changelog = "fix",
            exePath = null,
            apkPath = null
        )
        advanceUntilIdle()

        assertEquals(1, publishingService.createStoreReleaseCalls)
        assertTrue(viewModel.message.value?.contains("pubblicato") == true)
        viewModel.dispose()
    }

    @Test
    fun saveAppStopsWhenEnsureRepositoryFails() = runTest {
        val publishingService = AdminTestPublishingService(
            ensureRepositoryResult = Result.success(
                EnsureRepositoryResult(
                    failed = true,
                    reason = "Repository non accessibile con questo token."
                )
            )
        )
        val viewModel = createViewModel(
            publishingService = publishingService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.saveApp(
            AppEntry(
                id = "codex",
                name = "Codex",
                description = "Messaging app",
                repository = GitHubRepositoryRef(
                    repoOwner = "owner",
                    repoName = "codex",
                    manifestPath = "manifest.json"
                )
            )
        )
        advanceUntilIdle()

        assertEquals(1, publishingService.ensureRepositoryCalls)
        assertEquals(0, publishingService.updateAppManifestCalls)
        assertTrue(viewModel.message.value?.contains("Repository non accessibile") == true)
        assertFalse(viewModel.isSaving.value)
        viewModel.dispose()
    }

    @Test
    fun publishStoreUpdateStableMirrorsStableToBetaMetadata() = runTest {
        val publishingService = AdminTestPublishingService()
        val viewModel = createViewModel(
            publishingService = publishingService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.publishStoreUpdate(
            version = "1.2.0",
            channel = Channel.STABLE,
            changelog = "stable",
            exePath = null,
            apkPath = null
        )
        advanceUntilIdle()

        val releases = publishingService.lastUpdatedStoreIndex?.storeReleases
        assertEquals("1.2.0", releases?.stable?.version)
        assertEquals("1.2.0", releases?.beta?.version)
        viewModel.dispose()
    }

    @Test
    fun publishStoreUpdateBetaUpdatesOnlyBetaMetadata() = runTest {
        val publishingService = AdminTestPublishingService()
        val viewModel = createViewModel(
            publishingService = publishingService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.publishStoreUpdate(
            version = "1.3.0-beta.1",
            channel = Channel.BETA,
            changelog = "beta",
            exePath = null,
            apkPath = null
        )
        advanceUntilIdle()

        val releases = publishingService.lastUpdatedStoreIndex?.storeReleases
        assertEquals(null, releases?.stable)
        assertEquals("1.3.0-beta.1", releases?.beta?.version)
        viewModel.dispose()
    }

    @Test
    fun promoteStoreBetaToStableCopiesStoreChannelMetadata() = runTest {
        val betaVersion = VersionInfo(
            version = "1.4.0-beta.2",
            releaseDate = "2026-03-10",
            changelog = "beta",
            releaseTag = "beta-pampa-store-v1.4.0-beta.2"
        )
        val publishingService = AdminTestPublishingService(
            initialStoreIndex = StoreIndexManifest(
                storeReleases = StoreReleaseChannels(beta = betaVersion)
            )
        )
        val viewModel = createViewModel(
            publishingService = publishingService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.init()
        advanceUntilIdle()
        viewModel.promoteStoreBetaToStable()
        advanceUntilIdle()

        val releases = publishingService.lastUpdatedStoreIndex?.storeReleases
        assertEquals(betaVersion.version, releases?.stable?.version)
        assertEquals(betaVersion.version, releases?.beta?.version)
        viewModel.dispose()
    }

    @Test
    fun addVersionStableUpdatesBothStableAndBeta() = runTest {
        val app = testApp(stable = VersionInfo("1.0.0", "2026-03-10", releaseTag = "stable-codex-v1.0.0"))
        val publishingService = AdminTestPublishingService()
        val viewModel = createViewModel(
            publishingService = publishingService,
            catalogService = AdminTestCatalogService(StoreManifest(apps = listOf(app))),
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.init()
        advanceUntilIdle()
        viewModel.addVersion(
            appId = "codex",
            channel = Channel.STABLE,
            versionInfo = VersionInfo(
                version = "1.1.0",
                releaseDate = "2026-03-11",
                changelog = "stable",
                releaseTag = ""
            ),
            apkPath = null,
            exePath = null
        )
        advanceUntilIdle()

        val updated = publishingService.lastUpdatedAppManifest?.app
        assertEquals("1.1.0", updated?.stable?.version)
        assertEquals("1.1.0", updated?.beta?.version)
        viewModel.dispose()
    }

    @Test
    fun addVersionBetaUpdatesOnlyBeta() = runTest {
        val app = testApp(stable = VersionInfo("1.0.0", "2026-03-10", releaseTag = "stable-codex-v1.0.0"))
        val publishingService = AdminTestPublishingService()
        val viewModel = createViewModel(
            publishingService = publishingService,
            catalogService = AdminTestCatalogService(StoreManifest(apps = listOf(app))),
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.init()
        advanceUntilIdle()
        viewModel.addVersion(
            appId = "codex",
            channel = Channel.BETA,
            versionInfo = VersionInfo(
                version = "1.2.0-beta.1",
                releaseDate = "2026-03-11",
                changelog = "beta",
                releaseTag = ""
            ),
            apkPath = null,
            exePath = null
        )
        advanceUntilIdle()

        val updated = publishingService.lastUpdatedAppManifest?.app
        assertEquals("1.0.0", updated?.stable?.version)
        assertEquals("1.2.0-beta.1", updated?.beta?.version)
        viewModel.dispose()
    }

    @Test
    fun promoteAppBetaToStableCopiesBetaVersion() = runTest {
        val app = testApp(
            stable = VersionInfo("1.0.0", "2026-03-10", releaseTag = "stable-codex-v1.0.0"),
            beta = VersionInfo("1.1.0-beta.1", "2026-03-11", releaseTag = "beta-codex-v1.1.0-beta.1")
        )
        val publishingService = AdminTestPublishingService()
        val viewModel = createViewModel(
            publishingService = publishingService,
            catalogService = AdminTestCatalogService(StoreManifest(apps = listOf(app))),
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.init()
        advanceUntilIdle()
        viewModel.promoteAppBetaToStable("codex")
        advanceUntilIdle()

        val updated = publishingService.lastUpdatedAppManifest?.app
        assertEquals("1.1.0-beta.1", updated?.stable?.version)
        assertEquals("1.1.0-beta.1", updated?.beta?.version)
        viewModel.dispose()
    }

    @Test
    fun automationDetectionCreatesDraftFromSingleArtifact() = runTest {
        val localStorage = createLocalStorage().also { storage ->
            storage.init()
            storage.saveReleaseAutomationProfiles(
                listOf(
                    ReleaseAutomationProfile(
                        id = "store-release",
                        name = "Store release",
                        target = ReleaseAutomationTarget.Store,
                        windowsDirectory = "C:/build/out"
                    )
                )
            )
        }
        val automationService = AdminTestAutomationService()
        val viewModel = AdminViewModel(
            preferencesRepository = DefaultPreferencesRepository(localStorage),
            publishingRepository = DefaultPublishingRepository(AdminTestPublishingService()),
            apkValidationService = AdminTestApkValidationService(),
            releaseAutomationService = automationService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.pendingReleaseDrafts.collect { }
        }

        viewModel.init()
        advanceUntilIdle()
        automationService.emit(
            ReleaseAutomationEvent.ArtifactDetected(
                profileId = "store-release",
                artifact = DetectedArtifact(
                    kind = ReleaseArtifactKind.WINDOWS,
                    path = "C:/build/out/Pampa Store-1.2.0.exe",
                    fileName = "Pampa Store-1.2.0.exe",
                    version = "1.2.0",
                    signature = "windows-1"
                )
            )
        )
        advanceUntilIdle()

        val draft = viewModel.pendingReleaseDrafts.value.single()
        assertEquals("1.2.0", draft.version)
        assertEquals(null, draft.apkArtifact)
        assertEquals("Pampa Store-1.2.0.exe", draft.windowsArtifact?.fileName)
        assertTrue(draft.changelog.contains("Store release"))

        collectJob.cancel()
        viewModel.dispose()
    }

    @Test
    fun automationDetectionMergesSecondArtifactIntoExistingDraft() = runTest {
        val localStorage = createLocalStorage().also { storage ->
            storage.init()
            storage.saveReleaseAutomationProfiles(
                listOf(
                    ReleaseAutomationProfile(
                        id = "store-release",
                        name = "Store release",
                        target = ReleaseAutomationTarget.Store,
                        apkDirectory = "C:/apk",
                        windowsDirectory = "C:/win"
                    )
                )
            )
        }
        val automationService = AdminTestAutomationService()
        val viewModel = AdminViewModel(
            preferencesRepository = DefaultPreferencesRepository(localStorage),
            publishingRepository = DefaultPublishingRepository(AdminTestPublishingService()),
            apkValidationService = AdminTestApkValidationService(),
            releaseAutomationService = automationService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.init()
        advanceUntilIdle()
        automationService.emit(
            ReleaseAutomationEvent.ArtifactDetected(
                profileId = "store-release",
                artifact = DetectedArtifact(
                    kind = ReleaseArtifactKind.APK,
                    path = "C:/apk/androidApp-release.apk",
                    fileName = "androidApp-release.apk",
                    version = "1.2.0",
                    signature = "apk-1"
                )
            )
        )
        automationService.emit(
            ReleaseAutomationEvent.ArtifactDetected(
                profileId = "store-release",
                artifact = DetectedArtifact(
                    kind = ReleaseArtifactKind.WINDOWS,
                    path = "C:/win/Pampa Store-1.2.0.exe",
                    fileName = "Pampa Store-1.2.0.exe",
                    version = "1.2.0",
                    signature = "windows-1"
                )
            )
        )
        advanceUntilIdle()

        val draft = viewModel.pendingReleaseDrafts.value.single()
        assertEquals("androidApp-release.apk", draft.apkArtifact?.fileName)
        assertEquals("Pampa Store-1.2.0.exe", draft.windowsArtifact?.fileName)
        viewModel.dispose()
    }

    @Test
    fun publishAutomationDraftSuccessRemovesDraftAndMarksArtifactsHandled() = runTest {
        val tempFile = Files.createTempFile("automation-release", ".exe").toFile().apply {
            writeBytes("installer".encodeToByteArray())
        }
        val localStorage = createLocalStorage().also { storage ->
            storage.init()
            storage.saveReleaseAutomationProfiles(
                listOf(
                    ReleaseAutomationProfile(
                        id = "store-release",
                        name = "Store release",
                        target = ReleaseAutomationTarget.Store,
                        windowsDirectory = tempFile.parentFile.absolutePath
                    )
                )
            )
        }
        val automationService = AdminTestAutomationService()
        val publishingService = AdminTestPublishingService()
        val viewModel = AdminViewModel(
            preferencesRepository = DefaultPreferencesRepository(localStorage),
            publishingRepository = DefaultPublishingRepository(publishingService),
            apkValidationService = AdminTestApkValidationService(),
            releaseAutomationService = automationService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.init()
        advanceUntilIdle()
        automationService.emit(
            ReleaseAutomationEvent.ArtifactDetected(
                profileId = "store-release",
                artifact = DetectedArtifact(
                    kind = ReleaseArtifactKind.WINDOWS,
                    path = tempFile.absolutePath,
                    fileName = tempFile.name,
                    version = "1.2.0",
                    signature = "windows-1"
                )
            )
        )
        advanceUntilIdle()

        val draftId = viewModel.pendingReleaseDrafts.value.single().id
        viewModel.publishAutomationDraft(draftId, Channel.STABLE)
        advanceUntilIdle()

        assertTrue(viewModel.pendingReleaseDrafts.value.isEmpty())
        assertTrue(localStorage.isReleaseArtifactHandled("windows-1"))
        assertEquals(1, publishingService.createStoreReleaseCalls)
        viewModel.dispose()
    }

    @Test
    fun publishAutomationDraftFailureKeepsDraftInQueue() = runTest {
        val tempFile = Files.createTempFile("automation-release", ".exe").toFile().apply {
            writeBytes("installer".encodeToByteArray())
        }
        val localStorage = createLocalStorage().also { storage ->
            storage.init()
            storage.saveReleaseAutomationProfiles(
                listOf(
                    ReleaseAutomationProfile(
                        id = "store-release",
                        name = "Store release",
                        target = ReleaseAutomationTarget.Store,
                        windowsDirectory = tempFile.parentFile.absolutePath
                    )
                )
            )
        }
        val automationService = AdminTestAutomationService()
        val publishingService = AdminTestPublishingService(
            uploadAssetResult = Result.failure(IllegalStateException("Upload ko"))
        )
        val viewModel = AdminViewModel(
            preferencesRepository = DefaultPreferencesRepository(localStorage),
            publishingRepository = DefaultPublishingRepository(publishingService),
            apkValidationService = AdminTestApkValidationService(),
            releaseAutomationService = automationService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.init()
        advanceUntilIdle()
        automationService.emit(
            ReleaseAutomationEvent.ArtifactDetected(
                profileId = "store-release",
                artifact = DetectedArtifact(
                    kind = ReleaseArtifactKind.WINDOWS,
                    path = tempFile.absolutePath,
                    fileName = tempFile.name,
                    version = "1.2.0",
                    signature = "windows-1"
                )
            )
        )
        advanceUntilIdle()

        val draftId = viewModel.pendingReleaseDrafts.value.single().id
        viewModel.publishAutomationDraft(draftId, Channel.STABLE)
        advanceUntilIdle()

        val draft = viewModel.pendingReleaseDrafts.value.single()
        assertEquals(draftId, draft.id)
        assertTrue(draft.errorMessage.contains("Upload ko"))
        viewModel.dispose()
    }

    @Test
    fun addVersionStableBlocksRegressionAgainstNewerBeta() = runTest {
        val app = testApp(
            stable = VersionInfo("1.0.0", "2026-03-10", releaseTag = "stable-codex-v1.0.0"),
            beta = VersionInfo("1.3.0-beta.1", "2026-03-11", releaseTag = "beta-codex-v1.3.0-beta.1")
        )
        val publishingService = AdminTestPublishingService()
        val viewModel = createViewModel(
            publishingService = publishingService,
            catalogService = AdminTestCatalogService(StoreManifest(apps = listOf(app))),
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.init()
        advanceUntilIdle()
        viewModel.addVersion(
            appId = "codex",
            channel = Channel.STABLE,
            versionInfo = VersionInfo(
                version = "1.2.0",
                releaseDate = "2026-03-11",
                changelog = "stable",
                releaseTag = ""
            ),
            apkPath = null,
            exePath = null
        )
        advanceUntilIdle()

        assertEquals(0, publishingService.createReleaseCalls)
        assertTrue(viewModel.message.value?.contains("non pubblicabile") == true)
        viewModel.dispose()
    }

    private fun createViewModel(
        publishingService: AdminTestPublishingService,
        catalogService: CatalogService = AdminTestCatalogService(),
        scope: CoroutineScope
    ): AdminViewModel {
        return AdminViewModel(
            preferencesRepository = DefaultPreferencesRepository(createLocalStorage()),
            appRepository = DefaultAppRepository(catalogService),
            publishingRepository = DefaultPublishingRepository(publishingService),
            apkValidationService = AdminTestApkValidationService(),
            scope = scope
        )
    }

    private fun createLocalStorage(): LocalStorage {
        val tempHome = Files.createTempDirectory("admin-viewmodel-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        return LocalStorage(PreferencesStorage())
    }
}

private class AdminTestCatalogService(
    private val manifest: StoreManifest = StoreManifest()
) : CatalogService {
    override suspend fun getManifest(): Result<StoreManifest> = Result.success(manifest)

    override suspend fun getLatestStoreUpdate(channel: Channel): Result<StoreUpdateInfo?> = Result.success(null)

    override fun downloadAsset(
        source: ReleaseAssetSource,
        savePath: String,
        onProgress: (Float) -> Unit
    ): Flow<DownloadEvent> = emptyFlow()

    override fun close() = Unit
}

private class AdminTestApkValidationService : ApkValidationService {
    override suspend fun validate(apkPath: String): ApkValidationResult {
        return ApkValidationResult.Unsupported("APK validation not needed in this test.")
    }
}

private class AdminTestAutomationService : ReleaseAutomationService {
    private val mutableEvents = MutableSharedFlow<ReleaseAutomationEvent>(extraBufferCapacity = 16)

    override val events: Flow<ReleaseAutomationEvent> = mutableEvents

    override fun start() = Unit

    override fun updateProfiles(profiles: List<ReleaseAutomationProfile>) = Unit

    override fun stop() = Unit

    suspend fun emit(event: ReleaseAutomationEvent) {
        mutableEvents.emit(event)
    }
}

private class AdminTestPublishingService(
    val ensureRepositoryResult: Result<EnsureRepositoryResult> = Result.success(
        EnsureRepositoryResult(alreadyExists = true)
    ),
    private val latestStoreRelease: GitHubRelease? = null,
    initialStoreIndex: StoreIndexManifest = StoreIndexManifest(),
    private val uploadAssetResult: Result<Unit> = Result.success(Unit)
) : AdminPublishingService {
    override val isConfigured: Boolean = true
    override val disabledReason: String? = null
    override val storeRepository: GitHubRepositoryRef? =
        GitHubRepositoryRef(repoOwner = "owner", repoName = "store", manifestPath = "manifest.json")

    var ensureRepositoryCalls = 0
    var updateAppManifestCalls = 0
    var createStoreReleaseCalls = 0
    var createReleaseCalls = 0
    var lastUpdatedStoreIndex: StoreIndexManifest? = null
    var lastUpdatedAppManifest: AppManifest? = null
    private var currentStoreIndex: StoreIndexManifest = initialStoreIndex

    override suspend fun getStoreIndex(): Result<StoreIndexManifest> = Result.success(currentStoreIndex)

    override suspend fun updateStoreIndex(index: StoreIndexManifest): Result<Unit> {
        currentStoreIndex = index
        lastUpdatedStoreIndex = index
        return Result.success(Unit)
    }

    override suspend fun ensureRepository(
        repository: GitHubRepositoryRef,
        appMetadata: RepositoryBootstrapMetadata
    ): Result<EnsureRepositoryResult> {
        ensureRepositoryCalls++
        return ensureRepositoryResult
    }

    override suspend fun getAppManifest(repository: GitHubRepositoryRef): Result<AppManifest> {
        return Result.failure(IllegalStateException("Not used in this test."))
    }

    override suspend fun updateAppManifest(repository: GitHubRepositoryRef, manifest: AppManifest): Result<Unit> {
        updateAppManifestCalls++
        lastUpdatedAppManifest = manifest
        return Result.success(Unit)
    }

    override suspend fun getLatestStoreRelease(): Result<GitHubRelease?> = Result.success(latestStoreRelease)

    override suspend fun createRelease(
        repository: GitHubRepositoryRef,
        tag: String,
        name: String,
        body: String,
        prerelease: Boolean
    ): Result<GitHubRelease> {
        createReleaseCalls++
        return Result.success(
            GitHubRelease(
                id = 200,
                tag_name = tag,
                name = name,
                body = body,
                prerelease = prerelease,
                upload_url = "https://upload.test/assets{?name,label}"
            )
        )
    }

    override suspend fun createStoreRelease(
        tag: String,
        name: String,
        body: String,
        prerelease: Boolean
    ): Result<GitHubRelease> {
        createStoreReleaseCalls++
        return Result.success(
            GitHubRelease(
                id = 100,
                tag_name = tag,
                name = name,
                body = body,
                prerelease = prerelease,
                upload_url = "https://upload.test/assets{?name,label}"
            )
        )
    }

    override suspend fun uploadAsset(
        uploadUrl: String,
        fileBytes: ByteArray,
        fileName: String,
        contentType: String
    ): Result<Unit> = uploadAssetResult

    override fun close() = Unit
}

private fun testApp(stable: VersionInfo? = null, beta: VersionInfo? = null): AppEntry {
    return AppEntry(
        id = "codex",
        name = "Codex",
        description = "Messaging app",
        repository = GitHubRepositoryRef(
            repoOwner = "owner",
            repoName = "codex",
            manifestPath = "manifest.json"
        ),
        stable = stable,
        beta = beta
    )
}
