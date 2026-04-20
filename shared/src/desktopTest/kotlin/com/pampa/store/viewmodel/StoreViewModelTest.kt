package com.pampa.store.viewmodel

import com.pampa.store.data.api.CatalogService
import com.pampa.store.data.api.DownloadEvent
import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.local.PreferencesStorage
import com.pampa.store.data.local.StorePreferences
import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.AppPlatform
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DownloadState
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.data.model.InstallEvent
import com.pampa.store.data.model.OwnershipFilter
import com.pampa.store.data.model.ReleaseAssetSource
import com.pampa.store.data.model.StoreManifest
import com.pampa.store.data.model.StoreUpdateInfo
import com.pampa.store.data.model.VersionInfo
import com.pampa.store.platform.InstallPrerequisites
import com.pampa.store.platform.InstallerService
import com.pampa.store.platform.PlatformAppService
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModelTest {
    private val originalUserHome = System.getProperty("user.home")

    @AfterTest
    fun restoreUserHome() {
        System.setProperty("user.home", originalUserHome)
    }

    @Test
    fun downloadAndInstallUsesPreferencesPathAndMarksInstalledOnSuccess() = runTest {
        val (localStorage, downloadDir) = createLocalStorage()
        val catalogService = FakeCatalogService(testManifest())
        val installer = FakeInstaller(
            listOf(
                InstallEvent.Preparing,
                InstallEvent.Installing("Launching installer"),
                InstallEvent.Success
            )
        )
        val platformAppService = FakePlatformAppService()
        val viewModel = createViewModel(
            localStorage,
            catalogService,
            installer,
            platformAppService,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredApps.collect { }
        }

        viewModel.init()
        advanceUntilIdle()
        viewModel.downloadAndInstall("test-app", Channel.STABLE)
        advanceUntilIdle()

        val request = assertNotNull(catalogService.downloadRequests.singleOrNull())
        assertEquals(expectedSavePath(downloadDir, "test-app-1.2.3-installer.exe"), request.savePath)

        val installRequest = assertNotNull(installer.installRequests.singleOrNull())
        assertEquals(request.savePath, installRequest.filePath)
        assertEquals("test-app", installRequest.appId)

        val installed = localStorage.getInstalledInfo("test-app")
        assertNotNull(installed)
        assertEquals("1.2.3", installed.version)
        assertEquals(Channel.STABLE, installed.channel)

        collectionJob.cancel()
        viewModel.dispose()
    }

    @Test
    fun downloadAndInstallPropagatesInstallerErrors() = runTest {
        val (localStorage, downloadDir) = createLocalStorage()
        val catalogService = FakeCatalogService(testManifest())
        val installer = FakeInstaller(
            listOf(
                InstallEvent.Preparing,
                InstallEvent.Failure("blocked", "Installer blocked")
            )
        )
        val viewModel = createViewModel(
            localStorage,
            catalogService,
            installer,
            FakePlatformAppService(),
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredApps.collect { }
        }

        viewModel.init()
        advanceUntilIdle()
        viewModel.downloadAndInstall("test-app", Channel.STABLE)
        advanceUntilIdle()

        val request = assertNotNull(catalogService.downloadRequests.singleOrNull())
        assertEquals(expectedSavePath(downloadDir, "test-app-1.2.3-installer.exe"), request.savePath)

        val app = assertNotNull(viewModel.filteredApps.value.find { it.entry.id == "test-app" })
        val errorState = assertIs<DownloadState.Error>(app.downloadState)
        assertEquals("Installer blocked", errorState.message)
        assertNull(localStorage.getInstalledInfo("test-app"))

        collectionJob.cancel()
        viewModel.dispose()
    }

    @Test
    fun ownershipFilterShowsOnlyInstalledApps() = runTest {
        val (localStorage, _) = createLocalStorage()
        val platformAppService = FakePlatformAppService(
            installedVersions = mutableMapOf("com.example.one" to "1.0.0")
        )
        val manifest = StoreManifest(
            apps = listOf(
                AppEntry(
                    id = "one",
                    name = "One",
                    description = "Installed",
                    packageName = "com.example.one",
                    stable = VersionInfo("1.0.0", "2026-03-08", releaseTag = "one-v1")
                ),
                AppEntry(
                    id = "two",
                    name = "Two",
                    description = "Not installed",
                    packageName = "com.example.two",
                    stable = VersionInfo("1.0.0", "2026-03-08", releaseTag = "two-v1")
                )
            )
        )
        val viewModel = createViewModel(
            localStorage,
            FakeCatalogService(manifest),
            FakeInstaller(listOf(InstallEvent.Success)),
            platformAppService,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            platform = AppPlatform.ANDROID
        )

        viewModel.init()
        advanceUntilIdle()
        viewModel.onOwnershipFilterSelected(OwnershipFilter.INSTALLED)
        advanceUntilIdle()

        assertEquals(listOf("one"), viewModel.filteredApps.value.map { it.entry.id })
        viewModel.dispose()
    }

    @Test
    fun refreshAppsReconcilesInstalledVersionWithPlatform() = runTest {
        val (localStorage, _) = createLocalStorage()
        localStorage.markInstalled("test-app", "1.0.0", Channel.STABLE, "")

        val platformAppService = FakePlatformAppService(
            installedVersions = mutableMapOf("com.example.test" to "1.2.3")
        )
        val viewModel = createViewModel(
            localStorage,
            FakeCatalogService(testManifest()),
            FakeInstaller(listOf(InstallEvent.Success)),
            platformAppService,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            platform = AppPlatform.ANDROID
        )

        viewModel.init()
        advanceUntilIdle()

        val installed = assertNotNull(localStorage.getInstalledInfo("test-app"))
        assertEquals("1.2.3", installed.version)
        assertEquals(false, viewModel.displayApps.value.first().hasUpdate)
        viewModel.dispose()
    }

    @Test
    fun refreshAppsDoesNotMarkAppUpdatableWhenInstalledVersionIsNewerThanCatalog() = runTest {
        val (localStorage, _) = createLocalStorage()
        localStorage.markInstalled("test-app", "1.0.0", Channel.STABLE, "")

        val platformAppService = FakePlatformAppService(
            installedVersions = mutableMapOf("com.example.test" to "1.3.0")
        )
        val viewModel = createViewModel(
            localStorage,
            FakeCatalogService(testManifest()),
            FakeInstaller(listOf(InstallEvent.Success)),
            platformAppService,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            platform = AppPlatform.ANDROID
        )

        viewModel.init()
        advanceUntilIdle()

        val installed = assertNotNull(localStorage.getInstalledInfo("test-app"))
        assertEquals("1.3.0", installed.version)
        assertEquals(false, viewModel.displayApps.value.first().hasUpdate)
        viewModel.dispose()
    }

    @Test
    fun refreshAppsRemovesMissingInstalledAppsWhenPackageIsGone() = runTest {
        val (localStorage, _) = createLocalStorage()
        localStorage.markInstalled("test-app", "1.0.0", Channel.STABLE, "")

        val viewModel = createViewModel(
            localStorage,
            FakeCatalogService(testManifest()),
            FakeInstaller(listOf(InstallEvent.Success)),
            FakePlatformAppService(installedVersions = mutableMapOf()),
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            platform = AppPlatform.ANDROID
        )

        viewModel.init()
        advanceUntilIdle()

        assertNull(localStorage.getInstalledInfo("test-app"))
        viewModel.dispose()
    }

    @Test
    fun downloadDoesNotStartWhenInstallPrerequisiteIsMissing() = runTest {
        val (localStorage, _) = createLocalStorage()
        val catalogService = FakeCatalogService(testManifest())
        val installer = FakeInstaller(listOf(InstallEvent.Success))
        val platformAppService = FakePlatformAppService(
            prerequisites = InstallPrerequisites(
                canInstallPackages = false,
                message = "Abilita prima le origini sconosciute."
            )
        )
        val viewModel = createViewModel(
            localStorage,
            catalogService,
            installer,
            platformAppService,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            platform = AppPlatform.ANDROID
        )

        viewModel.init()
        advanceUntilIdle()
        viewModel.downloadAndInstall("test-app", Channel.STABLE)
        advanceUntilIdle()

        assertTrue(catalogService.downloadRequests.isEmpty())
        val app = assertNotNull(viewModel.displayApps.value.find { it.entry.id == "test-app" })
        val errorState = assertIs<DownloadState.Error>(app.downloadState)
        assertTrue(errorState.message.contains("origini sconosciute"))
        viewModel.dispose()
    }

    @Test
    fun checkStoreUpdateUsesDedicatedStoreChannelPreference() = runTest {
        val (localStorage, _) = createLocalStorage()
        localStorage.setStoreUpdateChannel(Channel.BETA)

        val catalogService = FakeCatalogService(testManifest())
        val viewModel = createViewModel(
            localStorage,
            catalogService,
            FakeInstaller(listOf(InstallEvent.Success)),
            FakePlatformAppService(),
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )

        viewModel.init()
        advanceUntilIdle()
        viewModel.checkStoreUpdate()
        advanceUntilIdle()

        assertEquals(Channel.BETA, catalogService.requestedUpdateChannels.lastOrNull())
        viewModel.dispose()
    }

    private fun createViewModel(
        localStorage: LocalStorage,
        catalogService: CatalogService,
        installer: InstallerService,
        platformAppService: PlatformAppService,
        scope: CoroutineScope,
        platform: AppPlatform = AppPlatform.WINDOWS
    ): StoreViewModel {
        return StoreViewModel(
            preferencesRepository = com.pampa.store.data.repository.DefaultPreferencesRepository(localStorage),
            installerProvider = { installer },
            platformAppService = platformAppService,
            appRepositoryFactory = { com.pampa.store.data.repository.DefaultAppRepository(catalogService) },
            platformProvider = { platform },
            scope = scope
        )
    }

    private suspend fun createLocalStorage(): Pair<LocalStorage, String> {
        val tempHome = Files.createTempDirectory("pampastore-test-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        val localStorage = LocalStorage(PreferencesStorage())
        localStorage.init()

        val downloadDir = tempHome.resolve("custom-downloads").absolutePath
        localStorage.savePreferences(StorePreferences(downloadPath = downloadDir))

        return localStorage to downloadDir
    }

    private fun testManifest(): StoreManifest {
        return StoreManifest(
            apps = listOf(
                AppEntry(
                    id = "test-app",
                    name = "Test App",
                    description = "For testing",
                    packageName = "com.example.test",
                    stable = VersionInfo(
                        version = "1.2.3",
                        releaseDate = "2026-03-08",
                        releaseTag = "test-app-v1.2.3",
                        exeAsset = "test-app-1.2.3-installer.exe"
                    )
                )
            )
        )
    }

    private fun expectedSavePath(downloadDir: String, assetName: String): String {
        val separator = when {
            downloadDir.endsWith("/") || downloadDir.endsWith("\\") -> ""
            downloadDir.contains("\\") && !downloadDir.contains("/") -> "\\"
            else -> "/"
        }
        return downloadDir + separator + assetName
    }
}

private data class DownloadRequest(
    val releaseTag: String,
    val assetName: String,
    val savePath: String
)

private data class InstallRequest(
    val filePath: String,
    val appId: String
)

private class FakeCatalogService(
    private val manifest: StoreManifest,
    private val payload: ByteArray = "desktop-installer".encodeToByteArray(),
    private val digestOverride: String? = null,
    private val sizeOverride: Long? = null,
    private val errorMessage: String? = null,
    private val storeUpdateInfo: StoreUpdateInfo? = null
) : CatalogService {
    val downloadRequests = mutableListOf<DownloadRequest>()
    val requestedUpdateChannels = mutableListOf<Channel>()

    override suspend fun getManifest(): Result<StoreManifest> = Result.success(manifest)

    override suspend fun getLatestStoreUpdate(channel: Channel): Result<StoreUpdateInfo?> {
        requestedUpdateChannels += channel
        return Result.success(storeUpdateInfo)
    }

    override fun downloadAsset(
        source: ReleaseAssetSource,
        savePath: String,
        onProgress: (Float) -> Unit
    ): Flow<DownloadEvent> = flow {
        downloadRequests += DownloadRequest(source.releaseTag, source.assetName, savePath)

        val outputPath = Path.of(savePath)
        outputPath.parent?.createDirectories()
        outputPath.writeBytes(payload)

        emit(DownloadEvent.Started)
        onProgress(1f)
        emit(DownloadEvent.Progress(1f, payload.size.toLong(), payload.size.toLong()))
        if (errorMessage != null) {
            emit(DownloadEvent.Error(errorMessage))
            return@flow
        }
        emit(
            DownloadEvent.Completed(
                filePath = savePath,
                assetSize = sizeOverride ?: payload.size.toLong(),
                assetDigest = digestOverride ?: "sha256:${payload.sha256()}"
            )
        )
    }

    override fun close() = Unit
}

private class FakeInstaller(
    private val events: List<InstallEvent>
) : InstallerService {
    val installRequests = mutableListOf<InstallRequest>()

    override fun install(filePath: String, appId: String): Flow<InstallEvent> = flow {
        installRequests += InstallRequest(filePath, appId)
        events.forEach { emit(it) }
    }
}

private class FakePlatformAppService(
    private val prerequisites: InstallPrerequisites = InstallPrerequisites(canInstallPackages = true),
    val installedVersions: MutableMap<String, String> = mutableMapOf()
) : PlatformAppService {
    override suspend fun getInstallPrerequisites(): InstallPrerequisites = prerequisites

    override suspend fun resolveInstalledVersion(packageName: String): String? = installedVersions[packageName]

    override fun openInstallSettings() = Unit

    override fun launchInstalledApp(packageName: String): Boolean = packageName in installedVersions
}

private fun ByteArray.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString("") { "%02x".format(it) }
}
